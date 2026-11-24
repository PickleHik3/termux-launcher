package com.termux.app.wayland;

import static com.termux.app.wayland.WaylandAppRunner.StoppedAction.FAIL;
import static com.termux.app.wayland.WaylandAppRunner.StoppedAction.IGNORE;
import static com.termux.app.wayland.WaylandAppRunner.StoppedAction.RELEASE;
import static com.termux.app.wayland.WaylandAppRunner.StoppedAction.RESTART;
import static com.termux.app.wayland.WaylandAppRunner.onHostStopped;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** When a stopped host lets the runner unbind (and the process end), and when it binds on. */
public class WaylandAppRunnerStoppedActionTest {

    @Test
    public void anIdleStopWithNothingWaitingReleasesTheService() {
        assertEquals(RELEASE, onHostStopped(false, false, true, false));
    }

    @Test
    public void aStateFromBeforeTheStartIsIgnored() {
        assertEquals(IGNORE, onHostStopped(true, false, true, false));
        assertEquals(IGNORE, onHostStopped(true, false, false, true));
    }

    @Test
    public void aClientOrLaunchWaitingStartsTheHostAgainOnTheSameBinding() {
        assertEquals(RESTART, onHostStopped(false, false, false, false));
        assertEquals(RESTART, onHostStopped(false, false, true, true));
    }

    @Test
    public void aStartThatAnsweredStoppedWhileSomethingWaitsFails() {
        assertEquals(FAIL, onHostStopped(true, true, false, false));
        assertEquals(FAIL, onHostStopped(true, true, true, true));
    }

    @Test
    public void aStartThatAnsweredStoppedWithNothingWaitingReleases() {
        assertEquals(RELEASE, onHostStopped(true, true, true, false));
    }
}
