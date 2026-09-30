package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallpaperDeviceReadingsTest {

    @Test
    public void thermalBands() {
        assertEquals(WallpaperDirector.Thermal.NONE, WallpaperDeviceReadings.thermal(0));
        assertEquals(WallpaperDirector.Thermal.NONE, WallpaperDeviceReadings.thermal(-1));
        assertEquals(WallpaperDirector.Thermal.LIGHT, WallpaperDeviceReadings.thermal(1));
        assertEquals(WallpaperDirector.Thermal.MODERATE_OR_WORSE, WallpaperDeviceReadings.thermal(2));
        assertEquals(WallpaperDirector.Thermal.MODERATE_OR_WORSE, WallpaperDeviceReadings.thermal(6));
    }

    @Test
    public void percentNeedsALevelAndAScale() {
        assertEquals(50, WallpaperDeviceReadings.percent(50, 100));
        assertEquals(15, WallpaperDeviceReadings.percent(30, 200));
        assertEquals(-1, WallpaperDeviceReadings.percent(-1, 100));
        assertEquals(-1, WallpaperDeviceReadings.percent(50, 0));
    }

    @Test
    public void lowOnlyCountsOffTheCharger() {
        assertTrue(WallpaperDeviceReadings.lowWhileDischarging(15, 0, false));
        assertFalse(WallpaperDeviceReadings.lowWhileDischarging(16, 0, false));
        assertTrue(WallpaperDeviceReadings.lowWhileDischarging(40, 0, true));
        assertFalse(WallpaperDeviceReadings.lowWhileDischarging(5, 1, true));
        assertFalse(WallpaperDeviceReadings.lowWhileDischarging(-1, 0, false));
    }
}
