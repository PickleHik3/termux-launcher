package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.terminal.KittyTextSizing;
import com.termux.terminal.TerminalEmulator;

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
}
