package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StatsPollCadenceTest {

    @Test
    public void runsAtTheRequestedPeriodWhileAReadingIsOnScreen() {
        assertEquals(6000L, StatsPollCadence.periodMs(true, false, true, true, 6000L));
        assertEquals(18000L, StatsPollCadence.periodMs(true, false, true, true, 18000L));
    }

    @Test
    public void neverFasterThanTheFloorTheCardIncluded() {
        // The card used to ask for 1.5 s; one privileged command per 1.5 s is the floor's reason.
        assertEquals(StatsPollCadence.MIN_PERIOD_MS,
            StatsPollCadence.periodMs(true, true, true, true, 1500L));
        assertEquals(StatsPollCadence.MIN_PERIOD_MS,
            StatsPollCadence.periodMs(true, false, true, true, 0L));
    }

    @Test
    public void pausedWhileNothingShowsAReading() {
        assertEquals(StatsPollCadence.PAUSED,
            StatsPollCadence.periodMs(false, false, true, true, 6000L));
    }

    @Test
    public void theOpenCardIsAReadingOnScreenOfItsOwn() {
        assertEquals(StatsPollCadence.MIN_PERIOD_MS,
            StatsPollCadence.periodMs(false, true, true, true, 2000L));
    }

    @Test
    public void pausedWithTheScreenOffOrTheLauncherBehindAnotherApp() {
        assertEquals(StatsPollCadence.PAUSED,
            StatsPollCadence.periodMs(true, true, false, true, 6000L));
        assertEquals(StatsPollCadence.PAUSED,
            StatsPollCadence.periodMs(true, true, true, false, 6000L));
        assertEquals(StatsPollCadence.PAUSED,
            StatsPollCadence.periodMs(true, false, false, false, 6000L));
    }

    @Test
    public void theFirstStartSamplesAtOnce() {
        assertEquals(0L, StatsPollCadence.firstDelayMs(false, -1L, 6000L));
        assertEquals(0L, StatsPollCadence.firstDelayMs(true, -1L, 2000L));
    }

    @Test
    public void aRestartWaitsOutThePeriodFromThePreviousSample() {
        // The regression: every status-bar refresh restarted the sampler, and every restart
        // sampled at once, so the six-second cadence became a privileged command per refresh.
        assertEquals(5800L, StatsPollCadence.firstDelayMs(false, 200L, 6000L));
        assertEquals(1000L, StatsPollCadence.firstDelayMs(false, 5000L, 6000L));
        assertEquals(0L, StatsPollCadence.firstDelayMs(false, 6000L, 6000L));
        assertEquals(0L, StatsPollCadence.firstDelayMs(false, 60_000L, 6000L));
    }

    @Test
    public void openingTheCardMayCutTheWaitShortButNotBelowTheGap() {
        assertEquals(800L, StatsPollCadence.firstDelayMs(true, 200L, 2000L));
        assertEquals(0L, StatsPollCadence.firstDelayMs(true, 1500L, 2000L));
    }

    @Test
    public void aStartRepeatingTheRunningSettingsChangesNothing() {
        assertFalse(StatsPollCadence.startChangesSchedule(true, 6000L, false, 6000L, false));
        assertTrue(StatsPollCadence.startChangesSchedule(false, 6000L, false, 6000L, false));
        assertTrue(StatsPollCadence.startChangesSchedule(true, 6000L, false, 2000L, true));
        assertTrue(StatsPollCadence.startChangesSchedule(true, 6000L, false, 18000L, false));
        assertTrue(StatsPollCadence.startChangesSchedule(true, 2000L, true, 2000L, false));
    }
}
