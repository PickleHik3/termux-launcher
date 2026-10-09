package com.termux.app.chrome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;

/** When the Fancier Glass switch is shown, when it can be flipped, and when it does anything. */
public class FancierGlassPolicyTest {

    private static final int API_33 = Build.VERSION_CODES.TIRAMISU;
    private static final int API_32 = Build.VERSION_CODES.S_V2;

    @Test
    public void theSwitchIsHiddenBelowAndroid13() {
        assertFalse(FancierGlassPolicy.offered(API_32));
        assertFalse(FancierGlassPolicy.offered(Build.VERSION_CODES.O));
        assertTrue(FancierGlassPolicy.offered(API_33));
        assertTrue(FancierGlassPolicy.offered(API_33 + 3));
    }

    @Test
    public void theSwitchNeedsAWallpaperSetFromInsideTheLauncher() {
        assertTrue(FancierGlassPolicy.flippable(API_33, true));
        assertFalse("any other wallpaper leaves it disabled with the hint",
            FancierGlassPolicy.flippable(API_33, false));
        assertFalse("a managed wallpaper does not bring it back below Android 13",
            FancierGlassPolicy.flippable(API_32, true));
    }

    @Test
    public void everySurfaceRefractsOnlyWhenAllThreeHold() {
        assertTrue(FancierGlassPolicy.active(API_33, true, true));
        assertFalse("the switch off is the default look", FancierGlassPolicy.active(API_33, false, true));
        assertFalse("the switch stays on but does nothing over a system wallpaper",
            FancierGlassPolicy.active(API_33, true, false));
        assertFalse(FancierGlassPolicy.active(API_32, true, true));
        assertFalse(FancierGlassPolicy.active(API_32, false, false));
    }
}
