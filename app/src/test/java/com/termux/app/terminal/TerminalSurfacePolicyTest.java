package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.ChromeInk.Polarity;

import org.junit.Test;

public class TerminalSurfacePolicyTest {

    @Test
    public void opaqueModeAlwaysPaintsTheTerminalSurface() {
        assertTrue(TerminalSurfacePolicy.showsTerminalSurface(false, 100));
        assertTrue(TerminalSurfacePolicy.showsTerminalSurface(false, 0));
    }

    @Test
    public void wallpaperModePaintsTheTintUnlessTheSliderIsAtZero() {
        assertTrue(TerminalSurfacePolicy.showsTerminalSurface(true, 1));
        assertTrue(TerminalSurfacePolicy.showsTerminalSurface(true, 60));
        assertFalse(TerminalSurfacePolicy.showsTerminalSurface(true, 0));
    }

    /**
     * Issue #27: turning fullscreen on made the terminal lose its darkness in wallpaper mode. The
     * rule takes no fullscreen input at all, so there is nothing left for fullscreen to switch off.
     */
    @Test
    public void theRuleHasNoFullscreenInput() throws NoSuchMethodException {
        Class<?>[] params = TerminalSurfacePolicy.class
            .getMethod("showsTerminalSurface", boolean.class, int.class).getParameterTypes();
        assertEquals(2, params.length);
    }

    /**
     * Floating -> Done -> Docked left the root's dim different from a fresh Docked start (device
     * measurement, 2026-10-01). The ground is a function of the glass switch and the two colours
     * alone, so the round trip is the same call with the same arguments.
     */
    @Test
    public void aFloatingToDockedRoundTripLeavesTheSameRootDimAsAFreshDockedStart() {
        int tint = 0x99101820;
        int dim = 0x33000000;
        int fresh = TerminalSurfacePolicy.wallGround(true, tint, dim);
        TerminalSurfacePolicy.wallGround(true, tint, dim);  // Floating's pass in between
        assertEquals(fresh, TerminalSurfacePolicy.wallGround(true, tint, dim));
        assertEquals("with slabs the root carries only the wallpaper dim", dim, fresh);
    }

    @Test
    public void homeNeverWearsTheTerminalTint() {
        int tint = 0x99101820;
        int dim = 0x33000000;
        assertEquals(dim, TerminalSurfacePolicy.pageGround(false, false, tint, dim));
        assertEquals(dim, TerminalSurfacePolicy.pageGround(false, true, tint, dim));
    }

    @Test
    public void theTerminalPageFoldsTheTintOnlyWithoutGlass() {
        int tint = 0x80000000;
        int dim = 0x33000000;
        assertEquals(TerminalSurfacePolicy.wallGround(false, tint, dim),
            TerminalSurfacePolicy.pageGround(true, false, tint, dim));
        assertEquals(dim, TerminalSurfacePolicy.pageGround(true, true, tint, dim));
        assertTrue((TerminalSurfacePolicy.pageGround(true, false, tint, dim) >>> 24) > (dim >>> 24));
    }

    @Test
    public void withoutSlabsTheTintIsFoldedIntoTheDim() {
        int ground = TerminalSurfacePolicy.wallGround(false, 0xFF000000, 0x33000000);
        assertEquals(0xFF000000, ground);
        assertEquals(0x80000000, TerminalSurfacePolicy.wallGround(false, 0x80000000, 0));
        assertEquals(0, TerminalSurfacePolicy.wallGround(false, 0, 0));
    }

    /**
     * A light theme over a dark wallpaper behind clear glass kept its dark text (pong, 2026-10-22).
     * Thin glass leans to pale ink whatever the terminal wore and whatever the theme asks for.
     */
    @Test
    public void thinGlassLeansToPaleInkWhateverWasHeld() {
        int thin = TerminalSurfacePolicy.PALE_INK_BELOW_OPACITY - 1;
        assertEquals(Polarity.PALE_INK, TerminalSurfacePolicy.inkLeaning(true, thin, null));
        assertEquals(Polarity.PALE_INK,
            TerminalSurfacePolicy.inkLeaning(true, thin, Polarity.DARK_INK));
        assertEquals(Polarity.PALE_INK,
            TerminalSurfacePolicy.inkLeaning(true, 0, Polarity.PALE_INK));
    }

    /** Opaque light glass keeps dark text: from the line up the held ink, or the theme's, stays. */
    @Test
    public void thickGlassKeepsTheHeldInk() {
        int line = TerminalSurfacePolicy.PALE_INK_BELOW_OPACITY;
        assertEquals(Polarity.DARK_INK,
            TerminalSurfacePolicy.inkLeaning(true, line, Polarity.DARK_INK));
        assertEquals(Polarity.DARK_INK,
            TerminalSurfacePolicy.inkLeaning(true, 100, Polarity.DARK_INK));
        assertEquals(Polarity.PALE_INK,
            TerminalSurfacePolicy.inkLeaning(true, 100, Polarity.PALE_INK));
        assertNull(TerminalSurfacePolicy.inkLeaning(true, line, null));
    }

    /** Off the wallpaper the text stands on the palette's own background; opacity has no say. */
    @Test
    public void anOpaqueTerminalKeepsTheHeldInk() {
        assertEquals(Polarity.DARK_INK,
            TerminalSurfacePolicy.inkLeaning(false, 0, Polarity.DARK_INK));
        assertNull(TerminalSurfacePolicy.inkLeaning(false, 10, null));
    }

    /**
     * Where the line comes from: white glass over a black wallpaper first gives black text 4.5:1
     * at 46%, and a Material surface is a little darker than white, so the line cannot sit lower.
     */
    @Test
    public void theLineIsWhereDarkTextFirstReadsOnLightGlassOverBlack() {
        assertTrue(blackTextContrastOnWhiteGlassOverBlack(46) >= 4.5d);
        assertTrue(blackTextContrastOnWhiteGlassOverBlack(45) < 4.5d);
        assertTrue(TerminalSurfacePolicy.PALE_INK_BELOW_OPACITY >= 46);
    }

    /** WCAG contrast of black on white glass at {@code opacity} percent over black, sRGB blend. */
    private static double blackTextContrastOnWhiteGlassOverBlack(int opacity) {
        double channel = opacity / 100d;
        double linear = channel <= 0.04045 ? channel / 12.92
            : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
        return (linear + 0.05d) / 0.05d;
    }
}
