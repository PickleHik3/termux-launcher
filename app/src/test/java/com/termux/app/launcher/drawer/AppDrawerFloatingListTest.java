package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerFloatingListTest {

    @Test public void fadeIsOneLengthScaledByDensity() {
        assertEquals(24, AppDrawerFloatingList.fadeLengthPx(1f));
        assertEquals(60, AppDrawerFloatingList.fadeLengthPx(2.5f));
        assertEquals(1, AppDrawerFloatingList.fadeLengthPx(0f));
    }

    @Test public void topPaddingStacksTheBandsTheRowsRestBelow() {
        assertEquals(100, AppDrawerFloatingList.topPaddingPx(100, 0, 0));
        assertEquals(100 + 20 + 30, AppDrawerFloatingList.topPaddingPx(100, 20, 30));
        assertEquals(0, AppDrawerFloatingList.topPaddingPx(-5, -5, -5));
    }

    @Test public void theFadeStandsAtTheViewsEdgesNotThePaddings() {
        assertEquals(-100, AppDrawerFloatingList.topFadeOffsetPx(100));
        assertEquals(80, AppDrawerFloatingList.bottomFadeOffsetPx(80));
        assertEquals(0, AppDrawerFloatingList.topFadeOffsetPx(-4));
        assertEquals(0, AppDrawerFloatingList.bottomFadeOffsetPx(-4));
    }

    @Test public void theCapsuleTakesTouchesOnlyInsideItsShape() {
        // A 100 x 40 capsule: centre in, the square corner out, the end caps round.
        assertTrue(AppDrawerFloatingList.capsuleContains(50f, 20f, 0f, 100f, 40f));
        assertFalse(AppDrawerFloatingList.capsuleContains(1f, 1f, 0f, 100f, 40f));
        assertFalse(AppDrawerFloatingList.capsuleContains(99f, 39f, 0f, 100f, 40f));
        assertTrue(AppDrawerFloatingList.capsuleContains(2f, 20f, 0f, 100f, 40f));
        assertFalse(AppDrawerFloatingList.capsuleContains(-1f, 20f, 0f, 100f, 40f));
        assertFalse(AppDrawerFloatingList.capsuleContains(50f, 40f, 0f, 100f, 40f));
    }

    @Test public void aFoldedCapsuleIsACircleAtItsEnd() {
        // Folded to the round button on the right: left 60, right 100, height 40 (a circle).
        assertTrue(AppDrawerFloatingList.capsuleContains(80f, 20f, 60f, 100f, 40f));
        assertFalse(AppDrawerFloatingList.capsuleContains(30f, 20f, 60f, 100f, 40f));
        assertFalse(AppDrawerFloatingList.capsuleContains(61f, 1f, 60f, 100f, 40f));
        assertFalse(AppDrawerFloatingList.capsuleContains(5f, 5f, 10f, 10f, 40f));
    }

    @Test public void theFadeStopsWhereTheCapsuleStarts() {
        // The shipped capsule: 12dp from the card's top under a 24dp fade, at 3x.
        int fade = AppDrawerFloatingList.fadeLengthPx(3f);
        assertEquals(0.5f, AppDrawerFloatingList.fadeStrengthCap(36, fade), 1e-6f);
        assertEquals(0f, AppDrawerFloatingList.fadeStrengthCap(0, fade), 1e-6f);
        // A capsule further in than the fade leaves it whole; an edge without one fades in full.
        assertEquals(1f, AppDrawerFloatingList.fadeStrengthCap(fade * 2, fade), 1e-6f);
        assertEquals(1f, AppDrawerFloatingList.fadeStrengthCap(-1, fade), 1e-6f);
    }
}
