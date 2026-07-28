package com.termux.app.terminal;

import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Display;
import android.view.FrameMetrics;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.Arrays;

/** Window-level frame, jank, allocation, and GC counters for terminal benchmarks. */
public final class TerminalFrameMetricsMonitor {

    private static final int SAMPLE_CAPACITY = 240;
    private static final String ART_BYTES_ALLOCATED = "art.gc.bytes-allocated";
    private static final String ART_GC_COUNT = "art.gc.gc-count";

    private final long[] totalDurationSamples = new long[SAMPLE_CAPACITY];
    private final long[] drawDurationSamples = new long[SAMPLE_CAPACITY];
    private int sampleCount;
    private int sampleCursor;
    private long frameCount;
    private long totalDurationNanos;
    private long totalDrawDurationNanos;
    private long maxTotalDurationNanos;
    private long maxDrawDurationNanos;
    private long jankyFrameCount;
    private long estimatedDroppedFrames;
    private long metricsReportsDropped;
    private long frameBudgetNanos = 16_666_667L;
    private long activeDurationNanos;
    private long activeStartNanos;
    private long allocatedBytesBaseline = readRuntimeStat(ART_BYTES_ALLOCATED);
    private long gcCountBaseline = readRuntimeStat(ART_GC_COUNT);

    @Nullable private Window window;
    @Nullable private HandlerThread handlerThread;
    @Nullable private Window.OnFrameMetricsAvailableListener listener;

    @RequiresApi(api = Build.VERSION_CODES.N)
    public synchronized void start(@NonNull Window nextWindow) {
        if (window == nextWindow && listener != null) return;
        stop();
        window = nextWindow;
        updateFrameBudget(nextWindow.getDecorView());
        handlerThread = new HandlerThread("terminal-frame-metrics");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        listener = (reportedWindow, frameMetrics, reportsDropped) -> {
            long deadline = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? frameMetrics.getMetric(FrameMetrics.DEADLINE) : frameBudgetNanos();
            recordFrame(frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION),
                frameMetrics.getMetric(FrameMetrics.DRAW_DURATION), deadline,
                frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L, reportsDropped);
        };
        nextWindow.addOnFrameMetricsAvailableListener(listener, handler);
        activeStartNanos = SystemClock.elapsedRealtimeNanos();
    }

    public synchronized void stop() {
        if (activeStartNanos > 0L) {
            activeDurationNanos += SystemClock.elapsedRealtimeNanos() - activeStartNanos;
            activeStartNanos = 0L;
        }
        if (window != null && listener != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            window.removeOnFrameMetricsAvailableListener(listener);
        }
        listener = null;
        window = null;
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
        }
    }

    synchronized void recordFrame(long totalNanos, long drawNanos, long deadlineNanos,
                                  boolean firstDraw, int reportsDropped) {
        metricsReportsDropped += Math.max(0, reportsDropped);
        if (firstDraw || totalNanos < 0L || drawNanos < 0L) return;
        long budget = deadlineNanos > 0L ? deadlineNanos : frameBudgetNanos;
        frameCount++;
        totalDurationNanos += totalNanos;
        totalDrawDurationNanos += drawNanos;
        maxTotalDurationNanos = Math.max(maxTotalDurationNanos, totalNanos);
        maxDrawDurationNanos = Math.max(maxDrawDurationNanos, drawNanos);
        totalDurationSamples[sampleCursor] = totalNanos;
        drawDurationSamples[sampleCursor] = drawNanos;
        sampleCursor = (sampleCursor + 1) % SAMPLE_CAPACITY;
        if (sampleCount < SAMPLE_CAPACITY) sampleCount++;
        if (budget > 0L && totalNanos > budget) {
            jankyFrameCount++;
            estimatedDroppedFrames += (totalNanos - 1L) / budget;
        }
    }

    public synchronized Snapshot snapshot() {
        // Refresh rate may change while the Activity remains started (Pong commonly moves between
        // 90 Hz and 120 Hz), so do not leave the reported/fallback budget pinned to onStart().
        if (window != null) updateFrameBudget(window.getDecorView());
        long active = activeDurationNanos;
        if (activeStartNanos > 0L) active += SystemClock.elapsedRealtimeNanos() - activeStartNanos;
        long allocated = difference(readRuntimeStat(ART_BYTES_ALLOCATED), allocatedBytesBaseline);
        long gcCount = difference(readRuntimeStat(ART_GC_COUNT), gcCountBaseline);
        return new Snapshot(frameCount, totalDurationNanos, totalDrawDurationNanos,
            maxTotalDurationNanos, maxDrawDurationNanos, jankyFrameCount,
            estimatedDroppedFrames, metricsReportsDropped, frameBudgetNanos, active,
            allocated, gcCount, percentile(totalDurationSamples, sampleCount, 50),
            percentile(totalDurationSamples, sampleCount, 95),
            percentile(drawDurationSamples, sampleCount, 50),
            percentile(drawDurationSamples, sampleCount, 95));
    }

    /** Reset all counters without detaching the low-overhead listener. */
    public synchronized void reset() {
        sampleCount = 0;
        sampleCursor = 0;
        frameCount = 0L;
        totalDurationNanos = 0L;
        totalDrawDurationNanos = 0L;
        maxTotalDurationNanos = 0L;
        maxDrawDurationNanos = 0L;
        jankyFrameCount = 0L;
        estimatedDroppedFrames = 0L;
        metricsReportsDropped = 0L;
        activeDurationNanos = 0L;
        activeStartNanos = listener == null ? 0L : SystemClock.elapsedRealtimeNanos();
        allocatedBytesBaseline = readRuntimeStat(ART_BYTES_ALLOCATED);
        gcCountBaseline = readRuntimeStat(ART_GC_COUNT);
    }

    private void updateFrameBudget(@NonNull View decorView) {
        Display display = decorView.getDisplay();
        float refreshRate = display == null ? 60f : display.getRefreshRate();
        frameBudgetNanos = refreshRate > 0f
            ? (long) (1_000_000_000d / refreshRate) : 16_666_667L;
    }

    private synchronized long frameBudgetNanos() {
        return frameBudgetNanos;
    }

    private static long readRuntimeStat(String name) {
        try {
            String value = Debug.getRuntimeStat(name);
            return value == null ? -1L : Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }

    private static long difference(long value, long baseline) {
        return value < 0L || baseline < 0L ? -1L : Math.max(0L, value - baseline);
    }

    private static long percentile(long[] values, int count, int percentile) {
        if (count == 0) return 0L;
        long[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        int index = Math.max(0, (int) Math.ceil((percentile / 100.0d) * count) - 1);
        return sorted[Math.min(index, count - 1)];
    }

    public static final class Snapshot {
        public final long frameCount;
        public final long totalDurationNanos;
        public final long totalDrawDurationNanos;
        public final long maxTotalDurationNanos;
        public final long maxDrawDurationNanos;
        public final long jankyFrameCount;
        public final long estimatedDroppedFrames;
        public final long metricsReportsDropped;
        public final long frameBudgetNanos;
        public final long activeDurationNanos;
        public final long allocatedBytes;
        public final long gcCount;
        public final long medianTotalDurationNanos;
        public final long p95TotalDurationNanos;
        public final long medianDrawDurationNanos;
        public final long p95DrawDurationNanos;

        Snapshot(long frameCount, long totalDurationNanos, long totalDrawDurationNanos,
                 long maxTotalDurationNanos, long maxDrawDurationNanos, long jankyFrameCount,
                 long estimatedDroppedFrames, long metricsReportsDropped, long frameBudgetNanos,
                 long activeDurationNanos, long allocatedBytes, long gcCount,
                 long medianTotalDurationNanos, long p95TotalDurationNanos,
                 long medianDrawDurationNanos, long p95DrawDurationNanos) {
            this.frameCount = frameCount;
            this.totalDurationNanos = totalDurationNanos;
            this.totalDrawDurationNanos = totalDrawDurationNanos;
            this.maxTotalDurationNanos = maxTotalDurationNanos;
            this.maxDrawDurationNanos = maxDrawDurationNanos;
            this.jankyFrameCount = jankyFrameCount;
            this.estimatedDroppedFrames = estimatedDroppedFrames;
            this.metricsReportsDropped = metricsReportsDropped;
            this.frameBudgetNanos = frameBudgetNanos;
            this.activeDurationNanos = activeDurationNanos;
            this.allocatedBytes = allocatedBytes;
            this.gcCount = gcCount;
            this.medianTotalDurationNanos = medianTotalDurationNanos;
            this.p95TotalDurationNanos = p95TotalDurationNanos;
            this.medianDrawDurationNanos = medianDrawDurationNanos;
            this.p95DrawDurationNanos = p95DrawDurationNanos;
        }
    }
}
