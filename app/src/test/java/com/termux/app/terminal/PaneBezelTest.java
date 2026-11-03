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
        assertEquals(22, PaneBezel.sidePx(true, DENSITY));
        assertEquals(28, PaneBezel.topBottomPx(true, DENSITY));
        // 8 dp and 10 dp, a thin frame: 10 dp at 2.75 is 27.5, rounded up.
        assertEquals(8f, PaneBezel.SIDE_DP, 0f);
        assertEquals(10f, PaneBezel.TOP_BOTTOM_DP, 0f);
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
        assertRect(screen, 22f, 28f, W - 22f, H - 28f);
        assertEquals(PaneBezel.SCREEN_RADIUS_DP * DENSITY, radius, 0.001f);
        // A pane only a few rows tall caps the screen's corners like any pane.
        float shortRadius = PaneBezel.screen(true, W, 120f, DENSITY, 30f, screen);
        assertEquals((120f - 56f) / 3f, shortRadius, 1f);
        // Smaller than its own bezel, the screen closes to nothing rather than turning inside out.
        PaneBezel.screen(true, 40f, 60f, DENSITY, 0f, screen);
        assertTrue(screen[2] >= screen[0]);
        assertTrue(screen[3] >= screen[1]);
    }

    @Test
    public void theTerminalSitsPastTheBezelAndTheArcAndTheBendNeedsNoClearance() {
        int[] insets = new int[2];
        PaneBezel.contentInsets(W, H, DENSITY, insets);
        int arc = PaneShape.contentInsetPx(PaneBezel.SCREEN_RADIUS_DP * DENSITY);
        assertEquals(22 + arc, insets[0]);
        assertEquals(28 + arc, insets[1]);
        // The bend is pinned at the middle of each edge: the picture's edge middles land on
        // the screen's, and its corners pull inward, so no cell needs room past the arc.
        assertTrue(PaneRetroEffect.CRT_AGSL.contains(
            "c * (1.0 + uBend * dot(c, c)) / (1.0 + uBend)"));
    }

    @Test
    public void everyCellTheTerminalHoldsIsShownOnTheBentScreen() {
        // The terminal's box at those margins, and each point of its outline, bent as the CRT
        // shader bends it: every one has to land inside the screen, or text is lost off the edge.
        float[] screen = new float[4];
        PaneBezel.screen(true, W, H, DENSITY, 0f, screen);
        int[] insets = new int[2];
        PaneBezel.contentInsets(W, H, DENSITY, insets);
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
    public void theBezelIsAlwaysFilledOpaqueInThePanelColour() {
        assertEquals(0xFF123456, PaneBezel.fill(0x00123456));
        assertEquals(0xFF123456, PaneBezel.fill(0x40123456));
        assertEquals(0xFF123456, PaneBezel.fill(0xFF123456));
    }

    @Test
    public void theBezelIsLitAtTheTopAndShadedAtTheBottom() {
        assertEquals(4, PaneBezel.SHADE_COLOURS.length);
        assertEquals(PaneBezel.SHADE_COLOURS.length, PaneBezel.SHADE_POSITIONS.length);
        // White at 8 % on top, clear at the middle, black at 18 % at the bottom.
        assertEquals(0xFFFFFF, PaneBezel.SHADE_COLOURS[0] & 0xFFFFFF);
        assertEquals(Math.round(0.08f * 255), PaneBezel.SHADE_COLOURS[0] >>> 24);
        assertEquals(0, PaneBezel.SHADE_COLOURS[1] >>> 24);
        assertEquals(0, PaneBezel.SHADE_COLOURS[2] >>> 24);
        assertEquals(0x000000, PaneBezel.SHADE_COLOURS[3] & 0xFFFFFF);
        assertEquals(Math.round(0.18f * 255), PaneBezel.SHADE_COLOURS[3] >>> 24);
        assertEquals(0f, PaneBezel.SHADE_POSITIONS[0], 0f);
        assertEquals(0.5f, PaneBezel.SHADE_POSITIONS[1], 0f);
        assertEquals(0.5f, PaneBezel.SHADE_POSITIONS[2], 0f);
        assertEquals(1f, PaneBezel.SHADE_POSITIONS[3], 0f);
    }

    private static void assertRect(float[] r, float l, float t, float right, float b) {
        assertEquals(l, r[0], 0.001f);
        assertEquals(t, r[1], 0.001f);
        assertEquals(right, r[2], 0.001f);
        assertEquals(b, r[3], 0.001f);
    }
}
