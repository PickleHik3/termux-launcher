package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppDrawerHomeCoverPolicyTest {
    @Test public void closedShowsHomeFully() {
        assertEquals(1f, AppDrawerHomeCoverPolicy.homeAlpha(0f), 0f);
    }

    @Test public void fullyOpenHidesHome() {
        assertEquals(0f, AppDrawerHomeCoverPolicy.homeAlpha(1f), 0f);
    }

    @Test public void alphaFollowsProgressLinearly() {
        assertEquals(0.75f, AppDrawerHomeCoverPolicy.homeAlpha(0.25f), 1e-6f);
        assertEquals(0.5f, AppDrawerHomeCoverPolicy.homeAlpha(0.5f), 1e-6f);
    }

    @Test public void overshootIsClamped() {
        assertEquals(0f, AppDrawerHomeCoverPolicy.homeAlpha(1.2f), 0f);
        assertEquals(1f, AppDrawerHomeCoverPolicy.homeAlpha(-0.1f), 0f);
    }
}
