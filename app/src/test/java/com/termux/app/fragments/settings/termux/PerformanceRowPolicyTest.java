package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.fragments.settings.termux.PerformanceRowPolicy.Facts;
import com.termux.app.fragments.settings.termux.PerformanceRowPolicy.Reason;

import org.junit.Test;

public class PerformanceRowPolicyTest {

    private static final int T = 33;
    private static final int S = 32;

    /** Android 13, a picked wallpaper, refraction on, wallpaper mode on, no preset: all rows free. */
    private static Facts free() {
        return new Facts(T, false, false, true, true, true);
    }

    @Test
    public void everyRowIsFreeWhenNothingStandsInTheWay() {
        Facts facts = free();
        assertEquals(Reason.NONE, PerformanceRowPolicy.glassRefraction(facts));
        assertEquals(Reason.NONE, PerformanceRowPolicy.livingGlass(facts));
        assertEquals(Reason.NONE, PerformanceRowPolicy.wallpaperParallax(facts));
        assertEquals(Reason.NONE, PerformanceRowPolicy.cursorTrail(facts));
        assertEquals(Reason.NONE, PerformanceRowPolicy.terminalEffect(facts));
        assertTrue(Reason.NONE.enabled());
        assertFalse(Reason.NONE.shownOff());
    }

    @Test
    public void theLowMemoryPresetOwnsWhatItWrote() {
        // The preset turns idle reduction on as well, which the glass rows answer to.
        Facts facts = new Facts(T, true, true, true, true, true);
        assertEquals(Reason.SET_BY_LOW_MEMORY_PRESET, PerformanceRowPolicy.wallpaperParallax(facts));
        assertEquals(Reason.SET_BY_LOW_MEMORY_PRESET, PerformanceRowPolicy.cursorTrail(facts));
        assertEquals(Reason.SET_BY_LOW_MEMORY_PRESET, PerformanceRowPolicy.terminalEffect(facts));
        assertEquals(Reason.OFF_WHILE_IDLE_REDUCED, PerformanceRowPolicy.glassRefraction(facts));
        assertEquals(Reason.OFF_WHILE_IDLE_REDUCED, PerformanceRowPolicy.livingGlass(facts));
        assertFalse(Reason.SET_BY_LOW_MEMORY_PRESET.enabled());
        assertFalse("the preset's value is the stored one", Reason.SET_BY_LOW_MEMORY_PRESET.shownOff());
    }

    @Test
    public void theLowMemoryPresetComesBeforeAMissingWallpaper() {
        Facts facts = new Facts(T, true, true, false, true, false);
        assertEquals(Reason.SET_BY_LOW_MEMORY_PRESET, PerformanceRowPolicy.wallpaperParallax(facts));
    }

    @Test
    public void idleReductionAloneGreysOnlyTheGlass() {
        Facts facts = new Facts(T, true, false, true, true, true);
        assertEquals(Reason.OFF_WHILE_IDLE_REDUCED, PerformanceRowPolicy.glassRefraction(facts));
        assertEquals(Reason.OFF_WHILE_IDLE_REDUCED, PerformanceRowPolicy.livingGlass(facts));
        assertEquals(Reason.NONE, PerformanceRowPolicy.wallpaperParallax(facts));
        assertEquals(Reason.NONE, PerformanceRowPolicy.cursorTrail(facts));
        assertEquals(Reason.NONE, PerformanceRowPolicy.terminalEffect(facts));
        assertTrue(Reason.OFF_WHILE_IDLE_REDUCED.shownOff());
        assertFalse(Reason.OFF_WHILE_IDLE_REDUCED.enabled());
    }

    @Test
    public void idleReductionComesBeforeAnOldAndroid() {
        Facts facts = new Facts(S, true, false, false, true, true);
        assertEquals(Reason.OFF_WHILE_IDLE_REDUCED, PerformanceRowPolicy.glassRefraction(facts));
        assertEquals(Reason.OFF_WHILE_IDLE_REDUCED, PerformanceRowPolicy.livingGlass(facts));
    }

    @Test
    public void belowAndroid13TheGlassNeedsAndroid13BeforeAWallpaper() {
        Facts facts = new Facts(S, false, false, false, false, true);
        assertEquals(Reason.NEEDS_ANDROID_13, PerformanceRowPolicy.glassRefraction(facts));
        assertEquals(Reason.NEEDS_ANDROID_13, PerformanceRowPolicy.livingGlass(facts));
        assertFalse(Reason.NEEDS_ANDROID_13.shownOff());
    }

    @Test
    public void withoutAPickedWallpaperTheGlassAsksForOne() {
        Facts facts = new Facts(T, false, false, false, false, true);
        assertEquals(Reason.NEEDS_PICKED_WALLPAPER, PerformanceRowPolicy.glassRefraction(facts));
        assertEquals("the wallpaper is named before refraction",
            Reason.NEEDS_PICKED_WALLPAPER, PerformanceRowPolicy.livingGlass(facts));
    }

    @Test
    public void livingGlassNeedsRefractionOn() {
        Facts facts = new Facts(T, false, false, true, false, true);
        assertEquals(Reason.NONE, PerformanceRowPolicy.glassRefraction(facts));
        assertEquals(Reason.NEEDS_GLASS_REFRACTION, PerformanceRowPolicy.livingGlass(facts));
    }

    @Test
    public void parallaxNeedsWallpaperMode() {
        Facts facts = new Facts(T, false, false, true, true, false);
        assertEquals(Reason.NEEDS_WALLPAPER, PerformanceRowPolicy.wallpaperParallax(facts));
        assertEquals("the glass reads the picked wallpaper, not the mode",
            Reason.NONE, PerformanceRowPolicy.glassRefraction(facts));
    }
}
