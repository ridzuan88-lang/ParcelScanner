package com.chewanninja.parcelscanner;

import android.app.Application;

/** Holds the single database + sync queue shared by all screens. */
public class App extends Application {
    private static ScanDatabase db;
    private static SyncManager sync;

    @Override
    public void onCreate() {
        super.onCreate();
        db = new ScanDatabase(this);
        sync = new SyncManager(db, new AppSettings(this));
        sync.kick(false); // upload anything left over from last session
    }

    public static ScanDatabase db() { return db; }
    public static SyncManager sync() { return sync; }
}
