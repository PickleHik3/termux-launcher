package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class OverviewExitLandingTest {

    private static final WallRow.Position ORIGIN = WallRow.homePage(1);
    private static final WallRow.Position SCROLLED_TO = WallRow.window(0);

    @Test
    public void aPersonClosingLandsOnTheCardTheyChose() {
        OverviewExitLanding landing = new OverviewExitLanding();
        landing.opened(ORIGIN);
        assertEquals(SCROLLED_TO, landing.landing(SCROLLED_TO));
    }

    @Test
    public void theHomeExitStaysOnThePageTheOverviewWasOpenedFrom() {
        OverviewExitLanding landing = new OverviewExitLanding();
        landing.opened(ORIGIN);
        assertEquals(ORIGIN, landing.exitStarted());
        assertEquals(ORIGIN, landing.landing(SCROLLED_TO));
    }

    @Test
    public void withoutAnOriginTheExitFallsBackToTheFocusedCard() {
        OverviewExitLanding landing = new OverviewExitLanding();
        landing.opened(null);
        assertNull(landing.exitStarted());
        assertEquals(SCROLLED_TO, landing.landing(SCROLLED_TO));
    }
}
