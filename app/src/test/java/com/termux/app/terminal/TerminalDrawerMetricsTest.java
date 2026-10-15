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
            MAX_WIDTH_PX, TerminalDrawerMetrics.widthPx(2000, DENSITY, 1f));
        // 45% of 720 = 324, which is the binding limit well before the dp cap.
        assertEquals(324, TerminalDrawerMetrics.widthPx(720, DENSITY, 1f));
        assertTrue("more than half of the terminal has to stay visible behind it",
            TerminalDrawerMetrics.widthPx(720, DENSITY, 1f) * 2 < 720);
    }

    /** 1080px phones at 420dpi: a 411dp-wide one, and the same panel on a 360dp one. */
    private static final float PHONE_DENSITY = 2.625f;
    private static final int AREA_411_DP_PX = 1080;
    private static final int AREA_360_DP_PX = 945;

    /**
     * The text is in sp, so the drawer widens with the font scale: at 1.5× on a 411dp phone the
     * 1.0 width had cut "Session 1" to "Sessio…". It grows in step until it would take more than
     * {@link TerminalDrawerMetrics#MAX_AREA_WIDTH_SHARE} of the terminal, then stops and lets the
     * rows wrap.
     */
    @Test
    public void theDrawerWidensWithTheFontScaleUpToItsShareOfTheTerminal() {
        // 411dp: 45% at 1.0, 58.5% at 1.3, then the 65% cap from 1.5 on.
        assertEquals(486, TerminalDrawerMetrics.widthPx(AREA_411_DP_PX, PHONE_DENSITY, 1.0f));
        assertEquals(632, TerminalDrawerMetrics.widthPx(AREA_411_DP_PX, PHONE_DENSITY, 1.3f));
        assertEquals(702, TerminalDrawerMetrics.widthPx(AREA_411_DP_PX, PHONE_DENSITY, 1.5f));
        assertEquals(702, TerminalDrawerMetrics.widthPx(AREA_411_DP_PX, PHONE_DENSITY, 2.0f));

        // 360dp: the same shares of a narrower area.
        assertEquals(425, TerminalDrawerMetrics.widthPx(AREA_360_DP_PX, PHONE_DENSITY, 1.0f));
        assertEquals(553, TerminalDrawerMetrics.widthPx(AREA_360_DP_PX, PHONE_DENSITY, 1.3f));
        assertEquals(614, TerminalDrawerMetrics.widthPx(AREA_360_DP_PX, PHONE_DENSITY, 1.5f));
        assertEquals(614, TerminalDrawerMetrics.widthPx(AREA_360_DP_PX, PHONE_DENSITY, 2.0f));

        for (int area : new int[] {AREA_360_DP_PX, AREA_411_DP_PX}) {
            for (float scale : new float[] {1.0f, 1.3f, 1.5f, 2.0f}) {
                int width = TerminalDrawerMetrics.widthPx(area, PHONE_DENSITY, scale);
                assertTrue("at " + scale + "x a third of the terminal stays beside the drawer",
                    width <= Math.round(area * TerminalDrawerMetrics.MAX_AREA_WIDTH_SHARE));
                assertTrue("never narrower than at 1.0",
                    width >= TerminalDrawerMetrics.widthPx(area, PHONE_DENSITY, 1f));
            }
        }
    }

    @Test
    public void aSmallFontScaleKeepsTheOrdinaryWidthAndATabletStillStopsAtTheGrownDpCap() {
        assertEquals("smaller text never needs a narrower panel",
            TerminalDrawerMetrics.widthPx(AREA_411_DP_PX, PHONE_DENSITY, 1f),
            TerminalDrawerMetrics.widthPx(AREA_411_DP_PX, PHONE_DENSITY, 0.85f));
        // 340dp × 1.5 at 2× is 1020px, well under 65% of a 4000px area.
        assertEquals(1020, TerminalDrawerMetrics.widthPx(4000, DENSITY, 1.5f));
        assertEquals("place() carries the same width",
            632, TerminalDrawerMetrics.place(0, 0, AREA_411_DP_PX, 1800, false,
                PHONE_DENSITY, 1.3f).width);
    }

    @Test
    public void theDrawerSitsOnTheLeadingEdgeAndSpansTheWholeArea() {
        TerminalDrawerMetrics.Bounds ltr =
            TerminalDrawerMetrics.place(8, 40, 720, 1200, false, DENSITY, 1f);

        assertEquals("the area's own left edge, not the plane's", 8, ltr.leftMargin);
        assertEquals(40, ltr.topMargin);
        assertEquals("every pane of the split, so it belongs to the terminal and not to a pane",
            1200, ltr.height);
        assertEquals(324, ltr.width);
    }

    @Test
    public void aRightToLeftLayoutPutsItOnTheOtherEdge() {
        TerminalDrawerMetrics.Bounds rtl =
            TerminalDrawerMetrics.place(8, 40, 720, 1200, true, DENSITY, 1f);

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
            TerminalDrawerMetrics.place(0, 0, 720, 1200, false, DENSITY, 1f);
        TerminalDrawerMetrics.Bounds shortened =
            TerminalDrawerMetrics.place(0, 0, 720, 600, false, DENSITY, 1f);

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
