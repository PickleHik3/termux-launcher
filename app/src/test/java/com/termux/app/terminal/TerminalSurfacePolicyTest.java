package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    public void withoutSlabsTheTintIsFoldedIntoTheDim() {
        int ground = TerminalSurfacePolicy.wallGround(false, 0xFF000000, 0x33000000);
        assertEquals(0xFF000000, ground);
        assertEquals(0x80000000, TerminalSurfacePolicy.wallGround(false, 0x80000000, 0));
        assertEquals(0, TerminalSurfacePolicy.wallGround(false, 0, 0));
    }
}
