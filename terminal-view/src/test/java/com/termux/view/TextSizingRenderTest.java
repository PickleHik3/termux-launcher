package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.terminal.KittyTextSizing;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextSizeFixtures;

import org.junit.Test;

/**
 * Where a kitty text sizing block lands and how big its text is drawn.
 *
 * <p>These are the numbers a screenshot would be read for: a block two rows tall really is two
 * rows tall, an {@code n/d} fraction really does shrink the text inside the block it keeps, and an
 * alignment really moves it to the edge it names. Getting one of them wrong is a heading drawn
 * over the line below it, which no other test in this module would notice.
 */
public class TextSizingRenderTest {

    private static final float FONT_WIDTH = 10f;
    private static final int LINE_SPACING = 24;
    private static final int BASELINE_DESCENT = 5;
    private static final float EPSILON = 0.001f;

    @Test
    public void aPlainBlockIsDrawnAtItsScale() {
        assertEquals(1f, TextBlockGeometry.sizeScale(1, 0, 0, false), EPSILON);
        assertEquals(2f, TextBlockGeometry.sizeScale(2, 0, 0, false), EPSILON);
        assertEquals(7f, TextBlockGeometry.sizeScale(7, 0, 0, false), EPSILON);
    }

    @Test
    public void aFractionCutsTheDrawnSizeButNotTheBlock() {
        // s=2 with n/d = 1/2: the block still covers two rows, the text is drawn at normal size.
        assertEquals(1f, TextBlockGeometry.sizeScale(2, 1, 2, false), EPSILON);
        assertEquals(1.5f, TextBlockGeometry.sizeScale(3, 1, 2, false), EPSILON);
        // The block itself keeps the height its scale bought it.
        assertEquals(2f * LINE_SPACING, TextBlockGeometry.height(LINE_SPACING, 2), EPSILON);
    }

    @Test
    public void aDemotedBlockIsDrawnAtNormalSize() {
        assertEquals(1f, TextBlockGeometry.sizeScale(4, 0, 0, true), EPSILON);
        assertEquals(1f, TextBlockGeometry.sizeScale(4, 1, 2, true), EPSILON);
    }

    @Test
    public void aBlockCoversItsOwnRowsAndColumns() {
        final float left = TextBlockGeometry.left(0f, FONT_WIDTH, 3);
        final float width = TextBlockGeometry.width(FONT_WIDTH, 4);
        assertEquals(30f, left, EPSILON);
        assertEquals(40f, width, EPSILON);

        // Drawn from the anchor's own row, whose bottom edge is at 100.
        final float top = TextBlockGeometry.top(100f, LINE_SPACING, 0);
        assertEquals(76f, top, EPSILON);
        assertEquals(48f, TextBlockGeometry.height(LINE_SPACING, 2), EPSILON);
    }

    @Test
    public void aBlockDrawnFromALaterRowStartsAtItsAnchor() {
        // Same block, drawn from its second row (whose bottom edge is one line further down): the
        // top has to come out the same, or a block whose anchor scrolled off the top would slide.
        assertEquals(TextBlockGeometry.top(100f, LINE_SPACING, 0),
            TextBlockGeometry.top(124f, LINE_SPACING, 1), EPSILON);
    }

    @Test
    public void withoutAFractionEveryVerticalAlignmentAgrees() {
        final float blockTop = 76f;
        final float blockHeight = TextBlockGeometry.height(LINE_SPACING, 2);
        final float boxHeight = TextBlockGeometry.boxHeight(LINE_SPACING, 2f);
        assertEquals(blockHeight, boxHeight, EPSILON);
        for (int align = 0; align <= 2; align++)
            assertEquals(blockTop,
                TextBlockGeometry.alignedTop(blockTop, blockHeight, boxHeight, align), EPSILON);
    }

    @Test
    public void aFractionLeavesRoomForTheVerticalAlignment() {
        final float blockTop = 76f;
        final float blockHeight = TextBlockGeometry.height(LINE_SPACING, 2);
        final float boxHeight = TextBlockGeometry.boxHeight(LINE_SPACING,
            TextBlockGeometry.sizeScale(2, 1, 2, false));
        assertEquals(24f, boxHeight, EPSILON);
        assertEquals(blockTop, TextBlockGeometry.alignedTop(blockTop, blockHeight, boxHeight,
            KittyTextSizing.ALIGN_START), EPSILON);
        assertEquals(blockTop + 24f, TextBlockGeometry.alignedTop(blockTop, blockHeight, boxHeight,
            KittyTextSizing.ALIGN_END), EPSILON);
        assertEquals(blockTop + 12f, TextBlockGeometry.alignedTop(blockTop, blockHeight, boxHeight,
            KittyTextSizing.ALIGN_CENTRE), EPSILON);
    }

    @Test
    public void theHorizontalAlignmentMovesNarrowTextInsideItsBlock() {
        final float blockLeft = 30f;
        final float blockWidth = 40f;
        final float advance = 24f;
        assertEquals(30f, TextBlockGeometry.alignedLeft(blockLeft, blockWidth, advance,
            KittyTextSizing.ALIGN_START), EPSILON);
        assertEquals(46f, TextBlockGeometry.alignedLeft(blockLeft, blockWidth, advance,
            KittyTextSizing.ALIGN_END), EPSILON);
        assertEquals(38f, TextBlockGeometry.alignedLeft(blockLeft, blockWidth, advance,
            KittyTextSizing.ALIGN_CENTRE), EPSILON);
    }

    @Test
    public void textWiderThanItsBlockStaysAtTheLeftEdge() {
        // The clip cuts the tail rather than the head, whichever alignment was asked for.
        for (int align = 0; align <= 2; align++)
            assertEquals(30f, TextBlockGeometry.alignedLeft(30f, 40f, 90f, align), EPSILON);
    }

    @Test
    public void aBlockAtScaleOneSitsOnTheSameBaselineAsPlainText() {
        final float rowBottom = 100f;
        final float top = TextBlockGeometry.top(rowBottom, LINE_SPACING, 0);
        assertEquals(rowBottom - BASELINE_DESCENT,
            TextBlockGeometry.baseline(top, LINE_SPACING, BASELINE_DESCENT, 1f), EPSILON);
    }

    @Test
    public void theBaselineFollowsTheDrawnSize() {
        final float top = TextBlockGeometry.top(100f, LINE_SPACING, 0);
        assertEquals(top + 2f * (LINE_SPACING - BASELINE_DESCENT),
            TextBlockGeometry.baseline(top, LINE_SPACING, BASELINE_DESCENT, 2f), EPSILON);
    }

    @Test
    public void aBlockCursorFillsEveryRowOfTheBlock() {
        final float[] rect = new float[4];
        assertTrue(TextBlockGeometry.cursorRect(TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK,
            30f, 40f, FONT_WIDTH / 4f, 76f, 100f, LINE_SPACING, false, rect));
        assertEquals(30f, rect[0], EPSILON);
        assertEquals(76f, rect[1], EPSILON);
        assertEquals(70f, rect[2], EPSILON);
        assertEquals(100f, rect[3], EPSILON);

        assertTrue(TextBlockGeometry.cursorRect(TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK,
            30f, 40f, FONT_WIDTH / 4f, 100f, 124f, LINE_SPACING, true, rect));
        assertEquals(100f, rect[1], EPSILON);
        assertEquals(124f, rect[3], EPSILON);
    }

    @Test
    public void aBarCursorStandsAtTheBlocksLeftEdgeDownItsWholeHeight() {
        final float[] rect = new float[4];
        assertTrue(TextBlockGeometry.cursorRect(TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR,
            30f, 40f, FONT_WIDTH / 4f, 76f, 100f, LINE_SPACING, false, rect));
        assertEquals(30f, rect[0], EPSILON);
        assertEquals(32.5f, rect[2], EPSILON);
        assertEquals(76f, rect[1], EPSILON);
        assertEquals(100f, rect[3], EPSILON);
    }

    @Test
    public void anUnderlineCursorIsDrawnOnlyAlongTheBlocksBottom() {
        final float[] rect = new float[4];
        assertFalse(TextBlockGeometry.cursorRect(TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE,
            30f, 40f, FONT_WIDTH / 4f, 76f, 100f, LINE_SPACING, false, rect));
        assertTrue(TextBlockGeometry.cursorRect(TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE,
            30f, 40f, FONT_WIDTH / 4f, 100f, 124f, LINE_SPACING, true, rect));
        assertEquals(30f, rect[0], EPSILON);
        assertEquals(124f - LINE_SPACING / 4f, rect[1], EPSILON);
        assertEquals(70f, rect[2], EPSILON);
        assertEquals(124f, rect[3], EPSILON);
    }

    @Test
    public void aPlainCellsCursorIsUnchanged() {
        // One cell, the bar a quarter of it: exactly what the renderer drew before blocks existed.
        final float[] rect = new float[4];
        assertTrue(TextBlockGeometry.cursorRect(TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR,
            30f, FONT_WIDTH, FONT_WIDTH / 4f, 76f, 100f, LINE_SPACING, true, rect));
        assertEquals(30f, rect[0], EPSILON);
        assertEquals(32.5f, rect[2], EPSILON);
    }

    // --- D5: the selection covers a block's anchor row, the fill covers all of its rows ---

    private static final int BLOCK_ANCHOR_ROW = 4;

    /** Two rows, standing in for the block's anchor row and the row under it. */
    private static TerminalRow[] blockRows() {
        TerminalRow[] rows = { new TerminalRow(8, 0L), new TerminalRow(8, 0L) };
        // Two rows tall, two columns wide, at column 0.
        TextSizeFixtures.markBlock(rows, 0, 0, TextSizeFixtures.record(2, 1));
        return rows;
    }

    @Test
    public void theFillOfASelectedBlockCoversEveryRowItSpans() {
        final TerminalRow[] rows = blockRows();
        // The selection the snap produces for a press on this block: its run on the anchor row.
        final int y1 = BLOCK_ANCHOR_ROW, y2 = BLOCK_ANCHOR_ROW, x1 = 0, x2 = 1;

        for (int column = 0; column < 2; column++) {
            assertTrue("anchor row, column " + column, TextBlockGeometry.cellSelected(rows[0],
                BLOCK_ANCHOR_ROW, column, y1, y2, x1, x2));
            assertTrue("the row under it, column " + column,
                TextBlockGeometry.cellSelected(rows[1], BLOCK_ANCHOR_ROW + 1, column,
                    y1, y2, x1, x2));
        }
    }

    @Test
    public void nothingBesideTheBlockIsFilled() {
        final TerminalRow[] rows = blockRows();
        final int y1 = BLOCK_ANCHOR_ROW, y2 = BLOCK_ANCHOR_ROW, x1 = 0, x2 = 1;

        // The selection stops at the block's last column on its own row, and the row under it is
        // outside the selection altogether — only the block's own cells are filled there.
        assertFalse(TextBlockGeometry.cellSelected(rows[0], BLOCK_ANCHOR_ROW, 5, y1, y2, x1, x2));
        assertFalse(TextBlockGeometry.cellSelected(rows[1], BLOCK_ANCHOR_ROW + 1, 5,
            y1, y2, x1, x2));
    }

    @Test
    public void anUnselectedBlockIsNotFilledOnAnyOfItsRows() {
        final TerminalRow[] rows = blockRows();
        // A selection further down the screen, nowhere near the block.
        final int y1 = 8, y2 = 8, x1 = 0, x2 = 3;

        for (int column = 0; column < 2; column++) {
            assertFalse(TextBlockGeometry.cellSelected(rows[0], BLOCK_ANCHOR_ROW, column,
                y1, y2, x1, x2));
            assertFalse(TextBlockGeometry.cellSelected(rows[1], BLOCK_ANCHOR_ROW + 1, column,
                y1, y2, x1, x2));
        }
    }

    @Test
    public void aPlainRowFollowsTheOrdinaryStreamSelection() {
        final TerminalRow plain = new TerminalRow(8, 0L);
        // Rows 2..4, from column 5 of the first to column 3 of the last.
        assertFalse(TextBlockGeometry.cellSelected(plain, 2, 4, 2, 4, 5, 3));
        assertTrue(TextBlockGeometry.cellSelected(plain, 2, 5, 2, 4, 5, 3));
        assertTrue(TextBlockGeometry.cellSelected(plain, 3, 0, 2, 4, 5, 3));
        assertTrue(TextBlockGeometry.cellSelected(plain, 3, 7, 2, 4, 5, 3));
        assertTrue(TextBlockGeometry.cellSelected(plain, 4, 3, 2, 4, 5, 3));
        assertFalse(TextBlockGeometry.cellSelected(plain, 4, 4, 2, 4, 5, 3));
        assertFalse(TextBlockGeometry.cellSelected(plain, 5, 0, 2, 4, 5, 3));
    }

    @Test
    public void noSelectionCoversNothing() {
        final TerminalRow[] rows = blockRows();
        // What TerminalView passes when nothing is selected.
        assertFalse(TextBlockGeometry.cellSelected(rows[0], BLOCK_ANCHOR_ROW, 0, -1, -1, -1, -1));
        assertFalse(TextBlockGeometry.cellSelected(rows[1], BLOCK_ANCHOR_ROW + 1, 0,
            -1, -1, -1, -1));
        assertFalse(TextBlockGeometry.selectionCovers(-1, 0, -1, -1, -1, -1));
    }
}
