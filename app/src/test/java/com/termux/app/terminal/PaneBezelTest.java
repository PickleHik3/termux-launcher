package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The bezel a pane wears under a terminal effect: its size, the screen the effect is cut to, and
 * the terminal's margins inside it, which have to hold every cell on the screen after the bend.
 */
public class PaneBezelTest {

    private static final float DENSITY = 2.75f;
    private static final float W = 1080f;
    private static final float H = 2000f;

    @Test
    public void everyEffectWearsTheBezelAndNoneTakesItOff() {
        assertFalse(PaneBezel.worn(PaneRetroStyle.NONE));
        for (PaneRetroStyle style : PaneRetroStyle.values())
            if (style != PaneRetroStyle.NONE) assertTrue(style.name(), PaneBezel.worn(style));
        assertEquals(0, PaneBezel.sidePx(false, DENSITY));
        assertEquals(0, PaneBezel.topBottomPx(false, DENSITY));
        assertEquals(33, PaneBezel.sidePx(true, DENSITY));
        assertEquals(44, PaneBezel.topBottomPx(true, DENSITY));
    }

    @Test
    public void withoutTheBezelTheScreenIsTheWholePaneAtItsOwnRadius() {
        float[] screen = new float[4];
        assertEquals(30f, PaneBezel.screen(false, W, H, DENSITY, 30f, screen), 0f);
        assertRect(screen, 0f, 0f, W, H);
    }

    @Test
    public void theScreenStandsInsideTheBezelRoundedAtTenDp() {
        float[] screen = new float[4];
        float radius = PaneBezel.screen(true, W, H, DENSITY, 30f, screen);
        assertRect(screen, 33f, 44f, W - 33f, H - 44f);
        assertEquals(PaneBezel.SCREEN_RADIUS_DP * DENSITY, radius, 0.001f);
        // A pane only a few rows tall caps the screen's corners like any pane.
        float shortRadius = PaneBezel.screen(true, W, 150f, DENSITY, 30f, screen);
        assertEquals((150f - 88f) / 3f, shortRadius, 1f);
        // Smaller than its own bezel, the screen closes to nothing rather than turning inside out.
        PaneBezel.screen(true, 40f, 60f, DENSITY, 0f, screen);
        assertTrue(screen[2] >= screen[0]);
        assertTrue(screen[3] >= screen[1]);
    }

    @Test
    public void theBendsClearanceIsWhatFallsOffTheMiddleOfAnEdge() {
        assertEquals(0, PaneBezel.bendClearancePx(1000f, 0f));
        assertEquals(0, PaneBezel.bendClearancePx(0f, PaneRetroEffect.CRT_BEND));
        // b / (1 + 2b) of the half-extent: 0.04 / 1.08 of 1000 px is 37.04, rounded up.
        assertEquals(38, PaneBezel.bendClearancePx(1000f, PaneRetroEffect.CRT_BEND));
    }

    @Test
    public void theTerminalSitsPastTheBezelTheArcAndTheBend() {
        int[] insets = new int[2];
        PaneBezel.contentInsets(W, H, DENSITY, 0f, insets);
        int arc = PaneShape.contentInsetPx(PaneBezel.SCREEN_RADIUS_DP * DENSITY);
        assertEquals(33 + arc, insets[0]);
        assertEquals(44 + arc, insets[1]);
        PaneBezel.contentInsets(W, H, DENSITY, PaneRetroEffect.CRT_BEND, insets);
        assertEquals(33 + arc + PaneBezel.bendClearancePx((W - 66f) / 2f, PaneRetroEffect.CRT_BEND),
            insets[0]);
        assertEquals(44 + arc + PaneBezel.bendClearancePx((H - 88f) / 2f, PaneRetroEffect.CRT_BEND),
            insets[1]);
    }

    @Test
    public void everyCellTheTerminalHoldsIsShownOnTheBentScreen() {
        // The terminal's box at those margins, and each point of its outline, bent as the CRT
        // shader bends it: every one has to land inside the screen, or text is lost off the edge.
        float[] screen = new float[4];
        PaneBezel.screen(true, W, H, DENSITY, 0f, screen);
        int[] insets = new int[2];
        PaneBezel.contentInsets(W, H, DENSITY, PaneRetroEffect.CRT_BEND, insets);
        float left = insets[0], top = insets[1], right = W - insets[0], bottom = H - insets[1];
        float[] out = new float[2];
        for (int i = 0; i <= 20; i++) {
            float t = i / 20f;
            float[][] points = {
                {left + t * (right - left), top}, {left + t * (right - left), bottom},
                {left, top + t * (bottom - top)}, {right, top + t * (bottom - top)}};
            for (float[] p : points) {
                PaneRetroEffect.displayedPoint(PaneRetroStyle.CRT, screen[0], screen[1],
                    screen[2], screen[3], p[0], p[1], out);
                assertTrue("x " + out[0], out[0] >= screen[0] && out[0] <= screen[2]);
                assertTrue("y " + out[1], out[1] >= screen[1] && out[1] <= screen[3]);
            }
        }
    }

    @Test
    public void theBezelIsFilledOnlyWhenTheGlassHasNoOpacity() {
        int surface = 0x00123456;
        assertEquals(0xFF123456, PaneBezel.fill(true, 0x00FFFFFF, surface));
        assertEquals(0, PaneBezel.fill(true, 0x40000000, surface));
        assertEquals(0, PaneBezel.fill(false, 0x00000000, surface));
    }

    private static void assertRect(float[] r, float l, float t, float right, float b) {
        assertEquals(l, r[0], 0.001f);
        assertEquals(t, r[1], 0.001f);
        assertEquals(right, r[2], 0.001f);
        assertEquals(b, r[3], 0.001f);
    }
}
