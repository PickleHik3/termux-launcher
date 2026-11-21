package com.termux.launcherctl;

import com.termux.ai.TaiSettings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The LAN window is read once from its wall-clock stamp, then runs on the monotonic clock. */
public class LauncherCtlLanDeadlineTest {

    private static final long HOUR = 60L * 60 * 1000;
    private static final long WALL_NOW = 1_800_000_000_000L;
    private static final long ELAPSED_NOW = 5_000_000L;

    @Test
    public void windowStartedElevenHoursAgo_endsOneHourLaterOnTheMonotonicClock() {
        long started = WALL_NOW - (TaiSettings.LAN_SESSION_MAX_MS - HOUR);

        long deadline = LauncherCtlApiServer.lanDeadlineOnElapsedClock(started, WALL_NOW, ELAPSED_NOW);

        assertEquals(ELAPSED_NOW + HOUR, deadline);
        assertFalse(LauncherCtlApiServer.isLanSessionOver(deadline, ELAPSED_NOW + HOUR - 1));
        assertTrue(LauncherCtlApiServer.isLanSessionOver(deadline, ELAPSED_NOW + HOUR + 1));
    }

    @Test
    public void wallClockMovingBackAfterStart_doesNotStretchTheWindow() {
        long deadline = LauncherCtlApiServer.lanDeadlineOnElapsedClock(WALL_NOW, WALL_NOW, ELAPSED_NOW);

        // The wall clock is no longer an input: a day set back changes nothing; 12h of uptime ends it.
        assertTrue(LauncherCtlApiServer.isLanSessionOver(deadline,
            ELAPSED_NOW + TaiSettings.LAN_SESSION_MAX_MS + 1));
    }

    @Test
    public void startStampedInTheFuture_getsAtMostOneWindow() {
        long deadline = LauncherCtlApiServer.lanDeadlineOnElapsedClock(WALL_NOW + 24 * HOUR, WALL_NOW, ELAPSED_NOW);

        assertEquals(ELAPSED_NOW + TaiSettings.LAN_SESSION_MAX_MS, deadline);
    }

    @Test
    public void spentWindow_isOverAtOnce_andNoStampMeansNoWindow() {
        long spent = LauncherCtlApiServer.lanDeadlineOnElapsedClock(
            WALL_NOW - TaiSettings.LAN_SESSION_MAX_MS - HOUR, WALL_NOW, ELAPSED_NOW);

        assertTrue(LauncherCtlApiServer.isLanSessionOver(spent, ELAPSED_NOW));
        assertEquals(0L, LauncherCtlApiServer.lanDeadlineOnElapsedClock(0L, WALL_NOW, ELAPSED_NOW));
        assertFalse(LauncherCtlApiServer.isLanSessionOver(0L, ELAPSED_NOW));
    }
}
