package com.termux.app.chrome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GestureNavigationTest {

    @Test public void gestureHomePressOnATerminalPageLandsWithoutTheSpring() {
        boolean gestures = GestureNavigation.resolve(2, 0, 0, false);
        assertFalse(HomeExitPolicy.animatesWall(true, gestures));
    }

    @Test public void buttonHomePressKeepsTheSpring() {
        assertTrue(HomeExitPolicy.animatesWall(true, GestureNavigation.resolve(0, 0, 0, false)));
        assertTrue(HomeExitPolicy.animatesWall(true, GestureNavigation.resolve(1, 0, 0, false)));
    }

    @Test public void notVisibleNeverAnimates() {
        assertFalse(HomeExitPolicy.animatesWall(false, false));
        assertFalse(HomeExitPolicy.animatesWall(false, true));
    }

    @Test public void unreadableSettingFallsBackToInsets() {
        assertTrue(GestureNavigation.resolve(GestureNavigation.UNKNOWN, 48, 0, true));
        assertFalse(GestureNavigation.resolve(GestureNavigation.UNKNOWN, 48, 48, true));
    }

    @Test public void nothingAnsweringMeansButtons() {
        assertFalse(GestureNavigation.resolve(GestureNavigation.UNKNOWN, 0, 0, false));
        assertFalse(GestureNavigation.resolve(GestureNavigation.UNKNOWN, 0, 0, true));
    }
}
