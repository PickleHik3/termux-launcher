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
            WallpaperBackdropPolicy.mode(true, WallpaperPicture.MATCHES_SCREEN, true));
    }

    @Test
    public void aPictureWeAreOnlyGuessingAtStaysWithTheSystem() {
        // Best-effort still blurs on every glass, but painting it behind everything would put a
        // picture on screen that the user is not looking at.
        assertEquals(WallpaperBackdropPolicy.Mode.PASSTHROUGH,
            WallpaperBackdropPolicy.mode(true, WallpaperPicture.BEST_EFFORT, true));
        assertEquals(WallpaperBackdropPolicy.Mode.PASSTHROUGH,
            WallpaperBackdropPolicy.mode(true, WallpaperPicture.NO_STILL, true));
    }

    @Test
    public void aWallpaperWeCannotReadStaysWithTheSystem() {
        assertEquals(WallpaperBackdropPolicy.Mode.PASSTHROUGH,
            WallpaperBackdropPolicy.mode(true, WallpaperPicture.MATCHES_SCREEN, false));
    }

    @Test
    public void theFeatureBeingOffLeavesEverythingAsItWas() {
        assertEquals(WallpaperBackdropPolicy.Mode.PASSTHROUGH,
            WallpaperBackdropPolicy.mode(false, WallpaperPicture.MATCHES_SCREEN, true));
        assertEquals(WallpaperBackdropPolicy.Mode.PASSTHROUGH,
            WallpaperBackdropPolicy.mode(false, WallpaperPicture.BEST_EFFORT, false));
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
