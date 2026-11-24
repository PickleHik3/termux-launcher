package com.termux.app.statusbar;

import android.os.SystemClock;
import android.util.Log;

/** TEMPORARY (TL-MARKTAP): traces a tap on the thin row's marks. Reverted once captured. */
public final class MarkTapDiag {

    public static final String TAG = "TL-MARKTAP";

    private MarkTapDiag() {}

    public static void log(String message) {
        Log.i(TAG, SystemClock.uptimeMillis() + " " + message);
    }

    /** {@code message} with the six frames that called the caller. */
    public static void logWithCallers(String message) {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder out = new StringBuilder(message).append(" callers:");
        for (int i = 2; i < stack.length && i < 8; i++) {
            StackTraceElement e = stack[i];
            out.append(" <- ").append(e.getClassName().substring(
                e.getClassName().lastIndexOf('.') + 1)).append('.').append(e.getMethodName())
                .append(':').append(e.getLineNumber());
        }
        log(out.toString());
    }

    public static String action(int action) {
        switch (action) {
            case 0: return "DOWN";
            case 1: return "UP";
            case 2: return "MOVE";
            case 3: return "CANCEL";
            default: return "A" + action;
        }
    }
}
