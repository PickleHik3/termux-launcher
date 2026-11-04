package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The keyboard's frost over a Display page: the compositor's blur, or plain see-through. */
public class DisplayKeyboardBlurPolicyTest {

    private static final float DENSITY = 2.75f;

    @Test
    public void theFrostIsTheLooksRadiusInPixels() {
        assertEquals(Math.round(20 * DENSITY),
            DisplayKeyboardBlurPolicy.blurRadiusPx(34, true, false, 20, DENSITY));
        assertTrue(DisplayKeyboardBlurPolicy.frosts(31, true, false, 1, DENSITY));
    }

    @Test
    public void belowAndroid12TheBedIsSeeThrough() {
        assertEquals(0, DisplayKeyboardBlurPolicy.blurRadiusPx(30, true, false, 20, DENSITY));
        assertFalse(DisplayKeyboardBlurPolicy.frosts(28, true, false, 20, DENSITY));
    }

    @Test
    public void windowBlurOffOrBatterySaverFallsBackToSeeThrough() {
        assertEquals(0, DisplayKeyboardBlurPolicy.blurRadiusPx(34, false, false, 20, DENSITY));
        assertEquals(0, DisplayKeyboardBlurPolicy.blurRadiusPx(34, true, true, 20, DENSITY));
    }

    @Test
    public void frostZeroDrawsNoBlurLayer() {
        assertEquals(0, DisplayKeyboardBlurPolicy.blurRadiusPx(34, true, false, 0, DENSITY));
        assertEquals(0, DisplayKeyboardBlurPolicy.blurRadiusPx(34, true, false, -4, DENSITY));
        assertEquals(0, DisplayKeyboardBlurPolicy.blurRadiusPx(34, true, false, 20, 0f));
    }

    @Test
    public void theBlurTakesTheSlicesLargestCornerSoItNeverShowsOutsideTheOutline() {
        assertEquals(24f, DisplayKeyboardBlurPolicy.cornerPx(24f, 24f, 0f, 0f), 0f);
        assertEquals(0f, DisplayKeyboardBlurPolicy.cornerPx(0f, 0f, 0f, 0f), 0f);
        assertEquals(0f, DisplayKeyboardBlurPolicy.cornerPx(-1f, -2f, -3f, -4f), 0f);
    }

    @Test
    public void theBedIsPlacedInTheDisplaySurfacesSpace() {
        int[] out = new int[2];
        DisplayKeyboardBlurPolicy.toSurfaceSpace(new int[] {12, 1400}, new int[] {12, 96}, out);
        assertEquals(0, out[0]);
        assertEquals(1304, out[1]);
        // A display slid half off the left edge mid-crossing: the bed sits right of its origin.
        DisplayKeyboardBlurPolicy.toSurfaceSpace(new int[] {0, 900}, new int[] {-540, 0}, out);
        assertEquals(540, out[0]);
        assertEquals(900, out[1]);
    }

    @Test
    public void underTheEditorsScaledPreviewTheFrostKeepsItsFullSizePlace() {
        // Full size: display at (0, 96), bed at (0, 1400).
        int[] full = new int[2];
        DisplayKeyboardBlurPolicy.toSurfaceSpace(new int[] {0, 1400}, new int[] {0, 96}, 1f, 1f,
            full);
        // The preview at 0.5, its origin at (270, 180): both views move and shrink alike.
        int[] scaled = new int[2];
        DisplayKeyboardBlurPolicy.toSurfaceSpace(new int[] {270, 180 + 700},
            new int[] {270, 180 + 48}, 0.5f, 0.5f, scaled);
        assertEquals(full[0], scaled[0]);
        assertEquals(full[1], scaled[1]);
        assertEquals(1304, scaled[1]);
        // A scale of 0 counts as none rather than dividing by it.
        DisplayKeyboardBlurPolicy.toSurfaceSpace(new int[] {10, 20}, new int[] {0, 0}, 0f, -1f,
            scaled);
        assertEquals(10, scaled[0]);
        assertEquals(20, scaled[1]);
    }
}
