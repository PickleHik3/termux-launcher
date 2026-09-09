package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Who draws the wallpaper, and what that costs the alignment slider. No device, no window. */
public class WallpaperBackdropPolicyTest {

    @Test
    public void aStillWallpaperWeCanReadIsDrawnByTheLauncher() {
        assertEquals(WallpaperBackdropPolicy.Mode.SELF_DRAWN,
            WallpaperBackdropPolicy.mode(true, false, true));
    }

    @Test
    public void aLiveWallpaperStaysWithTheSystem() {
        assertEquals(WallpaperBackdropPolicy.Mode.PASSTHROUGH,
            WallpaperBackdropPolicy.mode(true, true, true));
    }

    @Test
    public void aWallpaperWeCannotReadStaysWithTheSystem() {
        assertEquals(WallpaperBackdropPolicy.Mode.PASSTHROUGH,
            WallpaperBackdropPolicy.mode(true, false, false));
    }

    @Test
    public void theFeatureBeingOffLeavesEverythingAsItWas() {
        assertEquals(WallpaperBackdropPolicy.Mode.PASSTHROUGH,
            WallpaperBackdropPolicy.mode(false, false, true));
        assertEquals(WallpaperBackdropPolicy.Mode.PASSTHROUGH,
            WallpaperBackdropPolicy.mode(false, true, false));
    }

    @Test
    public void selfDrawnCapturesAtTrueSizeWhateverTheSliderSays() {
        assertEquals(100, WallpaperBackdropPolicy.renderZoomPercent(
            WallpaperBackdropPolicy.Mode.SELF_DRAWN, 108));
        assertEquals(100, WallpaperBackdropPolicy.renderZoomPercent(
            WallpaperBackdropPolicy.Mode.SELF_DRAWN, 90));
    }

    @Test
    public void passthroughCapturesAtTheUsersOwnNumber() {
        assertEquals(108, WallpaperBackdropPolicy.renderZoomPercent(
            WallpaperBackdropPolicy.Mode.PASSTHROUGH, 108));
        assertEquals(100, WallpaperBackdropPolicy.renderZoomPercent(
            WallpaperBackdropPolicy.Mode.PASSTHROUGH, 100));
    }

    @Test
    public void theSliderOnlyMattersWhileTheSystemDrawsTheWallpaper() {
        assertTrue(WallpaperBackdropPolicy.alignmentSliderApplies(
            WallpaperBackdropPolicy.Mode.PASSTHROUGH));
        assertFalse(WallpaperBackdropPolicy.alignmentSliderApplies(
            WallpaperBackdropPolicy.Mode.SELF_DRAWN));
    }
}
