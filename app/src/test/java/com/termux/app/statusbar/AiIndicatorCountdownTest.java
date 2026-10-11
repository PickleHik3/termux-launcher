package com.termux.app.statusbar;

import static org.junit.Assert.*;

import com.termux.ai.TaiRuntimePresence;

import org.junit.Test;

/** The AI glyph's spoken countdown and its "the runtime was killed" rule. */
public class AiIndicatorCountdownTest {

    @Test public void theSpokenCountdownIsWholeMinutesAsTheClockWouldRead() {
        assertEquals("9:41 reads as nine", 9, AiIndicatorController.wholeMinutesLeft(581_000L));
        assertEquals(1, AiIndicatorController.wholeMinutesLeft(60_000L));
        assertEquals("under a minute is its own sentence",
            0, AiIndicatorController.wholeMinutesLeft(59_400L));
        assertEquals(0, AiIndicatorController.wholeMinutesLeft(-5_000L));
        assertEquals(150, AiIndicatorController.wholeMinutesLeft(150 * 60_000L));
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
