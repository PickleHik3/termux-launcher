package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Where the sessions drawer lands, asserted without a laid-out split.
 *
 * <p>Three things decide it and none of them is the drawer's own content: how wide the terminal
 * area is, which way the layout runs, and how much radius a card of that shape can carry.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class TerminalDrawerMetricsTest {

    private static final float DENSITY = 2f;
    /** 340dp at 2×. */
    private static final int MAX_WIDTH_PX = 680;

    @Test
    public void aWideTerminalGetsTheDpCapAndANarrowOneGetsItsShare() {
        assertEquals("a tablet-width area stops at the dp cap rather than growing with the screen",
            MAX_WIDTH_PX, TerminalDrawerMetrics.widthPx(2000, DENSITY));
        // 45% of 720 = 324, which is the binding limit well before the dp cap.
        assertEquals(324, TerminalDrawerMetrics.widthPx(720, DENSITY));
        assertTrue("more than half of the terminal has to stay visible behind it",
            TerminalDrawerMetrics.widthPx(720, DENSITY) * 2 < 720);
    }

    @Test
    public void theDrawerSitsOnTheLeadingEdgeAndSpansTheWholeArea() {
        TerminalDrawerMetrics.Bounds ltr =
            TerminalDrawerMetrics.place(8, 40, 720, 1200, false, DENSITY);

        assertEquals("the area's own left edge, not the plane's", 8, ltr.leftMargin);
        assertEquals(40, ltr.topMargin);
        assertEquals("every pane of the split, so it belongs to the terminal and not to a pane",
            1200, ltr.height);
        assertEquals(324, ltr.width);
    }

    @Test
    public void aRightToLeftLayoutPutsItOnTheOtherEdge() {
        TerminalDrawerMetrics.Bounds rtl =
            TerminalDrawerMetrics.place(8, 40, 720, 1200, true, DENSITY);

        assertEquals("flush with the area's trailing edge, which is leading in RTL",
            8 + 720 - 324, rtl.leftMargin);
        assertEquals(324, rtl.width);
        assertEquals(40, rtl.topMargin);
        assertEquals(1200, rtl.height);
    }

    @Test
    public void itSlidesOutOfWhicheverEdgeItCameFrom() {
        assertEquals(-324f, TerminalDrawerMetrics.enterTranslationX(324, false), 0.001f);
        assertEquals(324f, TerminalDrawerMetrics.enterTranslationX(324, true), 0.001f);
    }

    /**
     * The plane clips at the screen's edge; with a side gap the drawer would show in the gap first.
     * The clip that follows the card is what keeps it behind the terminal's border instead.
     */
    @Test
    public void whileSlidingOnlyThePartInsideTheTerminalIsDrawn() {
        Rect clip = new Rect();

        assertTrue(TerminalDrawerMetrics.slideClip(324, 1200, -324f, false, clip));
        assertEquals("fully out: nothing of it is inside", new Rect(324, 0, 324, 1200), clip);

        assertTrue(TerminalDrawerMetrics.slideClip(324, 1200, -100f, false, clip));
        assertEquals("a third of the way in: the left 100px are still past the edge",
            new Rect(100, 0, 324, 1200), clip);

        assertFalse("at rest there is nothing to cut off",
            TerminalDrawerMetrics.slideClip(324, 1200, 0f, false, clip));
        assertEquals(new Rect(0, 0, 324, 1200), clip);

        assertTrue(TerminalDrawerMetrics.slideClip(324, 1200, 100f, true, clip));
        assertEquals("right to left: the trailing side is what hangs past the edge",
            new Rect(0, 0, 224, 1200), clip);

        assertTrue("overshoot past its own width still clips to an empty rectangle",
            TerminalDrawerMetrics.slideClip(324, 1200, -500f, false, clip));
        assertEquals(new Rect(324, 0, 324, 1200), clip);
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
        // A third of the shorter side is the cap, so a 324px-wide drawer can carry 108.
        assertEquals(324f / 3f, TerminalDrawerMetrics.trailingRadiusPx(400f, 324, 1200), 0.01f);
        assertEquals("a radius the card can carry is left alone",
            40f, TerminalDrawerMetrics.trailingRadiusPx(40f, 324, 1200), 0.01f);
        assertEquals("a square terminal lends a square drawer",
            0f, TerminalDrawerMetrics.trailingRadiusPx(0f, 324, 1200), 0.01f);
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
