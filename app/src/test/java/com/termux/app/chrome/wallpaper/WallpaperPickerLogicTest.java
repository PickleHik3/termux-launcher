package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallpaperPickerLogicTest {

    @Test
    public void storedTileKeepsStoredMode() {
        assertEquals("own", WallpaperPickerLogic.initialMode("aurora", "aurora", "own"));
        assertEquals("material", WallpaperPickerLogic.initialMode("aurora", "aurora", "material"));
    }

    @Test
    public void otherTilesStartOnMaterial() {
        assertEquals("material", WallpaperPickerLogic.initialMode("tide", "aurora", "own"));
        assertEquals("material", WallpaperPickerLogic.initialMode("tide", null, null));
        assertEquals("material", WallpaperPickerLogic.initialMode("aurora", "aurora", "junk"));
    }

    @Test
    public void storedMarking() {
        assertTrue(WallpaperPickerLogic.isStored("rain", "rain"));
        assertFalse(WallpaperPickerLogic.isStored("rain", null));
    }

    @Test
    public void thumbWidthFollowsPortraitAspect() {
        assertEquals(90, WallpaperPickerLogic.thumbWidth(200, 1080, 2400));
        assertEquals(90, WallpaperPickerLogic.thumbWidth(200, 2400, 1080));
    }
}
