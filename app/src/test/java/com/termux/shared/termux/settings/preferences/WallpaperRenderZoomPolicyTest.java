package com.termux.shared.termux.settings.preferences;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The wallpaper alignment's default and bounds, decided without a store or a device. */
public class WallpaperRenderZoomPolicyTest {

    @Test
    public void nothingOsStartsAtTheMeasuredCompositeZoom() {
        assertEquals(103, TermuxAppSharedPreferences.defaultWallpaperRenderZoom("Nothing"));
        // The manufacturer string's case is the ROM's business, not ours.
        assertEquals(103, TermuxAppSharedPreferences.defaultWallpaperRenderZoom("nothing"));
        assertEquals(103, TermuxAppSharedPreferences.defaultWallpaperRenderZoom("NOTHING"));
    }

    @Test
    public void everyOtherRomStartsUnzoomed() {
        assertEquals(100, TermuxAppSharedPreferences.defaultWallpaperRenderZoom("Google"));
        assertEquals(100, TermuxAppSharedPreferences.defaultWallpaperRenderZoom("samsung"));
        // "Nothing Phone" is a model, not the manufacturer this rule keys on.
        assertEquals(100, TermuxAppSharedPreferences.defaultWallpaperRenderZoom("Nothing Phone"));
        assertEquals(100, TermuxAppSharedPreferences.defaultWallpaperRenderZoom(""));
        assertEquals(100, TermuxAppSharedPreferences.defaultWallpaperRenderZoom(null));
    }

    @Test
    public void clampUsesTheSameBoundsAsItsSlider() {
        assertEquals(90, TermuxAppSharedPreferences.clampWallpaperRenderZoom(89));
        assertEquals(90, TermuxAppSharedPreferences.clampWallpaperRenderZoom(0));
        assertEquals(90, TermuxAppSharedPreferences.clampWallpaperRenderZoom(90));
        assertEquals(108, TermuxAppSharedPreferences.clampWallpaperRenderZoom(108));
        assertEquals(120, TermuxAppSharedPreferences.clampWallpaperRenderZoom(120));
        assertEquals(120, TermuxAppSharedPreferences.clampWallpaperRenderZoom(400));
    }
}
