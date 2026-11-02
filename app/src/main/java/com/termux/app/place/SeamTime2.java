package com.termux.app.place;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.view.FrameMetrics;
import android.view.Window;

import androidx.annotation.NonNull;

/**
 * TEMP diag(seam): never merge. The second set of seam timings, for what SeamTime left untimed:
 * the traversal after the first cross-kind move (measure, layout, draw: which phase, which view)
 * and the landing's settle work. Logs under tag SeamTime2, uptime ms first; Trace sections
 * Seam2:&lt;step&gt;.
 *
 * <ul>
 *   <li>{@code frame ...}: every frame whose FrameMetrics total is over {@link #SLOW_FRAME_MS},
 *   split into its phases (input, animation, measure+layout, draw, sync, command issue, swap,
 *   gpu), with the frame's intended vsync on the same uptime clock as {@code t=}.</li>
 *   <li>{@code step=...}: a timed step (settle sub-steps always; per-frame steps only when slow).</li>
 * </ul>
 */
public final class SeamTime2 {
    public static final String TAG = "SeamTime2";
    /** Frames slower than this are logged with their phases. */
    private static final float SLOW_FRAME_MS = 20f;

    private static boolean sWatching;

    private SeamTime2() {}

    /** Starts a timed step. */
    public static long begin(@NonNull String step) {
        Trace.beginSection("Seam2:" + step);
        return System.nanoTime();
    }

    /** Ends a step and logs it, however short. */
    public static void end(@NonNull String step, long t0) {
        long ns = System.nanoTime() - t0;
        Trace.endSection();
        log(step, ns);
    }

    /** Ends a step and logs it only when it took at least {@code minMs}. */
    public static void endIfSlow(@NonNull String step, long t0, float minMs) {
        long ns = System.nanoTime() - t0;
        Trace.endSection();
        if (ns / 1e6f >= minMs) log(step, ns);
    }

    /** A one-off marker line, so the log shows where a landing or a crossing began. */
    public static void mark(@NonNull String what) {
        Log.d(TAG, "t=" + SystemClock.uptimeMillis() + " mark=" + what);
    }

    private static void log(String step, long ns) {
        Log.d(TAG, "t=" + SystemClock.uptimeMillis() + " step=" + step + " ms=" + fmt(ns / 1e6f));
    }

    /** Logs every slow frame of the activity's window, phase by phase. Idempotent. */
    public static void watch(@NonNull Activity activity) {
        if (sWatching || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        sWatching = true;
        HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        Window window = activity.getWindow();
        window.addOnFrameMetricsAvailableListener((w, metrics, dropped) -> {
            float total = ms(metrics, FrameMetrics.TOTAL_DURATION);
            if (total < SLOW_FRAME_MS) return;
            long vsyncMs = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) / 1_000_000L;
            String gpu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? fmt(ms(metrics, FrameMetrics.GPU_DURATION)) : "-";
            Log.d(TAG, "t=" + SystemClock.uptimeMillis() + " frame vsync=" + vsyncMs
                + " total=" + fmt(total)
                + " delay=" + fmt(ms(metrics, FrameMetrics.UNKNOWN_DELAY_DURATION))
                + " input=" + fmt(ms(metrics, FrameMetrics.INPUT_HANDLING_DURATION))
                + " anim=" + fmt(ms(metrics, FrameMetrics.ANIMATION_DURATION))
                + " measureLayout=" + fmt(ms(metrics, FrameMetrics.LAYOUT_MEASURE_DURATION))
                + " draw=" + fmt(ms(metrics, FrameMetrics.DRAW_DURATION))
                + " sync=" + fmt(ms(metrics, FrameMetrics.SYNC_DURATION))
                + " issue=" + fmt(ms(metrics, FrameMetrics.COMMAND_ISSUE_DURATION))
                + " swap=" + fmt(ms(metrics, FrameMetrics.SWAP_BUFFERS_DURATION))
                + " gpu=" + gpu
                + " first=" + metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME)
                + " dropped=" + dropped);
        }, new Handler(thread.getLooper()));
    }

    private static float ms(FrameMetrics metrics, int id) {
        return metrics.getMetric(id) / 1e6f;
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
