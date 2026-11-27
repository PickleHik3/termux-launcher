package com.termux.app.tiling;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallpaperTapTest {

    private final WallpaperTap tap = new WallpaperTap(10f, 300L);

    @Test
    public void aQuickStillLiftIsATap() {
        tap.down(50f, 50f, 0L);
        assertTrue(tap.up(52f, 51f, 120L));
    }

    @Test
    public void aFingerThatTravelledIsADrag() {
        tap.down(50f, 50f, 0L);
        tap.move(50f, 90f);
        assertFalse(tap.up(50f, 52f, 100L));
    }

    @Test
    public void aLongPressIsNoTap() {
        tap.down(50f, 50f, 0L);
        assertFalse(tap.up(50f, 50f, 301L));
    }

    @Test
    public void aSecondFingerOrACancelEndsIt() {
        tap.down(50f, 50f, 0L);
        tap.cancel();
        assertFalse(tap.up(50f, 50f, 50L));
    }

    @Test
    public void aLiftWithNoDownIsNoTap() {
        assertFalse(tap.up(1f, 1f, 10L));
    }

    @Test
    public void eachStreamStartsFresh() {
        tap.down(0f, 0f, 0L);
        tap.move(100f, 0f);
        tap.up(100f, 0f, 50L);
        tap.down(5f, 5f, 1000L);
        assertTrue(tap.up(5f, 5f, 1050L));
    }
}
