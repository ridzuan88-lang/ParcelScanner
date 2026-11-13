package com.chewanninja.parcelscanner;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Diagnostic page: shows exactly what the Kaicom K7 scanner delivers. Saves/sends nothing. */
public class ScannerTestActivity extends AppCompatActivity implements ScanListener {

    private EditText field;
    private TextView tvFocusState, tvEnter, tvReceived, tvCount, tvStamp, tvLive, tvRaw;
    private ScannerInputManager scanner;
    private ScannerSource broadcast;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final StringBuilder rawLog = new StringBuilder();
    private int rawLines;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_scanner_test);
        field = findViewById(R.id.testField);
        tvFocusState = findViewById(R.id.tvFocusState);
        tvEnter = findViewById(R.id.tvEnter);
        tvReceived = findViewById(R.id.tvReceived);
        tvCount = findViewById(R.id.tvCount);
        tvStamp = findViewById(R.id.tvStamp);
        tvLive = findViewById(R.id.tvLive);
        tvRaw = findViewById(R.id.tvRaw);

        AppSettings s = new AppSettings(this);
        scanner = new ScannerInputManager(field, this);
        scanner.setTimeoutMs(Math.max(1500, s.getScanTimeoutMs()));
        scanner.start();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        field.setOnFocusChangeListener((v, has) -> {
            updateFocus();
            if (!has && resumed) ui.postDelayed(() -> scanner.restoreFocus(), 50);
        });
        findViewById(R.id.btnTestClear).setOnClickListener(v -> {
            rawLog.setLength(0);
            rawLines = 0;
            tvRaw.setText("");
            tvEnter.setText("ENTER: waiting for a scan...");
            tvEnter.setTextColor(0xFF000000);
            tvReceived.setText("Received text: -");
            tvCount.setText("Character count: -");
            tvStamp.setText("Last scan: -");
            scanner.clearBuffer();
            scanner.restoreFocus();
        });
        findViewById(R.id.btnTestBack).setOnClickListener(v -> finish());

        if (s.isBroadcastEnabled() && !s.getBroadcastAction().isEmpty()) {
            broadcast = new BroadcastScannerSource(this, s.getBroadcastAction(), s.getBroadcastExtra(), this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (broadcast != null) broadcast.start();
        scanner.restoreFocus();
        updateFocus();
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (broadcast != null) broadcast.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        scanner.stop();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) { scanner.restoreFocus(); updateFocus(); }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int k = event.getKeyCode();
        if (!field.hasFocus() && k != KeyEvent.KEYCODE_BACK && k != KeyEvent.KEYCODE_HOME) field.requestFocus();
        return super.dispatchKeyEvent(event);
    }

    private void updateFocus() {
        boolean has = field.hasFocus();
        tvFocusState.setText("Scanner input focus: " + (has ? "YES" : "NO"));
        tvFocusState.setTextColor(has ? 0xFF2E7D32 : 0xFFC62828);
    }

    @Override
    public void onScan(String raw, String source) {
        String clean = raw.replaceAll("\\p{Cntrl}", "");
        tvEnter.setText("ENTER: DETECTED  (via " + source + ")");
        tvEnter.setTextColor(0xFF2E7D32);
        tvReceived.setText("Received text: " + clean);
        tvCount.setText("Character count: " + clean.length()
                + (ScanValidator.isValid(ScanValidator.clean(raw)) ? "   (valid tracking format)" : "   (NOT a valid tracking format)"));
        tvStamp.setText("Last scan: " + Fmt.dateTimeKL(System.currentTimeMillis()));
        tvLive.setText("Live buffer: -");
    }

    @Override
    public void onEmptyScan(String source) {
        tvEnter.setText("ENTER received but no text (via " + source + ")");
        tvEnter.setTextColor(0xFFEF6C00);
    }

    @Override
    public void onBufferChanged(String buffer) {
        tvLive.setText("Live buffer: " + buffer + "  [" + buffer.length() + "]");
    }

    @Override
    public void onBufferCleared(String stale) {
        tvEnter.setText("ENTER: NOT RECEIVED - text arrived without ENTER");
        tvEnter.setTextColor(0xFFC62828);
        tvReceived.setText("Received text: " + stale);
        tvCount.setText("Character count: " + stale.length());
        tvStamp.setText("Last scan: " + Fmt.dateTimeKL(System.currentTimeMillis()));
        tvLive.setText("Live buffer: -");
    }

    @Override
    public void onRawEvent(String description) {
        rawLines++;
        rawLog.insert(0, Fmt.timeKL(System.currentTimeMillis()) + " " + description + "\n");
        if (rawLines > 60) {
            int cut = rawLog.lastIndexOf("\n", rawLog.length() - 2);
            if (cut > 0) rawLog.setLength(cut + 1);
            rawLines = 60;
        }
        tvRaw.setText(rawLog.toString());
    }
}
