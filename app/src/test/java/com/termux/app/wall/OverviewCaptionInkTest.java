package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Edit pages' captions take dark ink on a light wallpaper and white on a dark one. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class OverviewCaptionInkTest {

    private static final int CREAM = 0xFFF3EAD3;
    private static final int NIGHT = 0xFF14161C;

    @Test
    public void aCreamWallpaperGetsDarkInk() {
        assertEquals(OverviewCaptionInk.DARK_INK, OverviewCaptionInk.ink(CREAM));
    }

    @Test
    public void aDarkWallpaperGetsWhiteInk() {
        assertEquals(OverviewCaptionInk.PALE_INK, OverviewCaptionInk.ink(NIGHT));
    }

    @Test
    public void anUnknownWallpaperKeepsWhiteOnItsDarkShadow() {
        assertEquals(OverviewCaptionInk.PALE_INK, OverviewCaptionInk.ink(0));
        assertEquals(OverviewCaptionInk.UNKNOWN_SHADOW, OverviewCaptionInk.shadow(0));
    }

    @Test
    public void theShadowStandsOnTheFarSideOfTheInk() {
        // Dark writing gets a pale glow, not the dark shadow white writing wears.
        assertNotEquals(OverviewCaptionInk.UNKNOWN_SHADOW, OverviewCaptionInk.shadow(CREAM));
        int glow = OverviewCaptionInk.shadow(CREAM) | 0xFF000000;
        int r = (glow >> 16) & 0xFF;
        assertEquals(true, r > 0x80);
    }

    @Test
    public void aCaptionIsSampledAgainOnlyInAnotherCell() {
        assertEquals(OverviewCaptionInk.cell(10f, 64f), OverviewCaptionInk.cell(60f, 64f));
        assertNotEquals(OverviewCaptionInk.cell(10f, 64f), OverviewCaptionInk.cell(70f, 64f));
        assertEquals(-1, OverviewCaptionInk.cell(-5f, 64f));
    }
}
