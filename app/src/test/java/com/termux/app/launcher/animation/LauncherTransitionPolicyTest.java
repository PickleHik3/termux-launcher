package com.termux.app.launcher.animation;

import static com.termux.app.launcher.animation.LauncherTransitionPolicy.Source.DOCK;
import static com.termux.app.launcher.animation.LauncherTransitionPolicy.Source.DRAWER;
import static com.termux.app.launcher.animation.LauncherTransitionPolicy.Source.HOME;
import static com.termux.app.launcher.animation.LauncherTransitionPolicy.Source.NONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherTransitionPolicyTest {

    @Test
    public void safeModeLatchesOnlyAtTheThresholdOfRealErrors() {
        assertFalse(LauncherTransitionPolicy.shouldLatchSafeMode(0));
        assertFalse(LauncherTransitionPolicy.shouldLatchSafeMode(2));
        assertTrue(LauncherTransitionPolicy.shouldLatchSafeMode(3));
    }

    @Test
    public void safeModeLapsesOnTheFirstStartOfAProcessOnly() {
        assertTrue(LauncherTransitionPolicy.shouldClearSafeModeOnStart(true, true));
        assertFalse(LauncherTransitionPolicy.shouldClearSafeModeOnStart(true, false));
        assertFalse(LauncherTransitionPolicy.shouldClearSafeModeOnStart(false, true));
    }

    @Test
    public void dockWinsOverEverything() {
        assertEquals(DOCK, LauncherTransitionPolicy.pickSource(true, true, true, false));
        assertEquals(DOCK, LauncherTransitionPolicy.pickSource(true, false, false, true));
    }

    @Test
    public void homeIconWhenTheDrawerIsClosed() {
        assertEquals(HOME, LauncherTransitionPolicy.pickSource(false, true, false, false));
        assertEquals(NONE, LauncherTransitionPolicy.pickSource(false, false, true, false));
    }

    @Test
    public void drawerIconWhenTheDrawerIsOpen() {
        assertEquals(DRAWER, LauncherTransitionPolicy.pickSource(false, true, true, true));
        assertEquals(NONE, LauncherTransitionPolicy.pickSource(false, true, false, true));
    }

    @Test
    public void noIconIsNone() {
        assertEquals(NONE, LauncherTransitionPolicy.pickSource(false, false, false, false));
        assertEquals(NONE, LauncherTransitionPolicy.pickSource(false, false, false, true));
    }
}
