package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LinuxDisplayScaleTest {

    @Test
    public void everyStopOfTheRailIsItsPercentAsAScale() {
        assertEquals(1.0f, LinuxDisplayScale.outputScale("scaled", 100), 0f);
        assertEquals(1.25f, LinuxDisplayScale.outputScale("scaled", 125), 0f);
        assertEquals(1.5f, LinuxDisplayScale.outputScale("scaled", 150), 0f);
        assertEquals(1.75f, LinuxDisplayScale.outputScale("scaled", 175), 0f);
        assertEquals(2.0f, LinuxDisplayScale.outputScale("scaled", 200), 0f);
        assertEquals(2.5f, LinuxDisplayScale.outputScale("scaled", 250), 0f);
    }

    @Test
    public void aPercentBetweenStopsTakesTheNearestStopAsTheRailDoes() {
        assertEquals(2.0f, LinuxDisplayScale.outputScale("scaled", 190), 0f);
        assertEquals(2.5f, LinuxDisplayScale.outputScale("scaled", 300), 0f);
        assertEquals(1.0f, LinuxDisplayScale.outputScale("scaled", 30), 0f);
    }

    @Test
    public void anyModeButScaledIsTheScreensOwnPixels() {
        assertEquals(1.0f, LinuxDisplayScale.outputScale("native", 200), 0f);
        assertEquals(1.0f, LinuxDisplayScale.outputScale("exact", 200), 0f);
    }

    @Test
    public void aFreshInstallsDefaultsAreOne() {
        // X11Defaults sets "scaled" and the store's displayScale default is 100.
        assertEquals(1.0f, LinuxDisplayScale.outputScale("scaled", 100), 0f);
        assertEquals(1.0f, LinuxDisplayScale.outputScale("native", 100), 0f);
    }
}
