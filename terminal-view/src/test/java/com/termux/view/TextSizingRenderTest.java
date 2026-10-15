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

    // --- Faces inside a block: symbol_map and fallback resolve per code point, as in plain cells ---

    /** Stands in for the renderer's resolution: a symbol map over the private-use area. */
    private static final Object SYMBOLS = new Object();
    private static final Object FALLBACK = new Object();
    private static final TerminalRenderer.BlockFaces FACES = (codePoint, bold, italic) -> {
        if (TerminalRenderer.isPrivateUse(codePoint)) return SYMBOLS;
        if (codePoint == 0x4E2D) return FALLBACK;
        return null;
    };

    /** The block text split into its face runs, as {@code start-end} pairs. */
    private static String runs(String text) {
        StringBuilder out = new StringBuilder();
        for (int start = 0, end; start < text.length(); start = end) {
            end = TerminalRenderer.blockRunEnd(text, start, false, false, FACES);
            if (out.length() > 0) out.append(' ');
            out.append(start).append('-').append(end);
        }
        return out.toString();
    }

    @Test
    public void latinTextInABlockIsOneRunOnThePrimaryFace() {
        assertEquals("0-5", runs("Hello"));
        assertEquals(null, FACES.faceOf('H', false, false));
    }

    @Test
    public void aPrivateUseIconInABlockGetsARunOfItsOwnOnTheSymbolFace() {
        // "A<U+F0C5>B": the Latin parts keep the primary face, the icon is split out for the map.
        assertEquals("0-1 1-2 2-3", runs("AB"));
        assertEquals(SYMBOLS, FACES.faceOf("AB".codePointAt(1), false, false));
        // A supplementary-plane icon (U+F0219) is one code point, two chars, one run.
        final String supplementary = "A" + new String(Character.toChars(0xF0219)) + "B";
        assertEquals("0-1 1-3 3-4", runs(supplementary));
        // Two icons side by side share the face, so they share the run.
        assertEquals("0-2", runs(""));
    }

    @Test
    public void aFallbackCodePointInABlockGetsARunOfItsOwn() {
        assertEquals("0-1 1-2 2-3", runs("A中B"));
    }

    @Test
    public void aCombiningMarkStaysWithTheCodePointBeforeIt() {
        // U+0301 resolves to the primary face on its own; inside the icon's run it is not a break.
        assertEquals("0-2 2-3", runs("́B"));
        assertEquals("0-3", runs("Aé"));
    }

    @Test
    public void aSymbolRunCoversItsCellsAtTheBlocksScale() {
        assertEquals(20f, TextBlockGeometry.symbolBoxWidth(1, FONT_WIDTH, 2f), EPSILON);
        assertEquals(40f, TextBlockGeometry.symbolBoxWidth(2, FONT_WIDTH, 2f), EPSILON);
        assertEquals(10f, TextBlockGeometry.symbolBoxWidth(1, FONT_WIDTH, 1f), EPSILON);
        assertEquals(3, TerminalRenderer.blockRunColumns("A́B", 0, 4));
    }

    @Test
    public void anIconInADoubleSizeBlockIsCentredInItsTwoByTwoCells() {
        // s=2, one wcwidth column: the icon owns a 20 x 48 box (two cells by two rows).
        final float left = 30f;
        final float boxTop = 76f;
        final float boxWidth = TextBlockGeometry.symbolBoxWidth(1, FONT_WIDTH, 2f);
        final float boxHeight = TextBlockGeometry.boxHeight(LINE_SPACING, 2f);
        // A wide symbol face at the drawn size: 30 wide, line box 60 (ascent -48, descent 12).
        float advance = 30f;
        float ascent = -48f;
        float descent = 12f;
        final float fit = TextBlockGeometry.symbolFitScale(advance, descent - ascent, boxWidth,
            boxHeight);
        assertEquals(20f / 30f, fit, EPSILON);
        advance *= fit;
        ascent *= fit;
        descent *= fit;
        final float glyphLeft = TextBlockGeometry.centredLeft(left, boxWidth, advance);
        final float baseline = TextBlockGeometry.centredBaseline(boxTop, boxHeight,
            descent - ascent, ascent);
        // Centred horizontally in the two cells and vertically in the two rows.
        assertEquals(left + boxWidth / 2f, glyphLeft + advance / 2f, EPSILON);
        assertEquals(boxTop + boxHeight / 2f, ((baseline + ascent) + (baseline + descent)) / 2f,
            EPSILON);
        // And it stays inside them.
        assertTrue(glyphLeft >= left - EPSILON);
        assertTrue(glyphLeft + advance <= left + boxWidth + EPSILON);
        assertTrue(baseline + ascent >= boxTop - EPSILON);
        assertTrue(baseline + descent <= boxTop + boxHeight + EPSILON);
    }

    @Test
    public void aTallIconIsFitByHeightAndStillCentredAcross() {
        final float boxWidth = TextBlockGeometry.symbolBoxWidth(1, FONT_WIDTH, 2f);
        final float boxHeight = TextBlockGeometry.boxHeight(LINE_SPACING, 2f);
        final float fit = TextBlockGeometry.symbolFitScale(10f, 60f, boxWidth, boxHeight);
        assertEquals(0.8f, fit, EPSILON);
        assertEquals(36f, TextBlockGeometry.centredLeft(30f, boxWidth, 10f * fit), EPSILON);
        // A face that measured nothing keeps its size rather than dividing by zero.
        assertEquals(1f, TextBlockGeometry.symbolFitScale(0f, 0f, boxWidth, boxHeight), EPSILON);
    }

    // --- Private-use expansion inside a block: an icon spreads across the blanks after it ---

    private static final String ICON = new String(Character.toChars(0xF0219));
    private static final String PUA = "\uE0B0";

    /**
     * The block text split into its face runs as the renderer splits it, with a symbol run spread
     * across the blanks after it, as {@code start-end} pairs. Every icon measures {@code advance}
     * at the drawn size, against cells {@code cellWidth} wide at that size.
     */
    private static String expandedRuns(String text, float advance, float cellWidth, int cap) {
        StringBuilder out = new StringBuilder();
        for (int start = 0, end; start < text.length(); start = end) {
            end = TerminalRenderer.blockRunEnd(text, start, false, false, FACES);
            if (FACES.faceOf(text.codePointAt(start), false, false) == SYMBOLS)
                end = TerminalRenderer.blockExpansionEnd(text, start, end, advance, cellWidth,
                    cap);
            if (out.length() > 0) out.append(' ');
            out.append(start).append('-').append(end);
        }
        return out.toString();
    }

    @Test
    public void aWideIconInABlockAbsorbsTheBlankAfterItAndOwnsTwoColumns() {
        // s=2: a cell is 20 wide at the drawn size, the icon's own glyph is 34 wide, so it wants
        // two cells, as it does at normal size (17 against 10). The space after it is absorbed.
        final String text = ICON + " " + ICON;
        assertEquals("0-3 3-5", expandedRuns(text, 34f, FONT_WIDTH * 2f, Integer.MAX_VALUE));
        assertEquals(2, TerminalRenderer.blockRunColumns(text, 0, 3));
        // So its box is twice the one-column box, four cells of the terminal wide at s=2.
        assertEquals(40f, TextBlockGeometry.symbolBoxWidth(
            TerminalRenderer.blockRunColumns(text, 0, 3), FONT_WIDTH, 2f), EPSILON);
        // The last icon has no blank after it and keeps its one column.
        assertEquals(1, TerminalRenderer.blockRunColumns(text, 3, 5));
    }

    @Test
    public void anIconTakesNoMoreBlanksThanItWants() {
        // Two cells wanted, three blanks offered: one is absorbed, the other two stay text.
        assertEquals("0-2 2-4", expandedRuns(PUA + "   ", 30f, 20f, Integer.MAX_VALUE));
        // Three cells wanted: two blanks are absorbed.
        assertEquals("0-3 3-4", expandedRuns(PUA + "   ", 50f, 20f, Integer.MAX_VALUE));
        // An en space is a blank too.
        assertEquals("0-2", expandedRuns(PUA + "\u2002", 30f, 20f, Integer.MAX_VALUE));
    }

    @Test
    public void anIconFollowedByALetterDoesNotExpand() {
        assertEquals("0-1 1-2", expandedRuns(PUA + "B", 34f, 20f, Integer.MAX_VALUE));
    }

    @Test
    public void aNarrowIconDoesNotExpand() {
        // Its glyph fits one cell at the drawn size, so the blank stays a blank.
        assertEquals("0-1 1-2", expandedRuns(PUA + " ", 18f, 20f, Integer.MAX_VALUE));
        assertEquals("0-1 1-2", expandedRuns(PUA + " ", 20f, 20f, Integer.MAX_VALUE));
    }

    @Test
    public void aNarrowSymbolCapLimitsTheExpansion() {
        final TerminalRenderer.SymbolExpansion caps = new TerminalRenderer.SymbolExpansion(
            new int[] {0xE000}, new int[] {0xF8FF}, new int[] {1});
        final int cap = caps.maxColumnsFor(0xE0B0);
        assertEquals("0-1 1-2", expandedRuns(PUA + " ", 34f, 20f, cap));
        // A cap of two holds an icon that wants three to one absorbed blank.
        final int two = new TerminalRenderer.SymbolExpansion(
            new int[] {0xE000}, new int[] {0xF8FF}, new int[] {2}).maxColumnsFor(0xE0B0);
        assertEquals("0-2 2-3", expandedRuns(PUA + "  ", 50f, 20f, two));
    }

    @Test
    public void iconsSideBySideKeepTheirOwnColumns() {
        // A run of two icons is not one code point, so it is not spread, even with a blank after.
        assertEquals("0-2 2-3", expandedRuns(PUA + PUA + " ", 34f, 20f, Integer.MAX_VALUE));
    }

    /**
     * The contract dawn's ribbon relies on: {@code ESC ] 66 ; s=2:w=2 ; <icon><space> BEL} is one
     * block, and its icon spreads over both columns, centred, as tall as a letter of the block.
     * With w=0 every character is a block of its own, so only an explicit width gets this.
     */
    @Test
    public void anExplicitWidthBlockOfIconAndSpaceDrawsTheIconFullSize() {
        final String text = ICON + " ";
        // A wide symbol face at the drawn size: 30 wide against 20-wide cells, line box 60.
        float advance = 30f;
        float ascent = -48f;
        float descent = 12f;
        // The icon absorbs the space: one run of two columns, a 40 x 48 box at s=2.
        assertEquals("0-3", expandedRuns(text, advance, FONT_WIDTH * 2f, Integer.MAX_VALUE));
        final float boxWidth = TextBlockGeometry.symbolBoxWidth(
            TerminalRenderer.blockRunColumns(text, 0, 3), FONT_WIDTH, 2f);
        final float boxHeight = TextBlockGeometry.boxHeight(LINE_SPACING, 2f);
        assertEquals(40f, boxWidth, EPSILON);
        // Fit by height now, not width: the icon's line box fills both rows, a letter's height.
        final float fit = TextBlockGeometry.symbolFitScale(advance, descent - ascent, boxWidth,
            boxHeight);
        assertEquals(0.8f, fit, EPSILON);
        advance *= fit;
        ascent *= fit;
        descent *= fit;
        assertEquals(2f * LINE_SPACING, descent - ascent, EPSILON);
        // Wider than the same icon alone in one column, which is held to 20 by its width.
        final float aloneFit = TextBlockGeometry.symbolFitScale(30f, 60f,
            TextBlockGeometry.symbolBoxWidth(1, FONT_WIDTH, 2f), boxHeight);
        assertTrue(advance > 30f * aloneFit + 1f);
        // Centred over both columns and both rows.
        final float left = 30f;
        final float boxTop = 76f;
        final float glyphLeft = TextBlockGeometry.centredLeft(left, boxWidth, advance);
        final float baseline = TextBlockGeometry.centredBaseline(boxTop, boxHeight,
            descent - ascent, ascent);
        assertEquals(left + boxWidth / 2f, glyphLeft + advance / 2f, EPSILON);
        assertEquals(boxTop + boxHeight / 2f, ((baseline + ascent) + (baseline + descent)) / 2f,
            EPSILON);
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
