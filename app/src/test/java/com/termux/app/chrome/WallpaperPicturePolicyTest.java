package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The truth table behind issue #37: which wallpaper situations blur, hint, or go to plain tint. */
public class WallpaperPicturePolicyTest {

    @Test
    public void aStillWithNoServiceMatchesTheScreen() {
        assertEquals(WallpaperPicture.MATCHES_SCREEN, WallpaperPicturePolicy.resolve(false, false, true));
        assertEquals(WallpaperPicture.MATCHES_SCREEN, WallpaperPicturePolicy.resolve(false, false, false));
    }

    @Test
    public void aServiceShowingThePictureTheLauncherSetMatchesTheScreen() {
        assertEquals(WallpaperPicture.MATCHES_SCREEN, WallpaperPicturePolicy.resolve(true, true, true));
    }

    @Test
    public void aServiceWithSomeoneElsesStillIsBestEffort() {
        assertEquals(WallpaperPicture.BEST_EFFORT, WallpaperPicturePolicy.resolve(true, false, true));
    }

    @Test
    public void aServiceWithNoStillHasNothingWorthBlurring() {
        assertEquals(WallpaperPicture.NO_STILL, WallpaperPicturePolicy.resolve(true, false, false));
    }

    @Test
    public void onlyTheMatchingPictureIsDrawnByTheLauncher() {
        assertTrue(WallpaperPicture.MATCHES_SCREEN.allowsSelfDrawnBackdrop());
        assertFalse(WallpaperPicture.BEST_EFFORT.allowsSelfDrawnBackdrop());
        assertFalse(WallpaperPicture.NO_STILL.allowsSelfDrawnBackdrop());
    }

    @Test
    public void everythingButNoStillBlursAndEverythingButMatchingHints() {
        assertTrue(WallpaperPicture.MATCHES_SCREEN.blurs());
        assertTrue(WallpaperPicture.BEST_EFFORT.blurs());
        assertFalse(WallpaperPicture.NO_STILL.blurs());
        assertFalse(WallpaperPicture.MATCHES_SCREEN.showsHint());
        assertTrue(WallpaperPicture.BEST_EFFORT.showsHint());
        assertTrue(WallpaperPicture.NO_STILL.showsHint());
    }
}
