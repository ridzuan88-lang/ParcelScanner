package com.chewanninja.parcelscanner;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Background upload queue. Never touches the UI thread. Retries every 15 s while records are pending. */
public class SyncManager {

    public interface Callback {
        /** Called on a background thread after each upload attempt. */
        void onRecordUpdated(ScanRecord rec, SheetsClient.Result res);
    }

    private final ScanDatabase db;
    private final AppSettings settings;
    private final SheetsClient client = new SheetsClient();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger userKicks = new AtomicInteger();
    private final AtomicBoolean wantDrain = new AtomicBoolean();
    private volatile Callback callback;

    public SyncManager(ScanDatabase db, AppSettings settings) {
        this.db = db;
        this.settings = settings;
        timer.scheduleWithFixedDelay(new Runnable() {
            @Override public void run() {
                if (db.countPending() > 0) kick(false);
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    public void setCallback(Callback cb) { this.callback = cb; }

    /** True while an upload triggered by a fresh scan is still in progress. */
    public boolean isBusy() { return userKicks.get() > 0; }

    public void kick(final boolean fromScan) {
        if (fromScan) userKicks.incrementAndGet();
        wantDrain.set(true);
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (wantDrain.getAndSet(false)) drain();
                } catch (Exception ignored) {
                } finally {
                    if (fromScan) userKicks.decrementAndGet();
                }
            }
        });
    }

    private void drain() {
        while (true) {
            List<ScanRecord> list = db.getPending(25);
            if (list.isEmpty()) return;
            for (ScanRecord r : list) {
                SheetsClient.Result res = client.send(settings.getScriptUrl(), settings.getSecretKey(), r);
                boolean keepGoing = apply(r, res);
                ScanRecord fresh = db.getById(r.id);
                Callback cb = callback;
                if (cb != null && fresh != null) cb.onRecordUpdated(fresh, res);
                if (!keepGoing) return; // network/config problem: stop, retry later (order preserved)
            }
        }
    }

    private boolean apply(ScanRecord r, SheetsClient.Result res) {
        switch (res.status) {
            case "OK":
                db.markSynced(r.id, res.date, res.dateTime, res.message);
                return true;
            case "DUPLICATE":
                db.markDuplicate(r.id, res.date, res.dateTime, res.message);
                return true;
            case "INVALID":
                db.markRejected(r.id, res.message);
                return true;
            default:
                db.markAttemptFailed(r.id, res.message);
                return false;
        }
    }
}
