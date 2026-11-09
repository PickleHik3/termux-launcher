package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerAzSideTest {

    @Test
    public void todaysDrawerIsTheDefault() {
        assertEquals(AppDrawerAzSide.RIGHT, AppDrawerAzSide.fromValue(null));
        assertEquals(AppDrawerAzSide.RIGHT, AppDrawerAzSide.fromValue("sideways"));
        assertEquals(AppDrawerAzSide.RIGHT, AppDrawerAzSide.fromValue("right"));
    }

    @Test
    public void everyValueRoundTrips() {
        for (AppDrawerAzSide side : AppDrawerAzSide.values()) {
            assertEquals(side, AppDrawerAzSide.fromValue(side.value));
        }
    }

    @Test
    public void theListGivesUpTheStripOnTheStripsEdgeOnly() {
        assertEquals(0, AppDrawerAzSide.RIGHT.leftInsetPx(40));
        assertEquals(40, AppDrawerAzSide.RIGHT.rightInsetPx(40));
        assertEquals(40, AppDrawerAzSide.LEFT.leftInsetPx(40));
        assertEquals(0, AppDrawerAzSide.LEFT.rightInsetPx(40));
        assertEquals(0, AppDrawerAzSide.OFF.leftInsetPx(40));
        assertEquals(0, AppDrawerAzSide.OFF.rightInsetPx(40));
    }

    @Test
    public void offHidesTheStripAndLeftMirrorsIt() {
        assertFalse(AppDrawerAzSide.OFF.shown());
        assertTrue(AppDrawerAzSide.LEFT.shown());
        assertTrue(AppDrawerAzSide.LEFT.mirrored());
        assertFalse(AppDrawerAzSide.RIGHT.mirrored());
    }
}
