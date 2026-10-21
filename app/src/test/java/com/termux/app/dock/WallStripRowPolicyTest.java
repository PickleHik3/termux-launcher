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

    // ---------------------------------------------------------------- a tile's title

    /** The row's height, the tile's padding, the gap over the title and the least shape, in dp. */
    private static final float TILE_H = 84f;
    private static final float PAD = 7f;
    private static final float TITLE_GAP = 5f;
    private static final float MIN_SHAPE = 8f;
    /** One terminal beside the clock at 411 dp: the tile's width. */
    private static final float ONE_TERMINAL_TILE = 76.5f;

    /**
     * A tile's title for monospace text {@code charDp} wide a character (12 sp: 7.2 dp at font
     * scale 1, 10.8 at 1.5): {@code number} and the label's character counts, a float's mark of
     * 9 dp, gaps of 5 dp, and a glyph a character wide.
     */
    private static WallStripRowPolicy.TileText title(float tileWidth, float charDp, int number,
                                                    boolean floating, boolean glyph,
                                                    int labelChars) {
        float line = charDp * 1.17f / 0.6f;
        float head = number * charDp + 5f + (floating ? 9f + 5f : 0f);
        return WallStripRowPolicy.tileText(tileWidth, TILE_H, PAD, line, TITLE_GAP, head,
            glyph ? charDp + 5f : 0f, labelChars * charDp, 4f * charDp, MIN_SHAPE);
    }

    @Test
    public void title_atFontScaleOne_isOneLine_withItsGlyph() {
        WallStripRowPolicy.TileText text = title(ONE_TERMINAL_TILE, 7.2f, 1, false, true, 4);
        assertFalse(text.twoLines);
        assertTrue(text.showsGlyph);
        assertTrue(text.labelRoomPx >= 4f * 7.2f);
        assertTrue(text.shapeHeightPx > 0f);
    }

    @Test
    public void title_theGlyphGoesBeforeTheLabelDoes() {
        // Five characters fit beside the number, not with the glyph too.
        float tile = 2f * PAD + 7.2f + 5f + 5f * 7.2f + 1f;
        WallStripRowPolicy.TileText text = title(tile, 7.2f, 1, false, true, 5);
        assertFalse(text.twoLines);
        assertFalse(text.showsGlyph);
        assertTrue(text.labelRoomPx >= 5f * 7.2f);
    }

    @Test
    public void title_aLongLabel_atFontScaleOne_isCutOnItsLine_theShapeKept() {
        WallStripRowPolicy.TileText text = title(ONE_TERMINAL_TILE, 7.2f, 1, false, true, 20);
        assertFalse(text.twoLines);
        assertTrue(text.labelRoomPx >= 4f * 7.2f);
        float oneLineShape = TILE_H - 2f * PAD - 7.2f * 1.17f / 0.6f - TITLE_GAP;
        assertEquals(oneLineShape, text.shapeHeightPx, EPS);
    }

    @Test
    public void title_atFontScaleOneAndAHalf_takesASecondLine_andTheShapeShrinks() {
        // A float's tile at 1.5: number, mark and the label do not share a line.
        float charDp = 10.8f;
        WallStripRowPolicy.TileText text = title(ONE_TERMINAL_TILE, charDp, 1, true, true, 8);
        assertTrue(text.twoLines);
        assertEquals(ONE_TERMINAL_TILE - 2f * PAD, text.labelRoomPx, EPS);
        assertTrue(text.labelRoomPx >= 4f * charDp);
        float line = charDp * 1.17f / 0.6f;
        assertEquals(TILE_H - 2f * PAD - 2f * line - TITLE_GAP, text.shapeHeightPx, EPS);
        assertTrue(text.shapeHeightPx > 0f);
        // The two lines and the shape stay inside the tile, nothing over another.
        assertTrue(PAD + text.shapeHeightPx + TITLE_GAP + 2f * line + PAD <= TILE_H + EPS);
    }

    @Test
    public void title_atFontScaleOneAndAHalf_fourTerminals_keepsNumberAndTitle() {
        // Four terminals: five equal tiles in 383 dp with four 10 dp gaps.
        float tile = (CONTENT - 4f * GAP) / 5f;
        WallStripRowPolicy.TileText text = title(tile, 10.8f, 2, false, true, 6);
        assertTrue(text.twoLines);
        assertTrue(text.labelRoomPx >= 4f * 10.8f);
    }

    @Test
    public void title_theShapeIsLeftOut_beforeTheTitle() {
        // Past font scale 2: two lines leave less than the least shape.
        float charDp = 15f;
        WallStripRowPolicy.TileText text = title(ONE_TERMINAL_TILE, charDp, 1, true, false, 8);
        assertTrue(text.twoLines);
        assertEquals(0f, text.shapeHeightPx, 0f);
        assertTrue(text.labelRoomPx > 0f);
    }

    @Test
    public void title_noRoomForTwoLines_staysOneLine_withTheNumber() {
        WallStripRowPolicy.TileText text = WallStripRowPolicy.tileText(40f, 30f, 7f, 20f, 5f,
            30f, 10f, 60f, 40f, 8f);
        assertFalse(text.twoLines);
        assertFalse(text.showsGlyph);
        assertEquals(0f, text.shapeHeightPx, 0f);
    }

    @Test
    public void title_noLabel_isTheNumberAndGlyph() {
        WallStripRowPolicy.TileText text = title(ONE_TERMINAL_TILE, 7.2f, 1, false, true, 0);
        assertFalse(text.twoLines);
        assertTrue(text.showsGlyph);
        assertEquals(0f, text.labelRoomPx, 0f);
    }

    @Test
    public void sideInset_followsTheFirstIcon_whenItStandsInsideTheSlab() {
        // A Floating slab: the icons stand well inside it, and the clock lines up with them.
        assertEquals(60f, WallStripRowPolicy.sideInsetPx(60f, 50f), EPS);
    }

    @Test
    public void sideInset_neverCloserToTheSlabsSideThanTheSpecs14dp() {
        // A Docked slab with no Margin lays its icons out to the screen's edges: the first icon
        // read about 5 px in put the clock on the edge and cut the + tile (2026-10-21 check).
        float slab = 14f * 2.75f;
        assertEquals(slab, WallStripRowPolicy.sideInsetPx(5f, slab), EPS);
        WallStripRowPolicy.Row row = WallStripRowPolicy.layout(1080f,
            WallStripRowPolicy.sideInsetPx(5f, slab), 27.5f, 1, 0f, 0f);
        assertTrue(row.tileLeft(row.tileCount() - 1) + row.tileWidthPx <= 1080f - slab + EPS);
        assertTrue(row.tilesLeftPx - row.clockWidthPx >= slab - EPS);
    }

    @Test
    public void sideInset_isTheSlabsWhileNoIconIsLaidOut() {
        assertEquals(38.5f, WallStripRowPolicy.sideInsetPx(Float.NaN, 38.5f), EPS);
        assertEquals(0f, WallStripRowPolicy.sideInsetPx(Float.NaN, -3f), EPS);
    }
}
