package com.termux.app.chrome;

import android.os.SystemClock;
import android.util.Log;

/** TEMPORARY (RISEINK-DBG): the rising keyboard's and toolbar's inks across a crossing. Revert. */
public final class RiseInkDbg {
    public static final String TAG = "RISEINK-DBG";

    private RiseInkDbg() {}

    public static void log(String msg) {
        Log.d(TAG, SystemClock.uptimeMillis() + " " + msg);
    }

    /** {@link #log} with six caller frames. */
    public static void logWithCallers(String msg) {
        StackTraceElement[] st = new Throwable().getStackTrace();
        StringBuilder sb = new StringBuilder(msg).append(" | at");
        for (int i = 1; i < Math.min(st.length, 7); i++) {
            sb.append(' ').append(st[i].getClassName().replaceAll(".*\\.", ""))
                .append('.').append(st[i].getMethodName()).append(':').append(st[i].getLineNumber());
        }
        log(sb.toString());
    }

    public static String hex(int c) {
        return String.format("#%08X", c);
    }

    public static String hex(int[] cs) {
        if (cs == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cs.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(hex(cs[i]));
        }
        return sb.append(']').toString();
    }
}
