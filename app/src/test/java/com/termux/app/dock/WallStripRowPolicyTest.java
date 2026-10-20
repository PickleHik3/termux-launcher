package com.termux.app.dock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/**
 * The Wall strip's first row by terminal count (spec table): the clock's share, equal tiles with
 * {@code +} among them, the clock gone from three, narrower at four, scrolling past four at the
 * four-terminal width, and the row continuous through the clock's slide.
 */
public class WallStripRowPolicyTest {

    private static final float EPS = 0.01f;
    /** A 411 dp row at density 1: 14 dp inset each side leaves 383 for the content. */
    private static final float W = 411f;
    private static final float INSET = 14f;
    private static final float GAP = 10f;
    private static final float CONTENT = W - 2f * INSET;

    private static WallStripRowPolicy.Row row(int terminals) {
        return WallStripRowPolicy.layout(W, INSET, GAP, terminals, 28f, 12f);
    }

    /** The last tile's right edge, before scrolling. */
    private static float contentRight(WallStripRowPolicy.Row row) {
        return row.tileLeft(row.addTileIndex()) + row.tileWidthPx;
    }

    @Test
    public void none_clockTakesAbout56Percent_andPlusFillsTheRest() {
        WallStripRowPolicy.Row row = row(0);
        assertEquals(1, row.tileCount());
        assertTrue(row.showsClock());
        assertEquals(0.56f, (row.clockWidthPx + GAP / 2f) / CONTENT, 0.02f);
        // The clock, one ordinary gap, then the + tile to the row's end.
        assertEquals(INSET + row.clockWidthPx + GAP, row.tileLeft(0), EPS);
        assertEquals(W - INSET, contentRight(row), EPS);
        assertFalse(row.scrolls());
    }

    @Test
    public void one_terminalAndPlusAreTwoEqualTiles() {
        WallStripRowPolicy.Row row = row(1);
        assertEquals(2, row.tileCount());
        assertEquals(row(0).clockWidthPx, row.clockWidthPx, EPS);
        assertEquals(row.tileLeft(0) + row.tileWidthPx + GAP, row.tileLeft(1), EPS);
        assertEquals(W - INSET, contentRight(row), EPS);
    }

    @Test
    public void two_clockAndTerminalAreaHalfAndHalf() {
        WallStripRowPolicy.Row row = row(2);
        assertEquals(3, row.tileCount());
        float area = W - INSET - row.tilesLeftPx;
        assertEquals(row.clockWidthPx, area, EPS);
        assertEquals(W - INSET, contentRight(row), EPS);
    }

    @Test
    public void three_clockSlidesOut_andFourTilesSplitTheRow() {
        WallStripRowPolicy.Row row = row(3);
        assertFalse(row.showsClock());
        assertEquals(4, row.tileCount());
        assertEquals(INSET, row.tileLeft(0), EPS);
        assertEquals((CONTENT - 3f * GAP) / 4f, row.tileWidthPx, EPS);
        assertEquals(W - INSET, contentRight(row), EPS);
    }

    @Test
    public void four_fiveEqualTiles() {
        WallStripRowPolicy.Row row = row(4);
        assertEquals(5, row.tileCount());
        assertEquals((CONTENT - 4f * GAP) / 5f, row.tileWidthPx, EPS);
        assertFalse(row.scrolls());
    }

    @Test
    public void fiveAndMore_keepTheFourTerminalWidth_andScroll_plusLast() {
        float four = row(4).tileWidthPx;
        for (int n = 5; n <= 9; n++) {
            WallStripRowPolicy.Row row = row(n);
            assertEquals(n + 1, row.tileCount());
            assertEquals(four, row.tileWidthPx, EPS);
            assertTrue(row.scrolls());
            assertEquals(contentRight(row) - (W - INSET), row.maxScrollPx, EPS);
            assertEquals(n, row.addTileIndex());
        }
    }

    @Test
    public void plusTile_isAlwaysATerminalTilesSize() {
        for (int n = 0; n <= 7; n++) {
            WallStripRowPolicy.Row row = row(n);
            for (int i = 1; i < row.tileCount(); i++) {
                assertEquals("n=" + n, row.tileLeft(i - 1) + row.tileWidthPx + GAP,
                    row.tileLeft(i), EPS);
            }
        }
    }

    @Test
    public void clockSlide_isContinuousToNoClock() {
        WallStripRowPolicy.Row rest = row(3);
        WallStripRowPolicy.Row nearlyOut = WallStripRowPolicy.layout(W, INSET, GAP, 3, 28f, 12f,
            0.0001f);
        assertEquals(rest.tileLeft(0), nearlyOut.tileLeft(0), 0.1f);
        assertEquals(rest.tileWidthPx, nearlyOut.tileWidthPx, 0.1f);
        assertEquals(0f, nearlyOut.clockWidthPx, EPS);
        // Halfway through the slide the tiles still end at the row's end.
        WallStripRowPolicy.Row half = WallStripRowPolicy.layout(W, INSET, GAP, 3, 28f, 12f, 0.25f);
        assertEquals(W - INSET, contentRight(half), EPS);
    }

    @Test
    public void slidesClock_onlyAcrossTwoAndThree() {
        assertTrue(WallStripRowPolicy.slidesClock(2, 3));
        assertTrue(WallStripRowPolicy.slidesClock(3, 2));
        assertTrue(WallStripRowPolicy.slidesClock(0, 5));
        assertFalse(WallStripRowPolicy.slidesClock(1, 2));
        assertFalse(WallStripRowPolicy.slidesClock(3, 4));
        assertFalse(WallStripRowPolicy.slidesClock(0, 1));
    }

    @Test
    public void tileRadius_isTheDockRadiusLessItsPadding() {
        assertEquals(16f, row(2).tileRadiusPx, EPS);
        assertEquals(0f, WallStripRowPolicy.tileRadiusPx(8f, 12f), EPS);
        assertEquals(20f, WallStripRowPolicy.tileRadiusPx(20f, -3f), EPS);
    }

    @Test
    public void tileAt_findsTilesThroughTheScroll_andMissesTheClock() {
        WallStripRowPolicy.Row row = row(1);
        assertEquals(-1, row.tileAt(INSET + 5f, 0f));
        assertEquals(0, row.tileAt(row.tileLeft(0) + 1f, 0f));
        assertEquals(1, row.tileAt(row.tileLeft(1) + 1f, 0f));
        WallStripRowPolicy.Row scrolling = row(6);
        float scroll = scrolling.maxScrollPx;
        assertEquals(scrolling.addTileIndex(),
            scrolling.tileAt(scrolling.tileLeft(scrolling.addTileIndex()) - scroll + 1f, scroll));
    }

    @Test
    public void scrollToShow_bringsATileIntoView_heldToTheRange() {
        WallStripRowPolicy.Row row = row(7);
        float toLast = WallStripRowPolicy.scrollToShow(row, row.addTileIndex(), 0f, INSET, W);
        assertEquals(row.maxScrollPx, toLast, EPS);
        assertEquals(0f, WallStripRowPolicy.scrollToShow(row, 0, row.maxScrollPx, INSET, W), EPS);
        assertEquals(30f, WallStripRowPolicy.scrollToShow(row, 2, 30f, INSET, W), EPS);
    }

    @Test
    public void degenerateWidths_neverGoNegative() {
        WallStripRowPolicy.Row row = WallStripRowPolicy.layout(10f, 14f, 10f, 3, 28f, 12f);
        assertEquals(0f, row.tileWidthPx, EPS);
        assertEquals(0f, row.clockWidthPx, EPS);
    }

    @Test
    public void splitShape_normalisesPaneCells() {
        float[] shape = WallStripRowPolicy.splitShape(Arrays.asList(
            new int[]{0, 0, 40, 30}, null, new int[]{40, 0, 40, 30}));
        assertArrayEquals(new float[]{0f, 0f, 0.5f, 1f, 0.5f, 0f, 1f, 1f}, shape, EPS);
    }

    @Test
    public void splitShape_withNothingSized_isOneWholePane() {
        assertArrayEquals(new float[]{0f, 0f, 1f, 1f}, WallStripRowPolicy.splitShape(null), EPS);
        assertArrayEquals(new float[]{0f, 0f, 1f, 1f},
            WallStripRowPolicy.splitShape(Arrays.asList(new int[]{0, 0, 0, 0})), EPS);
    }
}
