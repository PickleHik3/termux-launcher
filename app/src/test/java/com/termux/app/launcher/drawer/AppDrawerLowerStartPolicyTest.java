package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppDrawerLowerStartPolicyTest {

    @Test
    public void offLeavesTheListAlone() {
        assertEquals(0, AppDrawerLowerStartPolicy.offsetPx(false, true, false, 2000, 200));
    }

    @Test
    public void onStartsTheListAtTheMidline() {
        assertEquals(800, AppDrawerLowerStartPolicy.offsetPx(true, true, false, 2000, 200));
    }

    @Test
    public void searchingLandscapeAndUnmeasuredStepAside() {
        assertEquals(0, AppDrawerLowerStartPolicy.offsetPx(true, true, true, 2000, 200));
        assertEquals(0, AppDrawerLowerStartPolicy.offsetPx(true, false, false, 2000, 200));
        assertEquals(0, AppDrawerLowerStartPolicy.offsetPx(true, true, false, 0, 200));
    }

    @Test
    public void aListAlreadyBelowTheMidlineIsNotRaised() {
        assertEquals(0, AppDrawerLowerStartPolicy.offsetPx(true, true, false, 300, 200));
    }
}
