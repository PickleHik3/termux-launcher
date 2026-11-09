package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The Look's glass Home's widgets wear: Base's numbers, clamped as the surfaces clamp them, the
 * corner sentinel resolved where it always is, and a blur frame asked for only when one is drawn.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LookGlassSpecTest {

    @Test
    public void baseNumbersPassThroughClampedAsTheSurfacesClampThem() {
        LookGlassSpec spec = LookGlassSpec.of(true, 12, 34, 18, 40, 24);
        assertEquals(12, spec.blurDp);
        assertEquals(34, spec.opacityPercent);
        assertEquals(18, spec.grainPercent);
        assertEquals(40, spec.tintPercent);
        assertEquals(24, spec.cornerRadiusDp);

        LookGlassSpec wild = LookGlassSpec.of(true, 90, 140, -5, 300, 64);
        assertEquals("the blur ceiling every slab shares", LookGlassSpec.MAX_BLUR_DP, wild.blurDp);
        assertEquals(100, wild.opacityPercent);
        assertEquals(0, wild.grainPercent);
        assertEquals(100, wild.tintPercent);
        assertEquals("Corners are the Look's own, held to the card at draw time", 64,
            wild.cornerRadiusDp);
    }

    @Test
    public void theFollowTheStyleSentinelIsTheRoundedSurfaceToken() {
        int auto = TermuxAppSharedPreferences.resolveAutoCornerRadiusDp(null, true);
        assertEquals(auto, LookGlassSpec.cornerRadiusDp(-1));
        assertEquals(auto, LookGlassSpec.of(true, 8, 34, 18, 0, -1).cornerRadiusDp);
        assertEquals("a widget is a card, never the Docked straight edge",
            TERMUX_APP.DEFAULT_ROUNDED_SURFACE_CORNER_RADIUS_DP, auto);
        assertEquals("square corners are a real answer", 0, LookGlassSpec.cornerRadiusDp(0));
    }

    @Test
    public void noStoreToReadIsTheShippedBase() {
        LookGlassSpec spec = LookGlassSpec.read(null);
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR, spec.blurDp);
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_OPACITY, spec.opacityPercent);
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_GRAIN, spec.grainPercent);
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_TINT, spec.tintPercent);
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS, spec.cornerRadiusDp);
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_BASE_CORNER_RADIUS,
            LookGlassSpec.cornerRadiusDp((TermuxAppSharedPreferences) null));
    }

    @Test
    public void theSlabIsWornOnlyOnTheWallpaperAndBlursOnlyWithABlurToDraw() {
        assertTrue(LookGlassSpec.of(true, 8, 34, 18, 0, 24).slabShown());
        assertFalse("off the wallpaper there is no glass", LookGlassSpec.of(false, 8, 34, 18, 0, 24)
            .slabShown());
        assertTrue("tint and grain alone are still the Look's glass",
            LookGlassSpec.of(true, 0, 34, 18, 0, 24).slabShown());

        assertTrue(LookGlassSpec.of(true, 8, 34, 18, 0, 24).wantsBlurFrame());
        assertFalse("no blur: no frame held", LookGlassSpec.of(true, 0, 34, 18, 0, 24)
            .wantsBlurFrame());
        assertFalse(LookGlassSpec.of(false, 8, 34, 18, 0, 24).wantsBlurFrame());
    }

    @Test
    public void theSurfaceColourIsTheBaseHueAtTheLooksOpacity() {
        assertEquals(0x00123456, LookGlassSpec.of(true, 8, 0, 0, 0, 24).surfaceColor(0xFF123456));
        assertEquals(0xFF123456, LookGlassSpec.of(true, 8, 100, 0, 0, 24).surfaceColor(0x00123456));
        assertEquals(0x80123456, LookGlassSpec.of(true, 8, 50, 0, 0, 24).surfaceColor(0xFF123456));
    }

    @Test
    public void specsCompareByValueSoTheChromePassSeesAMove() {
        assertEquals(LookGlassSpec.of(true, 8, 34, 18, 0, 24), LookGlassSpec.of(true, 8, 34, 18, 0, 24));
        assertEquals(LookGlassSpec.of(true, 8, 34, 18, 0, 24).hashCode(),
            LookGlassSpec.of(true, 8, 34, 18, 0, 24).hashCode());
        assertNotEquals(LookGlassSpec.of(true, 8, 34, 18, 0, 24),
            LookGlassSpec.of(true, 8, 34, 18, 0, 20));
        assertNotEquals(LookGlassSpec.of(true, 8, 34, 18, 0, 24),
            LookGlassSpec.of(false, 8, 34, 18, 0, 24));
    }
}
