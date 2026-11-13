package com.chewanninja.parcelscanner;

import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity implements ScanListener, SyncManager.Callback {

    private static final int GREEN = 0xFF2E7D32;
    private static final int RED = 0xFFC62828;
    private static final int AMBER = 0xFFEF6C00;
    private static final int GREY = 0xFF757575;
    private static final int BLUE = 0xFF1565C0;
    private static final int PURPLE = 0xFF6A1B9A;

    private AppSettings settings;
    private EditText scanField;
    private TextView tvMode, tvResult, tvTracking, tvTime, tvMessage, tvNotice, tvFocus, tvStats, tvRecent;
    private Button btnRpu, btnRts;

    private ScannerInputManager scanner;
    private ScannerSource broadcast;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService dbExec = Executors.newSingleThreadExecutor();
    private final AtomicInteger inProgress = new AtomicInteger();
    private volatile long currentId = -1;
    private boolean resumed;
    private ToneGenerator tone;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = new AppSettings(this);

        scanField = findViewById(R.id.scanField);
        tvMode = findViewById(R.id.tvMode);
        tvResult = findViewById(R.id.tvResult);
        tvTracking = findViewById(R.id.tvTracking);
        tvTime = findViewById(R.id.tvTime);
        tvMessage = findViewById(R.id.tvMessage);
        tvNotice = findViewById(R.id.tvNotice);
        tvFocus = findViewById(R.id.tvFocus);
        tvStats = findViewById(R.id.tvStats);
        tvRecent = findViewById(R.id.tvRecent);
        btnRpu = findViewById(R.id.btnRpu);
        btnRts = findViewById(R.id.btnRts);

        try { tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 90); } catch (RuntimeException ignored) { }
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        scanner = new ScannerInputManager(scanField, this);
        scanner.start();

        btnRpu.setOnClickListener(v -> switchMode(Config.MODE_RPU));
        btnRts.setOnClickListener(v -> switchMode(Config.MODE_RTS));
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // Whenever the scanner field loses focus (touching a button, dialogs...) take it back.
        scanField.setOnFocusChangeListener((v, has) -> {
            updateFocusIndicator(has);
            if (!has && resumed) ui.postDelayed(this::restoreFocus, 50);
        });

        applyMode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        scanner.setTimeoutMs(settings.getScanTimeoutMs());
        if (settings.isKeepAwake()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        stopBroadcast();
        if (settings.isBroadcastEnabled() && !settings.getBroadcastAction().isEmpty()) {
            broadcast = new BroadcastScannerSource(this, settings.getBroadcastAction(),
                    settings.getBroadcastExtra(), this);
            broadcast.start();
        }

        App.sync().setCallback(this);
        App.sync().kick(false);
        applyMode();
        refreshStats();
        restoreFocus();
    }

    @Override
    protected void onPause() {
        resumed = false;
        App.sync().setCallback(null);
        stopBroadcast();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        scanner.stop();
        if (tone != null) tone.release();
        dbExec.shutdown();
        super.onDestroy();
    }

    private void stopBroadcast() {
        if (broadcast != null) { broadcast.stop(); broadcast = null; }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) restoreFocus();
    }

    /** If a scan starts while the field somehow lost focus, grab focus first so no characters are lost. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!scanField.hasFocus()) {
            int k = event.getKeyCode();
            if (k != KeyEvent.KEYCODE_BACK && k != KeyEvent.KEYCODE_HOME && k != KeyEvent.KEYCODE_MENU
                    && k != KeyEvent.KEYCODE_VOLUME_UP && k != KeyEvent.KEYCODE_VOLUME_DOWN
                    && k != KeyEvent.KEYCODE_POWER) {
                scanField.requestFocus();
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void restoreFocus() {
        scanner.restoreFocus();
        updateFocusIndicator(scanField.hasFocus());
    }

    private void updateFocusIndicator(boolean has) {
        tvFocus.setText(has ? "SCANNER READY" : "SCANNER NOT FOCUSED - tap the input box");
        tvFocus.setTextColor(has ? GREEN : RED);
    }

    // ---------------------------------------------------------------- mode

    private boolean isBusy() {
        return inProgress.get() > 0 || App.sync().isBusy();
    }

    private void switchMode(String mode) {
        if (mode.equals(settings.getMode())) { restoreFocus(); return; }
        if (isBusy()) {
            Toast.makeText(this, "Wait - a scan is still being submitted", Toast.LENGTH_SHORT).show();
            restoreFocus();
            return;
        }
        settings.setMode(mode);
        applyMode();
        refreshStats();
        restoreFocus();
    }

    private void applyMode() {
        boolean rpu = Config.MODE_RPU.equals(settings.getMode());
        tvMode.setText(rpu ? "RPU MODE" : "RTS MODE");
        tvMode.setBackgroundColor(rpu ? BLUE : PURPLE);
        btnRpu.setAlpha(rpu ? 1f : 0.45f);
        btnRts.setAlpha(rpu ? 0.45f : 1f);
    }

    // ---------------------------------------------------------------- scanning

    @Override
    public void onScan(String raw, String source) {
        final String tracking = ScanValidator.clean(raw);
        if (!ScanValidator.isValid(tracking)) {
            String shown = tracking.isEmpty() ? "(empty)" : (tracking.length() > 40 ? tracking.substring(0, 40) + "..." : tracking);
            showState("ERROR", RED, shown, Fmt.dateTimeKL(System.currentTimeMillis()) + " (device)",
                    "Invalid barcode - not a tracking number. Nothing saved.");
            feedback(false);
            return;
        }
        final String mode = settings.getMode();       // mode is fixed at the moment of the scan
        final long capturedAt = System.currentTimeMillis();
        inProgress.incrementAndGet();
        tvNotice.setText("");
        showState("SENDING...", GREY, tracking, Fmt.dateTimeKL(capturedAt) + " (device)", mode + " - saving...");
        dbExec.execute(() -> processScan(mode, tracking, capturedAt));
    }

    /** Runs on the DB thread - the UI thread is free for the next scan immediately. */
    private void processScan(String mode, String tracking, long capturedAt) {
        try {
            final ScanRecord existing = App.db().findExisting(mode, tracking);
            if (existing != null) {
                ui.post(() -> {
                    currentId = -1;
                    String when = existing.serverDateTime.isEmpty()
                            ? Fmt.dateTimeKL(existing.createdAt) + " (device)" : existing.serverDateTime;
                    showState("DUPLICATE", AMBER, existing.tracking, when,
                            "Already scanned in " + mode
                                    + (ScanDatabase.PENDING.equals(existing.status) ? " (waiting to upload)" : ""));
                    feedback(false);
                });
                return;
            }
            ScanRecord rec = App.db().insertPending(mode, tracking, UUID.randomUUID().toString(), capturedAt);
            currentId = rec.id;
            App.sync().kick(true);                 // background upload
            ui.post(this::refreshStats);
        } catch (final Exception e) {
            ui.post(() -> {
                showState("ERROR", RED, tracking, Fmt.dateTimeKL(System.currentTimeMillis()) + " (device)",
                        "Could not save on device: " + e.getMessage());
                feedback(false);
            });
        } finally {
            inProgress.decrementAndGet();
        }
    }

    /** Background thread -> UI. */
    @Override
    public void onRecordUpdated(final ScanRecord rec, final SheetsClient.Result res) {
        ui.post(() -> {
            if (rec.id == currentId) render(rec);
            refreshStats();
            boolean fresh = System.currentTimeMillis() - rec.createdAt < 30000;
            if (fresh) feedback(ScanDatabase.SYNCED.equals(rec.status));
        });
    }

    private void render(ScanRecord rec) {
        switch (rec.status) {
            case ScanDatabase.SYNCED:
                showState("SUCCESS", GREEN, rec.tracking, rec.serverDateTime, "Saved to " + rec.mode + " sheet");
                break;
            case ScanDatabase.DUPLICATE:
                showState("DUPLICATE", AMBER, rec.tracking, rec.serverDateTime, "Already in " + rec.mode + " sheet - not added again");
                break;
            case ScanDatabase.REJECTED:
                showState("ERROR", RED, rec.tracking, Fmt.dateTimeKL(rec.createdAt) + " (device)", "Rejected by server: " + rec.message);
                break;
            default:
                showState("ERROR", RED, rec.tracking, Fmt.dateTimeKL(rec.createdAt) + " (device)",
                        "NOT UPLOADED YET - saved on device, will retry automatically. " + rec.message);
        }
    }

    private void showState(String label, int color, String tracking, String time, String msg) {
        tvResult.setText(label);
        tvResult.setBackgroundColor(color);
        tvTracking.setText(tracking);
        tvTime.setText(time);
        tvMessage.setText(msg);
    }

    private void feedback(boolean success) {
        if (settings.isBeep() && tone != null) {
            tone.startTone(success ? ToneGenerator.TONE_PROP_BEEP : ToneGenerator.TONE_PROP_NACK, success ? 120 : 350);
        }
        if (settings.isVibrate() && vibrator != null) {
            //noinspection deprecation
            vibrator.vibrate(success ? 60 : 350);
        }
    }

    private void refreshStats() {
        dbExec.execute(() -> {
            final int pending = App.db().countPending();
            final long since = Fmt.startOfTodayKL();
            final int rpu = App.db().countSyncedSince(Config.MODE_RPU, since);
            final int rts = App.db().countSyncedSince(Config.MODE_RTS, since);
            final List<ScanRecord> recent = App.db().recent(8);
            final StringBuilder sb = new StringBuilder();
            for (ScanRecord r : recent) {
                String t = r.serverDateTime.isEmpty() ? Fmt.timeKL(r.createdAt) + "*" : r.serverDateTime.substring(Math.max(0, r.serverDateTime.length() - 8));
                String st;
                switch (r.status) {
                    case ScanDatabase.SYNCED: st = "OK "; break;
                    case ScanDatabase.DUPLICATE: st = "DUP"; break;
                    case ScanDatabase.REJECTED: st = "ERR"; break;
                    default: st = "..."; break;
                }
                sb.append(st).append(' ').append(r.mode).append(' ').append(t).append(' ').append(r.tracking).append('\n');
            }
            ui.post(() -> {
                tvStats.setText("Pending upload: " + pending + "   RPU today: " + rpu + "   RTS today: " + rts);
                tvRecent.setText(sb.length() == 0 ? "" : sb.toString() + "(* = device time, not yet uploaded)");
            });
        });
    }

    // ---------------------------------------------------------------- remaining ScanListener callbacks

    @Override public void onEmptyScan(String enterSource) { tvNotice.setText("Empty scan ignored"); }
    @Override public void onBufferChanged(String buffer) { }
    @Override public void onBufferCleared(String stale) { tvNotice.setText("Partial input discarded (no ENTER received): " + stale); }
    @Override public void onRawEvent(String description) { }
}
