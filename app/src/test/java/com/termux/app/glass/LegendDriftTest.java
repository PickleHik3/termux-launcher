package com.termux.app.glass;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import com.termux.app.chrome.GlassInk;
import com.termux.app.chrome.OnGlass;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * A light to dark crossing as the dock's legends see it: each end's ink is chosen at rest from the
 * face kept for its page and the crossing blends the two, so the ink drifts from dark to pale
 * with no flip, where choosing an ink from a blended face would step at the middle.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LegendDriftTest {

    private static final int LIGHT_FACE = 0xFFE8EAEE;
    private static final int DARK_FACE = 0xFF14161A;
    private static final int THEME_INK = 0xFFE3E6EA;

    private static int luma(int color) {
        return (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
    }

    @Test
    public void legendsDriftFromDarkToPaleAcrossALightToDarkCrossing() {
        LegibilityStore store = new LegibilityStore();
        long home = LegibilityStore.pageKey(0, -1);
        long workspace = LegibilityStore.pageKey(1, -1);
        store.recordFace(home, LegibilityBand.DOCK, 0, 1800, 1080, 2000, 1, LIGHT_FACE);
        store.recordFace(workspace, LegibilityBand.DOCK, 0, 1800, 1080, 2000, 1, DARK_FACE);
        store.beginCrossing();
        double target = OnGlass.TARGET_BODY_TEXT;
        int here = GlassInk.faceInk(store.peek(home, LegibilityBand.DOCK).face(0), THEME_INK, target);
        int there = GlassInk.faceInk(store.peek(workspace, LegibilityBand.DOCK).face(0), THEME_INK,
            target);
        assertTrue("a light page wears dark legends", luma(here) < 128);
        assertTrue("a dark page wears pale legends", luma(there) >= 128);

        int previous = luma(here);
        for (int step = 1; step <= 32; step++) {
            int ink = LegibilityBlend.color(here, there, step / 32f);
            int now = luma(ink);
            assertTrue("never back toward dark at step " + step, now >= previous);
            assertTrue("no flip at step " + step, now - previous <= 16);
            previous = now;
        }
        assertEquals(there, LegibilityBlend.color(here, there, 1f));
        assertEquals(0, store.measuredInCrossing());
        assertEquals(0, store.sampledInCrossing());
    }

    @Test
    public void aBlendedFaceWouldFlipWhereTheBlendedInkDoesNot() {
        double target = OnGlass.TARGET_BODY_TEXT;
        int below = GlassInk.faceInk(LegibilityBlend.color(LIGHT_FACE, DARK_FACE, 0.45f), THEME_INK,
            target);
        int above = GlassInk.faceInk(LegibilityBlend.color(LIGHT_FACE, DARK_FACE, 0.55f), THEME_INK,
            target);
        assertNotEquals("choosing on a blended face steps near the middle", below, above);
    }
}
