package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The wallpaper page's hold after its Done: when the editor under it is revealed. */
public class WallpaperApplyHoldTest {

    private static final long FINISHED = 10_000L;

    @Test
    public void noColoursCallbackRevealsAfterTheQuietWindow() {
        assertEquals(WallpaperApplyHold.COLOURS_QUIET_MS,
            WallpaperApplyHold.remainingMs(FINISHED, WallpaperApplyHold.NONE, FINISHED));
        assertEquals(0L, WallpaperApplyHold.remainingMs(FINISHED, WallpaperApplyHold.NONE,
            FINISHED + WallpaperApplyHold.COLOURS_QUIET_MS));
    }

    @Test
    public void aColoursCallbackWaitsForTheRelaunchItAnnounces() {
        long colours = FINISHED + 200L;
        assertEquals(WallpaperApplyHold.RELAUNCH_WAIT_MS - 100L,
            WallpaperApplyHold.remainingMs(FINISHED, colours, colours + 100L));
        assertEquals(0L, WallpaperApplyHold.remainingMs(FINISHED, colours,
            colours + WallpaperApplyHold.RELAUNCH_WAIT_MS));
    }

    @Test
    public void coloursDuringTheApplyCountFromTheCallbackNotTheFinish() {
        // Home set first fires the colours; the Lock set then runs on before the apply finishes.
        long colours = FINISHED - 1_000L;
        assertEquals(WallpaperApplyHold.RELAUNCH_WAIT_MS - 1_000L,
            WallpaperApplyHold.remainingMs(FINISHED, colours, FINISHED));
    }

    @Test
    public void coloursLongBeforeTheFinishRevealAtOnce() {
        long colours = FINISHED - WallpaperApplyHold.RELAUNCH_WAIT_MS - 1L;
        assertEquals(0L, WallpaperApplyHold.remainingMs(FINISHED, colours, FINISHED));
    }

    @Test
    public void theHoldNeverOutlastsItsCeiling() {
        long late = FINISHED + WallpaperApplyHold.CEILING_MS - 10L;
        assertEquals(10L, WallpaperApplyHold.remainingMs(FINISHED, late, late));
        assertEquals(0L, WallpaperApplyHold.remainingMs(FINISHED, late,
            FINISHED + WallpaperApplyHold.CEILING_MS));
    }

    @Test
    public void theCeilingCoversALateCallbackWithinTheQuietWindow() {
        assertTrue(WallpaperApplyHold.CEILING_MS
            >= WallpaperApplyHold.COLOURS_QUIET_MS + WallpaperApplyHold.RELAUNCH_WAIT_MS);
    }
}
