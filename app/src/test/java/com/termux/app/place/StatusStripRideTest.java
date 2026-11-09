package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StatusStripRideTest {

    @Test
    public void theBarsOwnStripGoesWhereTheBarGoes() {
        // Home 2 -> Workspace: the bar rides in from the right, the strip with it.
        assertEquals(1080f, StatusStripRide.stripX(1080f, true, true), 0f);
        assertEquals(540f, StatusStripRide.stripX(540f, true, true), 0f);
        // Workspace -> Home: it leaves to the right with the bar.
        assertEquals(-300f, StatusStripRide.stripX(-300f, true, true), 0f);
        assertEquals(0f, StatusStripRide.stripX(0f, true, true), 0f);
    }

    @Test
    public void aStripThatIsNotTheBarsStays() {
        assertEquals("a sheet's strip", 0f, StatusStripRide.stripX(700f, true, false), 0f);
        assertEquals("no strip", 0f, StatusStripRide.stripX(700f, false, true), 0f);
        assertEquals(0f, StatusStripRide.stripX(Float.NaN, true, true), 0f);
    }
}
