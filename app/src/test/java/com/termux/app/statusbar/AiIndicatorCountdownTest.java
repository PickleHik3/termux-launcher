package com.termux.app.statusbar;

import static org.junit.Assert.*;

import com.termux.ai.TaiRuntimePresence;

import org.junit.Test;

/** The AI glyph's countdown text and its "the runtime was killed" rule. */
public class AiIndicatorCountdownTest {

    @Test public void lastMinuteCountsSeconds() {
        assertEquals("59s", AiIndicatorController.countdownText(59_400L));
        assertEquals("0s", AiIndicatorController.countdownText(0L));
        assertEquals("0s", AiIndicatorController.countdownText(-5_000L));
    }

    @Test public void minutesReadAsClockUnderTen() {
        assertEquals("1:00", AiIndicatorController.countdownText(60_000L));
        assertEquals("9:05", AiIndicatorController.countdownText(545_000L));
    }

    @Test public void longWaitsCollapseToWholeUnits() {
        assertEquals("10m", AiIndicatorController.countdownText(10 * 60_000L));
        assertEquals("2h", AiIndicatorController.countdownText(150 * 60_000L));
    }

    @Test public void aLoadedSnapshotThatStoppedPublishingIsStale() {
        long now = 1_000_000L;
        TaiRuntimePresence.Snapshot fresh = snapshot(now - 1_000L);
        TaiRuntimePresence.Snapshot old = snapshot(now - AiIndicatorController.SNAPSHOT_STALE_MS - 1L);
        assertFalse(AiIndicatorController.isStale(fresh, now));
        assertTrue(AiIndicatorController.isStale(old, now));
        // An idle runtime is never "stale": there is nothing left to expire.
        assertFalse(AiIndicatorController.isStale(TaiRuntimePresence.empty(), now));
    }

    private static TaiRuntimePresence.Snapshot snapshot(long publishedAtMs) {
        return TaiRuntimePresence.snapshotForTest(true, false, false, "m", 0L, publishedAtMs);
    }
}
