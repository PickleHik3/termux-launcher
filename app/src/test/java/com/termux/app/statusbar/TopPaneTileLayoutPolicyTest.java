package com.termux.app.statusbar;

import android.graphics.Rect;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Protects the equal-cell split that gives a top-slot tile the same dimensions as the clock, the
 * alignment-driven cell order (Widgets left of Display, mirroring the swipe direction), and the
 * degenerate-input clamping so a bad measurement never produces a negative or thrown rect.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TopPaneTileLayoutPolicyTest {

    private static final int WIDTH = 360;
    private static final int HEIGHT = 68;
    private static final int GUTTER = 12;
    private static final int GAP = 12;

    @Test
    public void threeCells_leftAlignment_putsClockFirst() {
        TopPaneTileLayoutPolicy.Result r = TopPaneTileLayoutPolicy.calculate(
            WIDTH, HEIGHT, GUTTER, GAP, "left", true, true, 0, false);
        assertEquals(new Rect(12, 0, 116, 68), r.clock);
        assertEquals(new Rect(128, 0, 232, 68), r.widgets);
        assertEquals(new Rect(244, 0, 348, 68), r.display);
        assertEqualCellWidths(r.clock, r.widgets, r.display);
        assertCellsFillUsableWidth(r.clock, r.widgets, r.display);
    }

    @Test
    public void threeCells_centerAlignment_putsClockInTheMiddle() {
        TopPaneTileLayoutPolicy.Result r = TopPaneTileLayoutPolicy.calculate(
            WIDTH, HEIGHT, GUTTER, GAP, "center", true, true, 0, false);
        assertEquals(new Rect(12, 0, 116, 68), r.widgets);
        assertEquals(new Rect(128, 0, 232, 68), r.clock);
        assertEquals(new Rect(244, 0, 348, 68), r.display);
        assertEqualCellWidths(r.clock, r.widgets, r.display);
        assertCellsFillUsableWidth(r.widgets, r.clock, r.display);
    }

    @Test
    public void threeCells_rightAlignment_putsClockLast() {
        TopPaneTileLayoutPolicy.Result r = TopPaneTileLayoutPolicy.calculate(
            WIDTH, HEIGHT, GUTTER, GAP, "right", true, true, 0, false);
        assertEquals(new Rect(12, 0, 116, 68), r.widgets);
        assertEquals(new Rect(128, 0, 232, 68), r.display);
        assertEquals(new Rect(244, 0, 348, 68), r.clock);
        assertEqualCellWidths(r.clock, r.widgets, r.display);
        assertCellsFillUsableWidth(r.widgets, r.display, r.clock);
    }

    @Test
    public void twoCells_widgetsTileOnly_forAllThreeAlignments() {
        for (String alignment : new String[] {"left", "center", "right"}) {
            TopPaneTileLayoutPolicy.Result r = TopPaneTileLayoutPolicy.calculate(
                WIDTH, HEIGHT, GUTTER, GAP, alignment, true, false, 0, false);
            assertTrue(alignment, r.display.isEmpty());
            assertFalse(alignment, r.widgets.isEmpty());
            assertFalse(alignment, r.clock.isEmpty());
            assertEquals(alignment, r.clock.width(), r.widgets.width());
            assertCellsFillUsableWidth(r.clock.left < r.widgets.left ? r.clock : r.widgets,
                r.clock.left < r.widgets.left ? r.widgets : r.clock);
            // "left" puts the clock in the first of the two cells; "center"/"right" (both
            // collapse to the same single trailing cell when there are only two) put it last.
            if ("left".equals(alignment)) {
                assertTrue(r.clock.left < r.widgets.left);
            } else {
                assertTrue(r.widgets.left < r.clock.left);
            }
        }
    }

    @Test
    public void twoCells_displayTileOnly_forAllThreeAlignments() {
        for (String alignment : new String[] {"left", "center", "right"}) {
            TopPaneTileLayoutPolicy.Result r = TopPaneTileLayoutPolicy.calculate(
                WIDTH, HEIGHT, GUTTER, GAP, alignment, false, true, 0, false);
            assertTrue(alignment, r.widgets.isEmpty());
            assertFalse(alignment, r.display.isEmpty());
            assertFalse(alignment, r.clock.isEmpty());
            assertEquals(alignment, r.clock.width(), r.display.width());
            if ("left".equals(alignment)) {
                assertTrue(r.clock.left < r.display.left);
            } else {
                assertTrue(r.display.left < r.clock.left);
            }
        }
    }

    @Test
    public void oneCell_clockTakesTheWholeUsableWidth_noTilesEitherAlignment() {
        for (String alignment : new String[] {"left", "center", "right"}) {
            TopPaneTileLayoutPolicy.Result r = TopPaneTileLayoutPolicy.calculate(
                WIDTH, HEIGHT, GUTTER, GAP, alignment, false, false, 0, false);
            assertEquals(alignment, new Rect(GUTTER, 0, WIDTH - GUTTER, HEIGHT), r.clock);
            assertTrue(alignment, r.widgets.isEmpty());
            assertTrue(alignment, r.display.isEmpty());
        }
    }

    @Test
    public void gutterSitsOnBothEdges_gapSitsBetweenCells() {
        TopPaneTileLayoutPolicy.Result r = TopPaneTileLayoutPolicy.calculate(
            WIDTH, HEIGHT, GUTTER, GAP, "left", true, true, 0, false);
        assertEquals(GUTTER, r.clock.left);
        assertEquals(WIDTH - GUTTER, r.display.right);
        assertEquals(GAP, r.widgets.left - r.clock.right);
        assertEquals(GAP, r.display.left - r.widgets.right);
    }

    @Test
    public void rtlMirrorsPixelsButKeepsTheLogicalCellAssignment() {
        TopPaneTileLayoutPolicy.Result ltr = TopPaneTileLayoutPolicy.calculate(
            WIDTH, HEIGHT, GUTTER, GAP, "left", true, true, 0, false);
        TopPaneTileLayoutPolicy.Result rtl = TopPaneTileLayoutPolicy.calculate(
            WIDTH, HEIGHT, GUTTER, GAP, "left", true, true, 0, true);
        assertMirrored(ltr.clock, rtl.clock);
        assertMirrored(ltr.widgets, rtl.widgets);
        assertMirrored(ltr.display, rtl.display);
        // Logical order is unchanged: the clock was the leftmost logical cell, so mirroring
        // makes it the rightmost on screen, and display (rightmost logically) becomes leftmost.
        assertTrue(rtl.clock.left > rtl.widgets.left);
        assertTrue(rtl.display.left < rtl.widgets.left);
    }

    @Test
    public void clockCompact_falseWhenTheFullFaceFitsItsCell() {
        // Three cells at this width/gutter/gap give each cell 312px (computed below).
        TopPaneTileLayoutPolicy.Result r = TopPaneTileLayoutPolicy.calculate(
            1080, HEIGHT * 3, 36, 36, "left", true, true, 260, false);
        assertEquals(312, r.clock.width());
        assertFalse(r.clockCompact);
    }

    @Test
    public void clockCompact_trueAtA1point3xFontScaleOnA360dpScreen() {
        // 360dp-wide screen at xxhdpi (density 3) -> 1080px, with the slot's real 12dp
        // gutter/gap (36px at this density). Three equal cells of 1008px usable / 3 = 336px...
        // wait: (1080 - 2*36 - 2*36) / 3 = (1080-144)/3 = 312px per cell (same layout as above).
        // The clock's full face wants ~260px at 1x system font scale (fits: 260 <= 312); at a
        // 1.3x font scale it grows to 260*1.3 = 338px, which no longer fits a 312px cell.
        TopPaneTileLayoutPolicy.Result r = TopPaneTileLayoutPolicy.calculate(
            1080, HEIGHT * 3, 36, 36, "left", true, true, 338, false);
        assertEquals(312, r.clock.width());
        assertTrue(r.clockCompact);
    }

    @Test
    public void degenerateInputs_neverThrowOrGoNegative() {
        assertNoNegativeRects(TopPaneTileLayoutPolicy.calculate(
            0, HEIGHT, GUTTER, GAP, "left", true, true, 100, false));
        assertNoNegativeRects(TopPaneTileLayoutPolicy.calculate(
            WIDTH, 0, GUTTER, GAP, "left", true, true, 100, false));
        // Gutter alone consumes more than the whole slot.
        assertNoNegativeRects(TopPaneTileLayoutPolicy.calculate(
            100, HEIGHT, 60, GAP, "left", true, true, 100, false));
        // Gap alone, across two gaps, dwarfs the slot: no tile rect is invented, but the clock
        // still gets whatever usable width remains.
        TopPaneTileLayoutPolicy.Result hugeGap = TopPaneTileLayoutPolicy.calculate(
            WIDTH, HEIGHT, GUTTER, 1000, "left", true, true, 100, false);
        assertNoNegativeRects(hugeGap);
        assertTrue(hugeGap.widgets.isEmpty());
        assertTrue(hugeGap.display.isEmpty());
        assertEquals(WIDTH - 2 * GUTTER, hugeGap.clock.width());
    }

    private static void assertEqualCellWidths(Rect a, Rect b, Rect c) {
        int max = Math.max(a.width(), Math.max(b.width(), c.width()));
        int min = Math.min(a.width(), Math.min(b.width(), c.width()));
        assertTrue("cell widths must be equal within a 1px remainder", max - min <= 1);
    }

    /** Cells, given left to right in on-screen order, must exactly fill the usable span. */
    private static void assertCellsFillUsableWidth(Rect... cellsLeftToRight) {
        assertEquals(GUTTER, cellsLeftToRight[0].left);
        assertEquals(WIDTH - GUTTER, cellsLeftToRight[cellsLeftToRight.length - 1].right);
        for (int i = 1; i < cellsLeftToRight.length; i++) {
            assertEquals(GAP, cellsLeftToRight[i].left - cellsLeftToRight[i - 1].right);
        }
    }

    private static void assertMirrored(Rect ltr, Rect rtl) {
        if (ltr.isEmpty()) {
            assertTrue(rtl.isEmpty());
            return;
        }
        assertEquals(WIDTH - ltr.right, rtl.left);
        assertEquals(WIDTH - ltr.left, rtl.right);
        assertEquals(ltr.top, rtl.top);
        assertEquals(ltr.bottom, rtl.bottom);
    }

    private static void assertNoNegativeRects(TopPaneTileLayoutPolicy.Result r) {
        assertNoNegativeRect(r.clock);
        assertNoNegativeRect(r.widgets);
        assertNoNegativeRect(r.display);
    }

    private static void assertNoNegativeRect(Rect rect) {
        assertTrue(rect.left >= 0);
        assertTrue(rect.top >= 0);
        assertTrue(rect.right >= rect.left);
        assertTrue(rect.bottom >= rect.top);
    }
}
