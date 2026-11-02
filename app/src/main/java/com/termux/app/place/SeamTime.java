package com.termux.app.place;

import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.view.Choreographer;

import androidx.annotation.NonNull;

/**
 * TEMP diagnostic (never merge): timings of a cross-kind seam drag under logcat tag
 * {@code SeamTime}, each step also wrapped in a {@code Seam:<step>} trace section. Main thread only.
 * Off (no log, no trace) unless {@link #on} is set for the current move.
 */
public final class SeamTime {
    public static final String TAG = "SeamTime";
    private static final long OFF = Long.MIN_VALUE;

    /** Whether the current move is cross-kind; set by the caller at the top of each move. */
    public static boolean on;
    /** The move's travel fraction, progress into the other half and crossing state, set by the caller. */
    public static float fraction;
    public static float otherProgress;
    public static boolean crossing;

    private static long sMoveStart = OFF;
    private static String sSlowName;
    private static long sSlowNs;

    private static long sLastFrameNs;
    private static long sLastCrossMs;
    private static boolean sFramesPosted;
    private static final Choreographer.FrameCallback FRAMES = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            long now = SystemClock.uptimeMillis();
            float delta = sLastFrameNs == 0 ? 0f : (frameTimeNanos - sLastFrameNs) / 1e6f;
            sLastFrameNs = frameTimeNanos;
            Log.d(TAG, "t=" + now + " frame dt=" + fmt(delta) + "ms");
            if (now - sLastCrossMs < 400) {
                Choreographer.getInstance().postFrameCallback(this);
            } else {
                sFramesPosted = false;
                sLastFrameNs = 0;
                Log.d(TAG, "t=" + now + " frames stop");
            }
        }
    };

    private SeamTime() {}

    /** Starts a step: a trace section and its start time; OFF while not {@link #on}. */
    public static long begin(@NonNull String step) {
        if (!on) return OFF;
        Trace.beginSection("Seam:" + step);
        return System.nanoTime();
    }

    /** Ends a once-per-crossing step begun by {@link #begin}, logging its duration. */
    public static void once(@NonNull String step, long t0) {
        if (t0 == OFF) return;
        long ns = System.nanoTime() - t0;
        Trace.endSection();
        Log.d(TAG, "t=" + SystemClock.uptimeMillis() + " step=" + step + " ms=" + fmt(ns / 1e6f));
        track(step, ns);
    }

    /** Ends a per-move sub-step begun by {@link #begin}; only the slowest is logged per move. */
    public static void sub(@NonNull String step, long t0) {
        if (t0 == OFF) return;
        long ns = System.nanoTime() - t0;
        Trace.endSection();
        track(step, ns);
    }

    private static void track(String step, long ns) {
        if (sMoveStart != OFF && ns > sSlowNs) {
            sSlowNs = ns;
            sSlowName = step;
        }
    }

    /** Whether a move's total is being timed (a nested travel call leaves it to the outer one). */
    public static boolean inMove() {
        return sMoveStart != OFF;
    }

    /** Starts a cross-kind move's total; also keeps the per-frame logger running. */
    public static void moveStart() {
        if (!on) {
            sMoveStart = OFF;
            return;
        }
        Trace.beginSection("Seam:move");
        sMoveStart = System.nanoTime();
        sSlowName = null;
        sSlowNs = 0;
        sLastCrossMs = SystemClock.uptimeMillis();
        if (!sFramesPosted) {
            sFramesPosted = true;
            sLastFrameNs = 0;
            Choreographer.getInstance().postFrameCallback(FRAMES);
        }
    }

    /** Ends a move begun by {@link #moveStart}: total, slowest sub-step, travel fraction. */
    public static void moveEnd() {
        on = false;
        if (sMoveStart == OFF) return;
        long ns = System.nanoTime() - sMoveStart;
        sMoveStart = OFF;
        Trace.endSection();
        Log.d(TAG, "t=" + SystemClock.uptimeMillis() + " move ms=" + fmt(ns / 1e6f)
            + " slowest=" + sSlowName + " slowMs=" + fmt(sSlowNs / 1e6f)
            + " fraction=" + fmt(fraction) + " otherProgress=" + fmt(otherProgress)
            + " crossing=" + crossing);
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
