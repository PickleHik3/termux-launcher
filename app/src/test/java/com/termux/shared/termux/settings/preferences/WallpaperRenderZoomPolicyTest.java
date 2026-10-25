package com.termux.shared.termux.settings.preferences;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The wallpaper copy's composite zoom per ROM, decided without a store or a device. */
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
}
