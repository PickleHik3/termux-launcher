package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallpaperTapClassifierTest {

    private static WallpaperTapClassifier make() {
        return new WallpaperTapClassifier(10f, 300L);
    }

    @Test
    public void aQuickStillTapOnBareWallpaperCounts() {
        WallpaperTapClassifier c = make();
        c.down(50f, 50f, 1000L, true);
        assertTrue(c.up(52f, 51f, 1100L));
    }

    @Test
    public void notBareAtDownNeverCounts() {
        WallpaperTapClassifier c = make();
        c.down(50f, 50f, 1000L, false);
        assertFalse(c.up(50f, 50f, 1050L));
    }

    @Test
    public void travelPastTheSlopIsADragEvenIfItComesBack() {
        WallpaperTapClassifier c = make();
        c.down(50f, 50f, 1000L, true);
        c.move(80f, 50f);
        c.move(50f, 50f);
        assertFalse(c.up(50f, 50f, 1100L));
    }

    @Test
    public void aHoldIsNotATap() {
        WallpaperTapClassifier c = make();
        c.down(50f, 50f, 1000L, true);
        assertFalse(c.up(50f, 50f, 1301L));
    }

    @Test
    public void cancelAndSecondFingerEndTheTap() {
        WallpaperTapClassifier c = make();
        c.down(50f, 50f, 1000L, true);
        c.cancel();
        assertFalse(c.up(50f, 50f, 1050L));
    }

    @Test
    public void aTapDoesNotCountTwice() {
        WallpaperTapClassifier c = make();
        c.down(50f, 50f, 1000L, true);
        assertTrue(c.up(50f, 50f, 1050L));
        assertFalse(c.up(50f, 50f, 1060L));
    }
}
