package com.termux.app.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.termux.app.theme.WallpaperColorSource.Kind;

import org.junit.Test;

/** Which colours the launcher wears: Android's wallpaper's, or its app-only picture's seed. */
public class WallpaperColorSourceTest {

    private static final int RED_SEED = 0xFFD32F2F;
    private static final long STAMP = WallpaperColorSource.pictureStamp(1_700_000_000_000L, 524_288L);

    @Test
    public void appOnlyPictureWithItsSeedWearsTheSeed() {
        assertEquals(Kind.APP_ONLY, WallpaperColorSource.choose(true, false, true, STAMP, STAMP, RED_SEED));
    }

    @Test
    public void noAppOnlyPictureKeepsTheSystemColours() {
        // A stored seed outlives the mark (Also set as phone wallpaper, a Home set) and means nothing.
        assertEquals(Kind.SYSTEM, WallpaperColorSource.choose(true, false, false, STAMP, STAMP, RED_SEED));
        assertEquals(Kind.SYSTEM, WallpaperColorSource.choose(true, false, false, 0L, 0L, WallpaperColorSource.NO_SEED));
    }

    @Test
    public void belowDynamicColourNothingFollowsAWallpaper() {
        assertEquals(Kind.SYSTEM, WallpaperColorSource.choose(false, false, true, STAMP, STAMP, RED_SEED));
    }

    @Test
    public void theTerminalSchemeOwnsTheChromeWhateverThePicture() {
        assertEquals(Kind.SYSTEM, WallpaperColorSource.choose(true, true, true, STAMP, STAMP, RED_SEED));
    }

    @Test
    public void aSeedForAnotherPictureIsTakenAgain() {
        long replaced = WallpaperColorSource.pictureStamp(1_700_000_100_000L, 600_000L);
        assertEquals(Kind.NEEDS_SEED, WallpaperColorSource.choose(true, false, true, replaced, STAMP, RED_SEED));
    }

    @Test
    public void aPictureSetBeforeSeedsWereKeptNeedsOne() {
        assertEquals(Kind.NEEDS_SEED, WallpaperColorSource.choose(true, false, true, STAMP, 0L, WallpaperColorSource.NO_SEED));
    }

    @Test
    public void aPictureNoSeedCameFromKeepsTheSystemColoursWithoutRetrying() {
        assertEquals(Kind.SYSTEM, WallpaperColorSource.choose(true, false, true, STAMP, STAMP, WallpaperColorSource.NO_SEED));
    }

    @Test
    public void anUnreadableStampNeverMatchesTheEmptyStoredOne() {
        assertEquals(Kind.NEEDS_SEED, WallpaperColorSource.choose(true, false, true, 0L, 0L, RED_SEED));
    }

    @Test
    public void stampsTellPicturesApartAndNeverReadAsNone() {
        assertEquals(0L, WallpaperColorSource.pictureStamp(0L, 0L));
        assertNotEquals(0L, WallpaperColorSource.pictureStamp(0L, 1L));
        assertNotEquals(STAMP, WallpaperColorSource.pictureStamp(1_700_000_000_000L, 524_289L));
        assertNotEquals(STAMP, WallpaperColorSource.pictureStamp(1_700_000_000_001L, 524_288L));
        // lastModified * 31 + length folding to 0 is still a picture.
        assertNotEquals(0L, WallpaperColorSource.pictureStamp(-1L, 31L));
    }
}
