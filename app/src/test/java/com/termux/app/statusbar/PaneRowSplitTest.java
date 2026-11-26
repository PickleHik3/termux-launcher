package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.statusbar.PaneRowPolicy.SplitRow;
import com.termux.app.terminal.PaneShape;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The thin row in a column split: a band above two or more columns, the row in the top pane for
 * one pane or panes in rows only. Every pane along the top holds the same
 * room, so the first lines of text line up and no terminal changes size between the cases.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, application = android.app.Application.class)
public class PaneRowSplitTest {

    private static final float DENSITY = 2.75f;
    private static final int ROW = PaneRowPolicy.rowHeightPx(DENSITY);
    private static final float RADIUS = 24f * DENSITY;

    @Test
    public void theRowStandsByHowManyPanesTouchTheTop() {
        assertEquals(SplitRow.IN_PANE, PaneRowPolicy.splitRow(0));
        assertEquals("one pane, or panes in rows only", SplitRow.IN_PANE, PaneRowPolicy.splitRow(1));
        assertEquals("two columns", SplitRow.BAND, PaneRowPolicy.splitRow(2));
        assertEquals("three columns", SplitRow.BAND, PaneRowPolicy.splitRow(3));
        assertEquals(SplitRow.BAND, PaneRowPolicy.splitRow(5));
    }

    @Test
    public void everyPaneAlongTheTopHoldsTheSameRoomSoTheirFirstLinesLineUp() {
        for (int along = 1; along <= 4; along++) {
            assertEquals("along the top of " + along, ROW,
                PaneRowPolicy.paneTopInsetPx(true, true, ROW));
            assertEquals("below the top of " + along, 0,
                PaneRowPolicy.paneTopInsetPx(true, false, ROW));
        }
    }

    @Test
    public void onlyTheBandMovesTheFramesOfThePanesAlongTheTop() {
        assertEquals("one pane", 0, PaneRowPolicy.cardTopPx(true, 1, true, ROW));
        assertEquals("rows only: the top pane", 0, PaneRowPolicy.cardTopPx(true, 1, true, ROW));
        assertEquals("rows only: the lower pane", 0, PaneRowPolicy.cardTopPx(true, 1, false, ROW));
        assertEquals("two columns", ROW, PaneRowPolicy.cardTopPx(true, 2, true, ROW));
        assertEquals("two columns, a pane below the top", 0,
            PaneRowPolicy.cardTopPx(true, 2, false, ROW));
        assertEquals("three columns", ROW, PaneRowPolicy.cardTopPx(true, 3, true, ROW));
        assertEquals("three columns, a pane below the top", 0,
            PaneRowPolicy.cardTopPx(true, 3, false, ROW));
        assertEquals("the strip stands", 0, PaneRowPolicy.cardTopPx(false, 3, true, ROW));
    }

    @Test
    public void theBandMovesTheFrameWithoutResizingTheTerminal() {
        // The terminal's height is the frame's less its room; the frame's card start is only drawn.
        int frame = 900;
        int inPane = frame - PaneRowPolicy.paneTopInsetPx(true, true, ROW);
        int band = frame - PaneRowPolicy.paneTopInsetPx(true, true, ROW);
        assertEquals(inPane, band);
        // The card starts where the room ends: the terminal's top margin sits on the card's top.
        assertEquals(PaneRowPolicy.paneTopInsetPx(true, true, ROW),
            PaneRowPolicy.cardTopPx(true, 3, true, ROW));
    }

    @Test
    public void onePaneIsPixelIdenticalToTheRowBeforeSplits() {
        android.graphics.Rect out = new android.graphics.Rect();
        float[] radii = {0f, 4f * DENSITY, RADIUS, 40f * DENSITY};
        for (float radius : radii) {
            for (boolean rtl : new boolean[]{false, true}) {
                PaneRowPolicy.rowRect(30, 150, 1000, radius, rtl, DENSITY, out);
                // The row as it was laid out before splits: one frame, both ends its own arc.
                int start = PaneRowPolicy.startInsetPx(radius, ROW, DENSITY);
                int end = PaneRowPolicy.endInsetPx(radius, ROW, DENSITY);
                assertEquals(30 + (rtl ? end : start), out.left);
                assertEquals(150, out.top);
                assertEquals(1030 - (rtl ? start : end), out.right);
                assertEquals(150 + ROW, out.bottom);
                android.graphics.Rect span = new android.graphics.Rect();
                PaneRowPolicy.spanRowRect(30, 150, 1030, radius, radius, rtl, DENSITY, span);
                assertEquals(out, span);
            }
        }
        assertEquals("a pane with no band keeps its own arc", RADIUS,
            PaneRowPolicy.endRadiusPx(RADIUS, 0), 0f);
    }

    @Test
    public void twoColumnsStandTheBandOverBothPanesWithNoArcUnderIt() {
        // Left pane 20..520, right pane 540..1060 (a 20px gap), both at 150.
        int cardTop = PaneRowPolicy.cardTopPx(true, 2, true, ROW);
        assertEquals(ROW, cardTop);
        android.graphics.Rect out = new android.graphics.Rect();
        PaneRowPolicy.spanRowRect(20, 150, 1060, PaneRowPolicy.endRadiusPx(RADIUS, cardTop),
            PaneRowPolicy.endRadiusPx(40f * DENSITY, cardTop), false, DENSITY, out);
        assertEquals(20 + Math.round(PaneRowPolicy.START_INSET_DP * DENSITY), out.left);
        assertEquals(1060 - Math.round(PaneRowPolicy.END_INSET_DP * DENSITY), out.right);
        // Right to left: the marks start at the right, so the start inset is the right end's.
        PaneRowPolicy.spanRowRect(20, 150, 1060, 0f, 0f, true, DENSITY, out);
        assertEquals(20 + Math.round(PaneRowPolicy.END_INSET_DP * DENSITY), out.left);
        assertEquals(1060 - Math.round(PaneRowPolicy.START_INSET_DP * DENSITY), out.right);
    }

    @Test
    public void theBandsGlassCoversTheColumnsAndHoldsTheRowAndTheCardsStartAtItsBottom() {
        android.graphics.Rect band = new android.graphics.Rect();
        PaneRowPolicy.bandRect(20, 150, 1060, DENSITY, band);
        assertEquals(new android.graphics.Rect(20, 150, 1060, 150 + ROW), band);
        android.graphics.Rect row = new android.graphics.Rect();
        PaneRowPolicy.spanRowRect(20, 150, 1060, 0f, 0f, false, DENSITY, row);
        assertTrue("the row lies inside the glass", band.contains(row));
        assertEquals("same top", band.top, row.top);
        assertEquals("same bottom", band.bottom, row.bottom);
        // The cards start where the band ends: frame top + the card's drop.
        assertEquals(band.bottom, 150 + PaneRowPolicy.cardTopPx(true, 2, true, ROW));
    }

    @Test
    public void theBandSpansEveryColumnInByTheMockUpsInsetsOnly() {
        int cardTop = PaneRowPolicy.cardTopPx(true, 3, true, ROW);
        float left = PaneRowPolicy.endRadiusPx(RADIUS, cardTop);
        float right = PaneRowPolicy.endRadiusPx(40f * DENSITY, cardTop);
        assertEquals("no arc reaches the band", 0f, left, 0f);
        assertEquals(0f, right, 0f);
        android.graphics.Rect out = new android.graphics.Rect();
        PaneRowPolicy.spanRowRect(20, 150, 1060, left, right, false, DENSITY, out);
        assertEquals(20 + Math.round(PaneRowPolicy.START_INSET_DP * DENSITY), out.left);
        assertEquals(1060 - Math.round(PaneRowPolicy.END_INSET_DP * DENSITY), out.right);
        // At the row's own height, its hairline the last pixel: between it and the terminals,
        // whose cards start where the band ends.
        assertEquals(150 + cardTop, out.bottom);
        android.graphics.Rect hairline = new android.graphics.Rect();
        PaneRowPolicy.hairlineRect(out.left, out.top, out.right, out.bottom, hairline);
        assertEquals(150 + cardTop - 1, hairline.top);
        assertEquals(150 + cardTop, hairline.bottom);
    }

    @Test
    public void smallPanesClampTheirOwnArcAndTheRowStillFits() {
        // Two narrow columns, 120px and 90px wide, 400px tall: radiusForBounds caps each arc.
        float requested = 40f * DENSITY;
        float leftRadius = PaneShape.radiusForBounds(requested, 120, 400);
        float rightRadius = PaneShape.radiusForBounds(requested, 90, 400);
        assertTrue(leftRadius < requested);
        assertTrue(rightRadius < leftRadius);
        android.graphics.Rect out = new android.graphics.Rect();
        PaneRowPolicy.spanRowRect(0, 0, 230, leftRadius, rightRadius, false, DENSITY, out);
        assertEquals(PaneRowPolicy.startInsetPx(leftRadius, ROW, DENSITY), out.left);
        assertEquals(230 - PaneRowPolicy.endInsetPx(rightRadius, ROW, DENSITY), out.right);
        assertTrue("the row keeps a width", out.right > out.left);
        assertTrue("clear of the left arc", out.left >= PaneRowPolicy.arcInsetPx(leftRadius, ROW / 2f));
        assertTrue("clear of the right arc",
            230 - out.right >= PaneRowPolicy.arcInsetPx(rightRadius, ROW / 2f));
    }
}
