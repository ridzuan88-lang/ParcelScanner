package com.chewanninja.parcelscanner;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class Fmt {
    private Fmt() {}
    private static final TimeZone KL = TimeZone.getTimeZone(Config.TIMEZONE);

    /** dd/MM/yyyy HH:mm:ss in Malaysia time (device clock - only used for local/pending display). */
    public static String dateTimeKL(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);
        f.setTimeZone(KL);
        return f.format(new Date(millis));
    }

    public static String timeKL(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss", Locale.US);
        f.setTimeZone(KL);
        return f.format(new Date(millis));
    }

    public static long startOfTodayKL() {
        Calendar c = Calendar.getInstance(KL);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
