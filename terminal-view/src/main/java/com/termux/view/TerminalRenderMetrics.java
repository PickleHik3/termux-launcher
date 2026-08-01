package com.termux.view;

import java.util.Arrays;

/** Allocation-free counters for the terminal pane's UI-thread draw hot path. */
public final class TerminalRenderMetrics {

    private static final int SAMPLE_CAPACITY = 240;
    private static final long ACTIVE_FRAME_GAP_NANOS = 250_000_000L;

    private final long[] renderSamples = new long[SAMPLE_CAPACITY];
    private final long[] frameTimeSamples = new long[SAMPLE_CAPACITY];
    private int renderSampleCount;
    private int renderSampleCursor;
    private int frameTimeSampleCount;
    private int frameTimeSampleCursor;
    private long drawCount;
    private long totalRenderNanos;
    private long maxRenderNanos;
    private long slowDrawCount;
    private long estimatedDroppedFrames;
    private long totalActiveFrameTimeNanos;
    private long maxActiveFrameTimeNanos;
    private long lastDrawStartNanos;
    private long frameBudgetNanos;

    synchronized void recordDraw(long drawStartNanos, long drawEndNanos, long budgetNanos) {
        long renderNanos = Math.max(0L, drawEndNanos - drawStartNanos);
        if (budgetNanos > 0L) frameBudgetNanos = budgetNanos;
        drawCount++;
        totalRenderNanos += renderNanos;
        maxRenderNanos = Math.max(maxRenderNanos, renderNanos);
        addRenderSample(renderNanos);

        if (frameBudgetNanos > 0L && renderNanos > frameBudgetNanos) {
            slowDrawCount++;
            estimatedDroppedFrames += (renderNanos - 1L) / frameBudgetNanos;
        }

        if (lastDrawStartNanos > 0L) {
            long frameTimeNanos = drawStartNanos - lastDrawStartNanos;
            if (frameTimeNanos > 0L && frameTimeNanos <= ACTIVE_FRAME_GAP_NANOS) {
                totalActiveFrameTimeNanos += frameTimeNanos;
                maxActiveFrameTimeNanos = Math.max(maxActiveFrameTimeNanos, frameTimeNanos);
                addFrameTimeSample(frameTimeNanos);
            }
        }
        lastDrawStartNanos = drawStartNanos;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(drawCount, totalRenderNanos, maxRenderNanos, slowDrawCount,
            estimatedDroppedFrames, frameBudgetNanos, frameTimeSampleCount,
            totalActiveFrameTimeNanos, maxActiveFrameTimeNanos,
            percentile(renderSamples, renderSampleCount, 50),
            percentile(renderSamples, renderSampleCount, 95),
            percentile(frameTimeSamples, frameTimeSampleCount, 50),
            percentile(frameTimeSamples, frameTimeSampleCount, 95));
    }

    public synchronized void reset() {
        renderSampleCount = 0;
        renderSampleCursor = 0;
        frameTimeSampleCount = 0;
        frameTimeSampleCursor = 0;
        drawCount = 0L;
        totalRenderNanos = 0L;
        maxRenderNanos = 0L;
        slowDrawCount = 0L;
        estimatedDroppedFrames = 0L;
        totalActiveFrameTimeNanos = 0L;
        maxActiveFrameTimeNanos = 0L;
        lastDrawStartNanos = 0L;
    }

    private void addRenderSample(long value) {
        renderSamples[renderSampleCursor] = value;
        renderSampleCursor = (renderSampleCursor + 1) % SAMPLE_CAPACITY;
        if (renderSampleCount < SAMPLE_CAPACITY) renderSampleCount++;
    }

    private void addFrameTimeSample(long value) {
        frameTimeSamples[frameTimeSampleCursor] = value;
        frameTimeSampleCursor = (frameTimeSampleCursor + 1) % SAMPLE_CAPACITY;
        if (frameTimeSampleCount < SAMPLE_CAPACITY) frameTimeSampleCount++;
    }

    private static long percentile(long[] values, int count, int percentile) {
        if (count == 0) return 0L;
        long[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        int index = Math.max(0, (int) Math.ceil((percentile / 100.0d) * count) - 1);
        return sorted[Math.min(index, count - 1)];
    }

    public static final class Snapshot {
        public final long drawCount;
        public final long totalRenderNanos;
        public final long maxRenderNanos;
        public final long slowDrawCount;
        public final long estimatedDroppedFrames;
        public final long frameBudgetNanos;
        public final long activeFrameTimeCount;
        public final long totalActiveFrameTimeNanos;
        public final long maxActiveFrameTimeNanos;
        public final long medianRenderNanos;
        public final long p95RenderNanos;
        public final long medianActiveFrameTimeNanos;
        public final long p95ActiveFrameTimeNanos;

        Snapshot(long drawCount, long totalRenderNanos, long maxRenderNanos,
                 long slowDrawCount, long estimatedDroppedFrames, long frameBudgetNanos,
                 long activeFrameTimeCount, long totalActiveFrameTimeNanos,
                 long maxActiveFrameTimeNanos, long medianRenderNanos, long p95RenderNanos,
                 long medianActiveFrameTimeNanos, long p95ActiveFrameTimeNanos) {
            this.drawCount = drawCount;
            this.totalRenderNanos = totalRenderNanos;
            this.maxRenderNanos = maxRenderNanos;
            this.slowDrawCount = slowDrawCount;
            this.estimatedDroppedFrames = estimatedDroppedFrames;
            this.frameBudgetNanos = frameBudgetNanos;
            this.activeFrameTimeCount = activeFrameTimeCount;
            this.totalActiveFrameTimeNanos = totalActiveFrameTimeNanos;
            this.maxActiveFrameTimeNanos = maxActiveFrameTimeNanos;
            this.medianRenderNanos = medianRenderNanos;
            this.p95RenderNanos = p95RenderNanos;
            this.medianActiveFrameTimeNanos = medianActiveFrameTimeNanos;
            this.p95ActiveFrameTimeNanos = p95ActiveFrameTimeNanos;
        }
    }
}
