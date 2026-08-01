package com.termux.view;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TerminalRenderMetricsTest {

    @Test
    public void recordsRenderTimePercentilesAndDroppedFrameEstimate() {
        TerminalRenderMetrics metrics = new TerminalRenderMetrics();
        long budget = 10_000_000L;
        metrics.recordDraw(100_000_000L, 104_000_000L, budget);
        metrics.recordDraw(110_000_000L, 118_000_000L, budget);
        metrics.recordDraw(120_000_000L, 145_000_000L, budget);

        TerminalRenderMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(3L, snapshot.drawCount);
        assertEquals(37_000_000L, snapshot.totalRenderNanos);
        assertEquals(8_000_000L, snapshot.medianRenderNanos);
        assertEquals(25_000_000L, snapshot.p95RenderNanos);
        assertEquals(1L, snapshot.slowDrawCount);
        assertEquals(2L, snapshot.estimatedDroppedFrames);
        assertEquals(2L, snapshot.activeFrameTimeCount);
        assertEquals(10_000_000L, snapshot.medianActiveFrameTimeNanos);
    }

    @Test
    public void idleGapsAreNotReportedAsFrameTimeOrDroppedFrames() {
        TerminalRenderMetrics metrics = new TerminalRenderMetrics();
        metrics.recordDraw(10L, 20L, 16L);
        metrics.recordDraw(300_000_020L, 300_000_030L, 16L);

        TerminalRenderMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(0L, snapshot.activeFrameTimeCount);
        assertEquals(0L, snapshot.estimatedDroppedFrames);
    }

    @Test
    public void resetClearsSamplesButKeepsTheKnownDisplayBudget() {
        TerminalRenderMetrics metrics = new TerminalRenderMetrics();
        metrics.recordDraw(100L, 110L, 20L);
        metrics.reset();

        TerminalRenderMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(0L, snapshot.drawCount);
        assertEquals(20L, snapshot.frameBudgetNanos);
        assertEquals(0L, snapshot.p95RenderNanos);
    }
}
