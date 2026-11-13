package com.chewanninja.parcelscanner;

/**
 * Optional build-time defaults. You can paste your Apps Script URL and secret here
 * (edit this file on GitHub) so the APK is ready to use. Both can still be changed
 * later in the app: Settings.
 */
public final class Config {
    private Config() {}

    /** Example: https://script.google.com/macros/s/AKfy.../exec */
    public static final String DEFAULT_SCRIPT_URL = "";
    /** Must be identical to SECRET_KEY in apps-script/Code.gs */
    public static final String DEFAULT_SECRET_KEY = "";

    public static final String TIMEZONE = "Asia/Kuala_Lumpur";
    public static final String MODE_RPU = "RPU";
    public static final String MODE_RTS = "RTS";
}
