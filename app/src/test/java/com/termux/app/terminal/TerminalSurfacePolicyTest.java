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
}
