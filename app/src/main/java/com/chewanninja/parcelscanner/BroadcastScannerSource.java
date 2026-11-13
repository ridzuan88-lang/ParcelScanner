package com.chewanninja.parcelscanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

/**
 * OPTIONAL fallback: some scanners (instead of typing into the screen) broadcast an Intent.
 * Disabled by default. Enable in Settings and enter the action + extra name used by the K7's
 * scanner utility. The Scanner Test page shows every extra of any broadcast received, which
 * helps you discover the correct names.
 */
public class BroadcastScannerSource implements ScannerSource {
    private final Context context;
    private final String action;
    private final String extraKey;
    private final ScanListener listener;
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            StringBuilder dump = new StringBuilder("BROADCAST " + intent.getAction());
            Bundle b = intent.getExtras();
            if (b != null) {
                for (String k : b.keySet()) dump.append("\n  ").append(k).append(" = ").append(String.valueOf(b.get(k)));
            }
            listener.onRawEvent(dump.toString());
            String v = extraKey.isEmpty() ? null : intent.getStringExtra(extraKey);
            if (v != null && !v.trim().isEmpty()) listener.onScan(v, "BROADCAST");
        }
    };

    public BroadcastScannerSource(Context context, String action, String extraKey, ScanListener listener) {
        this.context = context;
        this.action = action == null ? "" : action;
        this.extraKey = extraKey == null ? "" : extraKey;
        this.listener = listener;
    }

    @Override
    public void start() {
        if (registered || action.isEmpty()) return;
        context.registerReceiver(receiver, new IntentFilter(action));
        registered = true;
    }

    @Override
    public void stop() {
        if (!registered) return;
        try { context.unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) { }
        registered = false;
    }
}
