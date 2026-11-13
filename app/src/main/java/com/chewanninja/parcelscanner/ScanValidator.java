package com.chewanninja.parcelscanner;

import java.util.Locale;
import java.util.regex.Pattern;

/** Same rule is enforced again on the server (Code.gs). Keep both in sync. */
public final class ScanValidator {
    private ScanValidator() {}
    private static final Pattern TRACKING = Pattern.compile("^[A-Z0-9][A-Z0-9\\-]{5,39}$");

    /** Removes control characters (CR/LF/TAB), trims, upper-cases. */
    public static String clean(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\p{Cntrl}", "").trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String cleaned) {
        return cleaned != null && TRACKING.matcher(cleaned).matches();
    }
}
