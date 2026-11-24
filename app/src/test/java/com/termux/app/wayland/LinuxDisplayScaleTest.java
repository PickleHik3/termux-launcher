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
    public void aFreshInstallFollowsTheScreensDensity() {
        // Nothing stored, or only the "scaled" mode X11Defaults and the settings page write.
        assertEquals(2.5f, LinuxDisplayScale.outputScale(null, null, 2.625f), 0f);
        assertEquals(2.5f, LinuxDisplayScale.outputScale("scaled", null, 2.625f), 0f);
        assertEquals(3.0f, LinuxDisplayScale.outputScale(null, null, 3.0f), 0f);
    }

    @Test
    public void aStopTheUserChoseWinsOverTheDensity() {
        // The rail at 100 writes "native"; at any other stop it stores the percent.
        assertEquals(1.0f, LinuxDisplayScale.outputScale("native", null, 2.625f), 0f);
        assertEquals(1.0f, LinuxDisplayScale.outputScale("native", 150, 2.625f), 0f);
        assertEquals(1.5f, LinuxDisplayScale.outputScale("scaled", 150, 2.625f), 0f);
        // The settings slider can store 100 in the scaled mode: still the user's 100.
        assertEquals(1.0f, LinuxDisplayScale.outputScale("scaled", 100, 2.625f), 0f);
        assertEquals(1.0f, LinuxDisplayScale.outputScale("exact", null, 2.625f), 0f);
        // A percent with no mode reads as X11 Display reads it: the store's default, native.
        assertEquals(1.0f, LinuxDisplayScale.outputScale(null, 200, 2.625f), 0f);
    }

    @Test
    public void theDensityScaleIsTheNearestQuarterTiesDown() {
        assertEquals(1.0f, LinuxDisplayScale.densityScale(1.0f), 0f);
        assertEquals(1.5f, LinuxDisplayScale.densityScale(1.5f), 0f);
        assertEquals(2.0f, LinuxDisplayScale.densityScale(2.0f), 0f);
        assertEquals(2.5f, LinuxDisplayScale.densityScale(2.625f), 0f); // 420 dpi
        assertEquals(2.75f, LinuxDisplayScale.densityScale(2.75f), 0f); // 440 dpi
        assertEquals(2.75f, LinuxDisplayScale.densityScale(2.8f), 0f);
        assertEquals(3.5f, LinuxDisplayScale.densityScale(3.5f), 0f); // 560 dpi
    }

    @Test
    public void theDensityScaleStaysInItsBounds() {
        assertEquals(1.0f, LinuxDisplayScale.densityScale(0.75f), 0f);
        assertEquals(4.0f, LinuxDisplayScale.densityScale(5.0f), 0f);
        assertEquals(1.0f, LinuxDisplayScale.densityScale(0f), 0f);
        assertEquals(1.0f, LinuxDisplayScale.densityScale(Float.NaN), 0f);
        assertEquals(1.0f, LinuxDisplayScale.densityScale(Float.POSITIVE_INFINITY), 0f);
    }
}
