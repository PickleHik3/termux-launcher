package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Paused time is left out of speech deadlines, so a paused reading is never timed out. */
public class TaiPauseClockTest {

    @Test
    public void countsClosedPausesAndTheOneUnderWay() {
        TaiPauseClock clock = new TaiPauseClock();
        assertEquals(0L, clock.totalPausedMs(100L));
        clock.pause(100L);
        assertTrue(clock.isPaused());
        assertEquals(50L, clock.totalPausedMs(150L));
        clock.resume(400L);
        assertFalse(clock.isPaused());
        assertEquals(300L, clock.totalPausedMs(1_000L));
        clock.pause(1_000L);
        assertEquals(310L, clock.totalPausedMs(1_010L));
    }

    @Test
    public void pauseAndResumeAreIdempotent() {
        TaiPauseClock clock = new TaiPauseClock();
        clock.resume(10L);
        assertEquals(0L, clock.totalPausedMs(10L));
        clock.pause(10L);
        clock.pause(50L);
        clock.resume(70L);
        clock.resume(90L);
        assertEquals(60L, clock.totalPausedMs(200L));
    }

    @Test
    public void aDeadlineMovesOutByThePausesSinceTheWaitBegan() {
        TaiPauseClock clock = new TaiPauseClock();
        clock.pause(0L);
        clock.resume(500L);
        // A wait of 1 s starting at 1000: the earlier half second of pause is not its business.
        long pausedAtStart = clock.totalPausedMs(1_000L);
        assertEquals(1_000L, clock.remainingMs(1_000L, 1_000L, pausedAtStart, 1_000L));
        assertEquals(200L, clock.remainingMs(1_000L, 1_000L, pausedAtStart, 1_800L));
        // Paused at 1800: the remaining time stands still however long the pause lasts.
        clock.pause(1_800L);
        assertEquals(200L, clock.remainingMs(1_000L, 1_000L, pausedAtStart, 60_000L));
        clock.resume(60_000L);
        assertEquals(100L, clock.remainingMs(1_000L, 1_000L, pausedAtStart, 60_100L));
        assertTrue(clock.remainingMs(1_000L, 1_000L, pausedAtStart, 60_300L) < 0L);
    }
}
