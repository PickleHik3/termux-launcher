package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;

import com.termux.app.x11.DisplayScaleSteps;
import com.termux.app.x11.X11AppSettings;

import org.junit.Test;

public class LinuxDisplayScaleTest {

    /** By default a window follows the screen: 2.5 on a 420 dpi phone, whatever X11 Display holds. */
    @Test
    public void aWindowWithNoScaleOfItsOwnFollowsTheScreen() {
        assertEquals(2.5f, LinuxDisplayScale.windowScale(null, 2.625f), 0f);
        assertEquals(3.0f, LinuxDisplayScale.windowScale(null, 3.0f), 0f);
        assertEquals(1.0f, LinuxDisplayScale.windowScale(null, 1.0f), 0f);
    }

    @Test
    public void anAppsOwnScaleWins() {
        assertEquals(1.0f, LinuxDisplayScale.windowScale("1", 2.625f), 0f);
        assertEquals(1.25f, LinuxDisplayScale.windowScale("1.25", 2.625f), 0f);
        assertEquals(1.75f, LinuxDisplayScale.windowScale("1.75", 2.625f), 0f);
        assertEquals(2.5f, LinuxDisplayScale.windowScale("2.5", 1.0f), 0f);
    }

    @Test
    public void anUnknownScaleFollowsTheScreen() {
        assertEquals(2.5f, LinuxDisplayScale.windowScale("3", 2.625f), 0f);
        assertEquals(2.5f, LinuxDisplayScale.windowScale("bogus", 2.625f), 0f);
        assertEquals(2.5f, LinuxDisplayScale.windowScale("", 2.625f), 0f);
    }

    /** A stop chosen on the rail, stored as the app's Scale, reads back as that stop's scale. */
    @Test
    public void everyStopRoundTripsThroughTheStoredValue() {
        for (int step : DisplayScaleSteps.STEPS) {
            String stored = DisplayScaleSteps.scaleValue(step);
            assertEquals(true, X11AppSettings.isScale(stored));
            float scale = LinuxDisplayScale.windowScale(stored, 2.625f);
            assertEquals(step / 100f, scale, 0f);
            assertEquals(step, LinuxDisplayScale.railStep(scale));
        }
    }

    @Test
    public void theRailShowsTheNearestStopForADensityBetweenStops() {
        assertEquals(250, LinuxDisplayScale.railStep(2.5f));
        assertEquals(250, LinuxDisplayScale.railStep(3.0f));
        assertEquals(250, LinuxDisplayScale.railStep(2.75f));
        assertEquals(200, LinuxDisplayScale.railStep(2.25f));
        assertEquals(100, LinuxDisplayScale.railStep(0f));
        assertEquals(100, LinuxDisplayScale.railStep(Float.NaN));
    }

    @Test
    public void densityScaleSnapsToQuartersTiesDown() {
        assertEquals(1.0f, LinuxDisplayScale.densityScale(1.0f), 0f);
        assertEquals(1.5f, LinuxDisplayScale.densityScale(1.5f), 0f);
        assertEquals(2.0f, LinuxDisplayScale.densityScale(2.0f), 0f);
        assertEquals(2.5f, LinuxDisplayScale.densityScale(2.625f), 0f); // 420 dpi
        assertEquals(2.75f, LinuxDisplayScale.densityScale(2.75f), 0f); // 440 dpi
        assertEquals(2.75f, LinuxDisplayScale.densityScale(2.8f), 0f);
        assertEquals(3.5f, LinuxDisplayScale.densityScale(3.5f), 0f); // 560 dpi
    }

    @Test
    public void densityScaleStaysInBounds() {
        assertEquals(1.0f, LinuxDisplayScale.densityScale(0.75f), 0f);
        assertEquals(4.0f, LinuxDisplayScale.densityScale(5.0f), 0f);
        assertEquals(1.0f, LinuxDisplayScale.densityScale(0f), 0f);
        assertEquals(1.0f, LinuxDisplayScale.densityScale(Float.NaN), 0f);
        assertEquals(1.0f, LinuxDisplayScale.densityScale(Float.POSITIVE_INFINITY), 0f);
    }
}
