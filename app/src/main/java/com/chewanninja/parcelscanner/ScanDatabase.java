package com.chewanninja.parcelscanner;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Persistent local queue + local duplicate memory (survives app restarts). */
public class ScanDatabase extends SQLiteOpenHelper {
    public static final String PENDING = "PENDING";
    public static final String SYNCED = "SYNCED";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String REJECTED = "REJECTED";

    private static final String[] COLS = {"id", "scan_id", "mode", "tracking", "status",
            "server_date", "server_datetime", "message", "attempts", "created_at"};

    public ScanDatabase(Context c) {
        super(c.getApplicationContext(), "scans.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE scans (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "scan_id TEXT NOT NULL UNIQUE, mode TEXT NOT NULL, tracking TEXT NOT NULL, "
                + "status TEXT NOT NULL, server_date TEXT, server_datetime TEXT, message TEXT, "
                + "attempts INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_mode_tracking ON scans(mode, tracking)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    private static ScanRecord read(Cursor c) {
        ScanRecord r = new ScanRecord();
        r.id = c.getLong(0);
        r.scanId = c.getString(1);
        r.mode = c.getString(2);
        r.tracking = c.getString(3);
        r.status = c.getString(4);
        r.serverDate = c.isNull(5) ? "" : c.getString(5);
        r.serverDateTime = c.isNull(6) ? "" : c.getString(6);
        r.message = c.isNull(7) ? "" : c.getString(7);
        r.attempts = c.getInt(8);
        r.createdAt = c.getLong(9);
        return r;
    }

    /** Existing record for this tracking number in this mode (RPU and RTS are independent). */
    public synchronized ScanRecord findExisting(String mode, String tracking) {
        Cursor c = getReadableDatabase().query("scans", COLS,
                "mode=? AND tracking=? AND status IN ('PENDING','SYNCED','DUPLICATE')",
                new String[]{mode, tracking}, null, null, "id ASC", "1");
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    public synchronized ScanRecord insertPending(String mode, String tracking, String scanId, long createdAt) {
        ContentValues v = new ContentValues();
        v.put("scan_id", scanId);
        v.put("mode", mode);
        v.put("tracking", tracking);
        v.put("status", PENDING);
        v.put("attempts", 0);
        v.put("created_at", createdAt);
        long id = getWritableDatabase().insertOrThrow("scans", null, v);
        return getById(id);
    }

    public synchronized ScanRecord getById(long id) {
        Cursor c = getReadableDatabase().query("scans", COLS, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    private void update(long id, String status, String date, String dateTime, String message, boolean attempt) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        if (date != null) v.put("server_date", date);
        if (dateTime != null) v.put("server_datetime", dateTime);
        if (message != null) v.put("message", message);
        SQLiteDatabase db = getWritableDatabase();
        db.update("scans", v, "id=?", new String[]{String.valueOf(id)});
        if (attempt) {
            db.execSQL("UPDATE scans SET attempts = attempts + 1 WHERE id=" + id);
        }
    }

    public synchronized void markSynced(long id, String date, String dateTime, String msg) {
        update(id, SYNCED, date, dateTime, msg, false);
    }

    public synchronized void markDuplicate(long id, String date, String dateTime, String msg) {
        update(id, DUPLICATE, date, dateTime, msg, false);
    }

    public synchronized void markRejected(long id, String msg) {
        update(id, REJECTED, null, null, msg, false);
    }

    public synchronized void markAttemptFailed(long id, String msg) {
        update(id, PENDING, null, null, msg, true);
    }

    public synchronized List<ScanRecord> getPending(int limit) {
        List<ScanRecord> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("scans", COLS, "status='PENDING'", null, null, null,
                "id ASC", String.valueOf(limit));
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized List<ScanRecord> recent(int n) {
        List<ScanRecord> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("scans", COLS, null, null, null, null,
                "id DESC", String.valueOf(n));
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized int countPending() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM scans WHERE status='PENDING'", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public synchronized int countSyncedSince(String mode, long sinceMillis) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM scans WHERE status='SYNCED' AND mode=? AND created_at>=?",
                new String[]{mode, String.valueOf(sinceMillis)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }
}
