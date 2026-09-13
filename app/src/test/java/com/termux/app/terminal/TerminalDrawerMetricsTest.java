package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Where the sessions drawer lands, asserted without a laid-out split.
 *
 * <p>Three things decide it and none of them is the drawer's own content: how wide the terminal
 * area is, which way the layout runs, and how much radius a card of that shape can carry.
 */
public class TerminalDrawerMetricsTest {

    private static final float DENSITY = 2f;
    /** 340dp at 2×. */
    private static final int MAX_WIDTH_PX = 680;

    @Test
    public void aWideTerminalGetsTheDpCapAndANarrowOneGetsItsShare() {
        assertEquals("a tablet-width area stops at the dp cap rather than growing with the screen",
            MAX_WIDTH_PX, TerminalDrawerMetrics.widthPx(2000, DENSITY));
        // 78% of 720 = 561.6, which is the binding limit well before the dp cap.
        assertEquals(562, TerminalDrawerMetrics.widthPx(720, DENSITY));
        assertTrue("a fifth of the terminal has to stay visible behind it",
            TerminalDrawerMetrics.widthPx(720, DENSITY) < 720);
    }

    @Test
    public void theDrawerSitsOnTheLeadingEdgeAndSpansTheWholeArea() {
        TerminalDrawerMetrics.Bounds ltr =
            TerminalDrawerMetrics.place(8, 40, 720, 1200, false, DENSITY);

        assertEquals("the area's own left edge, not the plane's", 8, ltr.leftMargin);
        assertEquals(40, ltr.topMargin);
        assertEquals("every pane of the split, so it belongs to the terminal and not to a pane",
            1200, ltr.height);
        assertEquals(562, ltr.width);
    }

    @Test
    public void aRightToLeftLayoutPutsItOnTheOtherEdge() {
        TerminalDrawerMetrics.Bounds rtl =
            TerminalDrawerMetrics.place(8, 40, 720, 1200, true, DENSITY);

        assertEquals("flush with the area's trailing edge, which is leading in RTL",
            8 + 720 - 562, rtl.leftMargin);
        assertEquals(562, rtl.width);
        assertEquals(40, rtl.topMargin);
        assertEquals(1200, rtl.height);
    }

    @Test
    public void itSlidesOutOfWhicheverEdgeItCameFrom() {
        assertEquals(-562f, TerminalDrawerMetrics.enterTranslationX(562, false), 0.001f);
        assertEquals(562f, TerminalDrawerMetrics.enterTranslationX(562, true), 0.001f);
    }

    /** The keyboard rising shortens the area; the drawer shortens with it rather than overhanging. */
    @Test
    public void aShorterAreaMakesAShorterDrawer() {
        TerminalDrawerMetrics.Bounds tall =
            TerminalDrawerMetrics.place(0, 0, 720, 1200, false, DENSITY);
        TerminalDrawerMetrics.Bounds shortened =
            TerminalDrawerMetrics.place(0, 0, 720, 600, false, DENSITY);

        assertEquals(1200, tall.height);
        assertEquals(600, shortened.height);
        assertEquals("only the height moved", tall.width, shortened.width);
    }

    @Test
    public void theTrailingCornersAreCappedWhereATallNarrowCardCannotWearThem() {
        // A third of the shorter side is the cap, so a 562px-wide drawer can carry 187.
        assertEquals(562f / 3f, TerminalDrawerMetrics.trailingRadiusPx(400f, 562, 1200), 0.01f);
        assertEquals("a radius the card can carry is left alone",
            40f, TerminalDrawerMetrics.trailingRadiusPx(40f, 562, 1200), 0.01f);
        assertEquals("a square terminal lends a square drawer",
            0f, TerminalDrawerMetrics.trailingRadiusPx(0f, 562, 1200), 0.01f);
    }

    @Test
    public void theLeadingPairOfRadiiFollowsTheLayoutDirection() {
        float[] ltr = TerminalDrawerMetrics.cornerRadii(30f, 12f, false);
        assertEquals("top-left and bottom-left are the terminal's own edge",
            30f, ltr[0], 0.001f);
        assertEquals(30f, ltr[6], 0.001f);
        assertEquals(12f, ltr[2], 0.001f);
        assertEquals(12f, ltr[4], 0.001f);

        float[] rtl = TerminalDrawerMetrics.cornerRadii(30f, 12f, true);
        assertEquals(12f, rtl[0], 0.001f);
        assertEquals(12f, rtl[6], 0.001f);
        assertEquals(30f, rtl[2], 0.001f);
        assertEquals(30f, rtl[4], 0.001f);
    }
}
