package com.termux.app.launcher.drawer;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * TEMP diagnostic, tag {@code DrawerOpen}: never merge. Times each step of an open's first frames
 * (the 500 ms after {@link #arm()}), with uptime and four caller frames.
 */
public final class DrawerOpenTrace {

    private static final String TAG = "DrawerOpen";
    private static final long WINDOW_MS = 500L;
    private static long sArmedAtMs = -1L;

    private DrawerOpenTrace() {}

    /** Opens the window: called as an open begins. */
    public static void arm() {
        sArmedAtMs = SystemClock.uptimeMillis();
        Log.d(TAG, sArmedAtMs + " arm");
    }

    /** A start mark; pass it to {@link #end}. */
    public static long start() {
        return System.nanoTime();
    }

    /** Logs {@code step}'s duration if the window is open. */
    public static void end(@NonNull String step, long startNanos) {
        long now = SystemClock.uptimeMillis();
        if (sArmedAtMs < 0L || now - sArmedAtMs > WINDOW_MS) return;
        float ms = (System.nanoTime() - startNanos) / 1_000_000f;
        StringBuilder frames = new StringBuilder();
        StackTraceElement[] trace = new Throwable().getStackTrace();
        for (int i = 1; i < Math.min(trace.length, 5); i++) {
            frames.append(" <").append(trace[i].getMethodName()).append(':')
                .append(trace[i].getLineNumber());
        }
        Log.d(TAG, now + " +" + (now - sArmedAtMs) + "ms " + step + " "
            + Math.round(ms * 100f) / 100f + "ms" + frames);
    }
}
