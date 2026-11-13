package com.chewanninja.parcelscanner;

import android.content.Context;
import android.content.SharedPreferences;

public class AppSettings {
    private final SharedPreferences p;

    public AppSettings(Context c) {
        p = c.getApplicationContext().getSharedPreferences("parcel_scanner", Context.MODE_PRIVATE);
    }

    private String str(String key, String def) {
        String v = p.getString(key, null);
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }

    public String getScriptUrl() { return str("url", Config.DEFAULT_SCRIPT_URL); }
    public String getSecretKey() { return str("key", Config.DEFAULT_SECRET_KEY); }
    /** Stale-input timeout in ms (0 = off). Clears half-received scans that never got ENTER. */
    public int getScanTimeoutMs() { return p.getInt("timeout_ms", 2000); }
    public boolean isBeep() { return p.getBoolean("beep", true); }
    public boolean isVibrate() { return p.getBoolean("vibrate", true); }
    public boolean isKeepAwake() { return p.getBoolean("keep_awake", true); }
    public boolean isBroadcastEnabled() { return p.getBoolean("bc_enabled", false); }
    public String getBroadcastAction() { return str("bc_action", ""); }
    public String getBroadcastExtra() { return str("bc_extra", ""); }
    public String getMode() { return p.getString("mode", Config.MODE_RPU); }

    public void setMode(String mode) { p.edit().putString("mode", mode).apply(); }

    public void save(String url, String key, int timeoutMs, boolean beep, boolean vibrate,
                     boolean keepAwake, boolean bcEnabled, String bcAction, String bcExtra) {
        p.edit().putString("url", url.trim()).putString("key", key.trim())
                .putInt("timeout_ms", timeoutMs).putBoolean("beep", beep)
                .putBoolean("vibrate", vibrate).putBoolean("keep_awake", keepAwake)
                .putBoolean("bc_enabled", bcEnabled).putString("bc_action", bcAction.trim())
                .putString("bc_extra", bcExtra.trim()).apply();
    }
}
