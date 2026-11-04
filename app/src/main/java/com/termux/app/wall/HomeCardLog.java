package com.termux.app.wall;

import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;

import androidx.annotation.NonNull;

/** TEMP (never merge): logs the Home frame and the Home cards for ~1.5 s after a home press. */
final class HomeCardLog {

    static final String TAG = "TLHomeCard";
    private static final long WINDOW_MS = 1500L;

    private static long sUntil;
    private static boolean sRunning;
    private static PaneWallLayout sWall;
    private static int sFrame;

    private HomeCardLog() { }

    static void log(@NonNull String what) {
        Log.i(TAG, SystemClock.uptimeMillis() + " " + what);
    }

    /** The six frames that called the caller of this. */
    @NonNull
    static String callers() {
        StackTraceElement[] trace = new Throwable().getStackTrace();
        StringBuilder out = new StringBuilder(" callers:");
        for (int i = 2; i < Math.min(trace.length, 8); i++) {
            String name = trace[i].getClassName();
            out.append(" <- ").append(name.substring(name.lastIndexOf('.') + 1))
                .append('.').append(trace[i].getMethodName()).append(':')
                .append(trace[i].getLineNumber());
        }
        return out.toString();
    }

    /** Starts (or extends) the per-frame dump of {@code wall}. */
    static void watch(@NonNull PaneWallLayout wall) {
        sWall = wall;
        sUntil = SystemClock.uptimeMillis() + WINDOW_MS;
        sFrame = 0;
        if (sRunning) return;
        sRunning = true;
        Choreographer.getInstance().postFrameCallback(FRAME);
    }

    private static final Choreographer.FrameCallback FRAME = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            PaneWallLayout wall = sWall;
            if (wall == null || SystemClock.uptimeMillis() > sUntil) {
                sRunning = false;
                log("watch end");
                return;
            }
            log("frame " + (sFrame++) + " " + wall.debugHomeCardState());
            Choreographer.getInstance().postFrameCallback(this);
        }
    };
}
