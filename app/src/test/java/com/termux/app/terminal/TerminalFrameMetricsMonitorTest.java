package com.termux.app.terminal;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class TerminalFrameMetricsMonitorTest {

    @Test
    public void recordsWindowDurationsAndDeadlineMisses() {
        TerminalFrameMetricsMonitor monitor = new TerminalFrameMetricsMonitor();
        monitor.recordFrame(8_000_000L, 3_000_000L, 10_000_000L, false, 0);
        monitor.recordFrame(12_000_000L, 5_000_000L, 10_000_000L, false, 2);
        monitor.recordFrame(25_000_000L, 9_000_000L, 10_000_000L, false, 0);

        TerminalFrameMetricsMonitor.Snapshot snapshot = monitor.snapshot();
        assertEquals(3L, snapshot.frameCount);
        assertEquals(45_000_000L, snapshot.totalDurationNanos);
        assertEquals(2L, snapshot.jankyFrameCount);
        assertEquals(3L, snapshot.estimatedDroppedFrames);
        assertEquals(2L, snapshot.metricsReportsDropped);
        assertEquals(12_000_000L, snapshot.medianTotalDurationNanos);
        assertEquals(25_000_000L, snapshot.p95TotalDurationNanos);
        assertEquals(5_000_000L, snapshot.medianDrawDurationNanos);
    }

    @Test
    public void firstDrawAndUnavailableMetricsAreExcluded() {
        TerminalFrameMetricsMonitor monitor = new TerminalFrameMetricsMonitor();
        monitor.recordFrame(100_000_000L, 50_000_000L, 10_000_000L, true, 1);
        monitor.recordFrame(-1L, 1L, 10_000_000L, false, 1);

        TerminalFrameMetricsMonitor.Snapshot snapshot = monitor.snapshot();
        assertEquals(0L, snapshot.frameCount);
        assertEquals(2L, snapshot.metricsReportsDropped);
    }

    @Test
    public void resetClearsFrameCounters() {
        TerminalFrameMetricsMonitor monitor = new TerminalFrameMetricsMonitor();
        monitor.recordFrame(20L, 10L, 10L, false, 0);
        monitor.reset();

        TerminalFrameMetricsMonitor.Snapshot snapshot = monitor.snapshot();
        assertEquals(0L, snapshot.frameCount);
        assertEquals(0L, snapshot.jankyFrameCount);
        assertEquals(0L, snapshot.p95TotalDurationNanos);
    }
}
