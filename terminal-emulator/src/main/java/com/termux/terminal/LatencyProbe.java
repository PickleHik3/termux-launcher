package com.termux.terminal;

import android.os.Build;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;

/**
 * Measures commit to echo: an async {@code KeyToEcho} trace section opens when a key is committed
 * and closes at the first {@code Terminal.render} that follows shell output, so a Perfetto query
 * reads the whole key path in one slice. Main thread only, as commits, output parsing and drawing
 * all are; allocation-free after class load.
 *
 * <p>A commit that never echoes (a modifier, a key the shell ignores) is dropped when the queue is
 * full or after {@link #STALE_MS}, so it cannot hold a section open for ever.
 */
public final class LatencyProbe {

    private static final String LOG_TAG = "TLLatency";
    private static final int PENDING = 8;
    private static final long STALE_MS = 1000L;
    private static final int SUMMARY_EVERY = 50;

    private static final long[] sStartNanos = new long[PENDING];
    private static final int[] sCookies = new int[PENDING];
    private static int sCount;
    private static int sNextCookie;
    private static boolean sOutputSeen;

    private static final PercentileRing sRing = new PercentileRing(SUMMARY_EVERY);
    private static int sSinceSummary;
    private static boolean sDebugBuild;
    private static final DebugSwitch sLogSwitch =
        new DebugSwitch("debug.tl.latency_log", false, DebugSwitch.SYSTEM_PROPERTIES);

    private LatencyProbe() {}

    /** Called once by the app: the logcat summary exists in debug builds only. */
    public static void setDebugBuild(boolean debug) {
        sDebugBuild = debug;
    }

    /** A key was committed (released and resolved). */
    public static void commit() {
        long now = System.nanoTime();
        while (sCount > 0 && (sCount == PENDING || now - sStartNanos[0] > STALE_MS * 1_000_000L)) {
            dropOldest();
        }
        int cookie = ++sNextCookie;
        sStartNanos[sCount] = now;
        sCookies[sCount] = cookie;
        sCount++;
        sOutputSeen = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.beginAsyncSection("KeyToEcho", cookie);
    }

    /** Shell output was parsed into the emulator. */
    public static void outputArrived() {
        if (sCount > 0) sOutputSeen = true;
    }

    /** A terminal frame has been drawn. */
    public static void rendered() {
        if (!sOutputSeen || sCount == 0) return;
        long now = System.nanoTime();
        boolean log = sDebugBuild && sLogSwitch.isOn(SystemClock.uptimeMillis());
        for (int i = 0; i < sCount; i++) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                Trace.endAsyncSection("KeyToEcho", sCookies[i]);
            if (log) sRing.add((now - sStartNanos[i]) / 1_000_000L);
        }
        if (log) {
            sSinceSummary += sCount;
            if (sSinceSummary >= SUMMARY_EVERY) {
                sSinceSummary = 0;
                Log.i(LOG_TAG, "commit->echo ms p50=" + sRing.percentile(50) + " p90="
                    + sRing.percentile(90) + " max=" + sRing.max() + " n=" + sRing.size());
            }
        }
        sCount = 0;
        sOutputSeen = false;
    }

    private static void dropOldest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            Trace.endAsyncSection("KeyToEcho", sCookies[0]);
        System.arraycopy(sStartNanos, 1, sStartNanos, 0, sCount - 1);
        System.arraycopy(sCookies, 1, sCookies, 0, sCount - 1);
        sCount--;
    }
}
