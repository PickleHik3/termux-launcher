package com.termux.app.terminal.rename;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

/** DIAG(rename-chips): logcat tag RenameDiag for a rename that does not reach the chips; revert. */
public final class RenameDiag {

    public static final String TAG = "RenameDiag";

    /** Refreshes are logged from a rename's start until five seconds after it ends. */
    private static final long TRAIL_MS = 5000L;
    private static long sArmedUntil;

    private RenameDiag() {}

    public static void armOpen() { sArmedUntil = Long.MAX_VALUE; }

    public static void armTrail() { sArmedUntil = SystemClock.uptimeMillis() + TRAIL_MS; }

    public static boolean armed() { return SystemClock.uptimeMillis() < sArmedUntil; }

    public static void log(@NonNull String message) {
        Log.i(TAG, SystemClock.uptimeMillis() + " " + message);
    }

    /** {@link #log} with the six frames that called the caller. */
    public static void logWithCallers(@NonNull String message) {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder frames = new StringBuilder();
        for (int i = 2; i < Math.min(stack.length, 8); i++) {
            frames.append("\n    at ").append(stack[i].getClassName()).append('.')
                .append(stack[i].getMethodName()).append(':').append(stack[i].getLineNumber());
        }
        log(message + frames);
    }
}
