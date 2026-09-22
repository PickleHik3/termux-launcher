package com.termux.terminal;

import java.util.List;

/** {@code OSC 66}, text drawn larger than one cell. */
public class KittyTextSizingTest extends TerminalTestCase {

	private TerminalBuffer screen() {
		return mTerminal.getScreen();
	}

	private TerminalBuffer.TextBlock blockAt(int row, int column) {
		return screen().getTextBlockAt(row, column);
	}

	private String textOfBlockAt(int row, int column) {
		TerminalBuffer.TextBlock block = blockAt(row, column);
		assertNotNull("No block at row=" + row + " column=" + column, block);
		return screen().getTextBlockText(block);
	}

	/** Assert a block's shape and where its text lives. */
	private void assertBlock(int row, int column, int anchorRow, int anchorColumn, int rows, int columns) {
		TerminalBuffer.TextBlock block = blockAt(row, column);
		assertNotNull("No block at row=" + row + " column=" + column, block);
		assertEquals("anchor row", anchorRow, block.row);
		assertEquals("anchor column", anchorColumn, block.column);
		assertEquals("rows", rows, block.rows);
		assertEquals("columns", columns, block.columns);
	}

	// --- The parser ---------------------------------------------------------------------

	public void testParseReadsEveryKey() {
		KittyTextSizing.Request request = KittyTextSizing.parse("s=3:w=2:n=1:d=2:v=2:h=1;Hi");
		assertNotNull(request);
		assertEquals(3, request.scale);
		assertEquals(2, request.width);
		assertEquals(1, request.numerator);
		assertEquals(2, request.denominator);
		assertEquals(KittyTextSizing.ALIGN_CENTRE, request.verticalAlign);
		assertEquals(KittyTextSizing.ALIGN_END, request.horizontalAlign);
		assertEquals("Hi", request.text);
		assertTrue(request.isFraction());
	}

	public void testParseClampsAndDefaults() {
		KittyTextSizing.Request request = KittyTextSizing.parse("s=99:w=42:n=99:d=99:v=9:h=9;x");
		assertNotNull(request);
		assertEquals(KittyTextSizing.MAX_SCALE, request.scale);
		assertEquals(KittyTextSizing.MAX_WIDTH, request.width);
		assertEquals(15, request.numerator);
		assertEquals(15, request.denominator);
		assertEquals(2, request.verticalAlign);
		assertEquals(2, request.horizontalAlign);

		KittyTextSizing.Request zero = KittyTextSizing.parse("s=0;x");
		assertNotNull(zero);
		assertEquals(1, zero.scale);

		KittyTextSizing.Request bare = KittyTextSizing.parse(";x");
		assertNotNull(bare);
		assertEquals(1, bare.scale);
		assertEquals(0, bare.width);
		assertEquals(KittyTextSizing.ALIGN_START, bare.verticalAlign);
	}

	public void testParseIgnoresUnknownKeysAndKeepsSemicolonsInTheText() {
		KittyTextSizing.Request request = KittyTextSizing.parse("s=2:zz=4:q=1;a;b");
		assertNotNull(request);
		assertEquals(2, request.scale);
		assertEquals("a;b", request.text);
		assertNull(KittyTextSizing.parse("s=2;"));
	}

	public void testLongTextIsCutToTheProtocolsLimit() {
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 5000; i++) text.append('a');
		KittyTextSizing.Request request = KittyTextSizing.parse("s=1;" + text);
		assertNotNull(request);
		assertEquals(KittyTextSizing.MAX_PAYLOAD_BYTES, request.text.length());
	}

	public void testGraphemeSplitKeepsMarksWithTheirBase() {
		List<String> graphemes = KittyTextSizing.splitGraphemes("ábc");
		assertEquals(3, graphemes.size());
		assertEquals("á", graphemes.get(0));
		assertEquals("b", graphemes.get(1));
	}

	// --- Writing a block ----------------------------------------------------------------

	/** Without a width every character is a block of its own, which is how a heading arrives. */
	public void testWidthZeroGivesEveryCharacterItsOwnBlock() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2;Hi\033\\");
		assertBlock(0, 0, 0, 0, 2, 2);
		assertBlock(1, 1, 0, 0, 2, 2);
		assertBlock(0, 2, 0, 2, 2, 2);
		assertEquals("H", textOfBlockAt(0, 0));
		assertEquals("i", textOfBlockAt(0, 2));
		assertCursorAt(0, 4);
	}

	/** With a width the whole text is one block, and the cursor moves s*w cells. */
	public void testForcedWidthMakesOneBlock() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=3;abc\033\\");
		assertBlock(0, 0, 0, 0, 2, 6);
		assertBlock(1, 5, 0, 0, 2, 6);
		assertEquals("abc", textOfBlockAt(0, 0));
		assertCursorAt(0, 6);
		assertNull("beyond the block", blockAt(0, 6));
	}

	/** Dawn asks whether the terminal knows the protocol by looking at where the cursor ends up. */
	public void testDawnsProbeMovesTheCursorTwoCells() {
		withTerminalSized(20, 6);
		enterString("\033]66;w=2;x\033\\");
		assertCursorAt(0, 2);
		assertBlock(0, 1, 0, 0, 1, 2);
	}

	/** Text wider than the cells the program asked for is cut, not squeezed. */
	public void testTextWiderThanTheForcedWidthIsCut() {
		withTerminalSized(20, 6);
		enterString("\033]66;w=2;abcdef\033\\");
		assertEquals("ab", textOfBlockAt(0, 0));
		assertCursorAt(0, 2);
	}

	public void testScaleIsCappedAtSeven() {
		withTerminalSized(60, 10);
		enterString("\033]66;s=9:w=1;x\033\\");
		TerminalBuffer.TextBlock block = blockAt(0, 0);
		assertNotNull(block);
		assertEquals(7, block.scale);
		assertEquals(7, block.rows);
		assertEquals(7, block.columns);
	}

	public void testFractionAndAlignmentSurviveOnEveryCell() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=2:n=1:d=2:v=1:h=2;Hi\033\\");
		TerminalBuffer.TextBlock block = blockAt(1, 3);
		assertNotNull(block);
		assertEquals(1, block.fractionNumerator);
		assertEquals(2, block.fractionDenominator);
		assertEquals(KittyTextSizing.ALIGN_END, block.verticalAlign);
		assertEquals(KittyTextSizing.ALIGN_CENTRE, block.horizontalAlign);
	}

	/** A block that does not fit to the right of the cursor goes to the next line whole. */
	public void testBlockTooWideForTheRestOfTheLineWraps() {
		withTerminalSized(10, 6);
		enterString("aaaaaaaa");
		assertCursorAt(0, 8);
		enterString("\033]66;s=2:w=2;Z\033\\");
		assertBlock(1, 0, 1, 0, 2, 4);
		assertCursorAt(1, 4);
		assertLineWraps(true, false);
	}

	/** Bigger than the screen in either direction: nothing is written and the cursor stays put. */
	public void testBlockBiggerThanTheScreenIsThrownAway() {
		withTerminalSized(6, 4);
		enterString("\033]66;s=2:w=5;X\033\\");
		assertNull(blockAt(0, 0));
		assertCursorAt(0, 0);
		assertLinesAre("      ", "      ", "      ", "      ");

		withTerminalSized(20, 3);
		enterString("\033]66;s=5:w=1;X\033\\");
		assertNull(blockAt(0, 0));
		assertCursorAt(0, 0);
	}

	/** Rows that would fall below the bottom scroll the screen, as that many line feeds would. */
	public void testBlockAtTheBottomScrollsTheScreen() {
		withTerminalSized(10, 4);
		enterString("one\r\ntwo\r\nthree\r\n");
		assertCursorAt(3, 0);
		enterString("\033]66;s=2:w=2;Z\033\\");
		// One row scrolled away, so the block's top row is the second-to-last row.
		assertBlock(2, 0, 2, 0, 2, 4);
		assertCursorAt(2, 4);
		assertLineIs(0, "two       ");
		assertHistoryStartsWith("one       ");
	}

	/** Insert mode makes room on the block's own row, as a write of s*w cells would. */
	public void testInsertModeShiftsTheRow() {
		withTerminalSized(12, 4);
		enterString("abcdef\r");
		enterString("\033[4h");
		enterString("\033]66;w=2;Z\033\\");
		assertEquals("Z", textOfBlockAt(0, 0));
		assertLineIs(0, "Z abcdef    ");
		assertCursorAt(0, 2);
	}

	/** A combining mark after a block belongs to the block's text. */
	public void testCombiningMarkAttachesToTheBlock() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=1;a\033\\");
		enterString("́");
		assertEquals("á", textOfBlockAt(0, 0));
		assertBlock(0, 0, 0, 0, 2, 2);
	}

	// --- Overwriting and erasing --------------------------------------------------------

	/** Writing over the cell that holds the text takes the whole block away. */
	public void testWritingOverTheAnchorDropsTheWholeBlock() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=2;Hi\033\\");
		enterString("\033[1;1H");
		enterString("x");
		assertNull(blockAt(0, 0));
		assertNull(blockAt(0, 3));
		assertNull(blockAt(1, 2));
		assertLineIs(0, "x                   ");
		assertLineIs(1, "                    ");
	}

	/** Writing over any other cell leaves a hole and the rest of the block standing. */
	public void testWritingOverAContinuationCellOnlyBlanksThatCell() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=2;Hi\033\\");
		enterString("\033[2;3H");
		enterString("x");
		assertNull("the hole", blockAt(1, 2));
		assertBlock(0, 0, 0, 0, 2, 4);
		assertBlock(1, 3, 0, 0, 2, 4);
		assertEquals("Hi", textOfBlockAt(0, 0));
	}

	public void testEraseCharacterDropsTheBlock() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=2;Hi\033\\");
		enterString("\033[1;4H\033[1X");
		assertNull(blockAt(0, 0));
		assertNull(blockAt(1, 1));
	}

	public void testEraseInLineDropsABlockItTouches() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=2;Hi\033\\");
		enterString("\033[2;2H\033[K");
		assertNull(blockAt(0, 0));
		assertNull(blockAt(0, 2));
	}

	public void testEraseInDisplayDropsTheBlock() {
		withTerminalSized(20, 6);
		enterString("\r\n\033]66;s=2:w=2;Hi\033\\");
		assertBlock(1, 0, 1, 0, 2, 4);
		enterString("\033[2J");
		assertNull(blockAt(1, 0));
		assertNull(blockAt(2, 3));
	}

	public void testInsertAndDeleteCharactersDropTheBlock() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=2;Hi\033\\");
		enterString("\033[1;1H\033[3@");
		assertNull(blockAt(0, 3));
		assertNull(blockAt(1, 0));

		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=2;Hi\033\\");
		enterString("\033[1;1H\033[2P");
		assertNull(blockAt(0, 0));
		assertNull(blockAt(1, 2));
	}

	public void testInsertAndDeleteLinesDropTheBlock() {
		withTerminalSized(20, 6);
		enterString("\r\n\033]66;s=2:w=2;Hi\033\\");
		enterString("\033[1;1H\033[1L");
		assertNull(blockAt(2, 0));
		assertNull(blockAt(3, 1));

		withTerminalSized(20, 6);
		enterString("\r\n\033]66;s=2:w=2;Hi\033\\");
		enterString("\033[1;1H\033[1M");
		assertNull(blockAt(0, 0));
		assertNull(blockAt(1, 1));
	}

	/** Scrolling down (SD) copies rows about, so a block it touches goes whole. */
	public void testScrollDownDropsTheBlock() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=2;Hi\033\\");
		enterString("\033[2T");
		assertNull(blockAt(2, 0));
		assertNull(blockAt(0, 0));
	}

	/** Scrolling up (SU) moves whole rows, so the block rides along and keeps its text. */
	public void testScrollUpCarriesTheBlockAlong() {
		withTerminalSized(20, 6);
		enterString("\r\n\r\n\033]66;s=2:w=2;Hi\033\\");
		assertBlock(2, 0, 2, 0, 2, 4);
		enterString("\033[2S");
		assertBlock(0, 0, 0, 0, 2, 4);
		assertEquals("Hi", textOfBlockAt(0, 0));
	}

	/** A plain scroll carries the rows by reference, so the block survives it intact. */
	public void testAWholeScreenScrollCarriesTheBlockAlong() {
		withTerminalSized(20, 4);
		enterString("\r\n\033]66;s=2:w=2;Hi\033\\");
		assertBlock(1, 0, 1, 0, 2, 4);
		enterString("\033[4;1H\r\n");
		assertBlock(0, 0, 0, 0, 2, 4);
		assertEquals("Hi", textOfBlockAt(0, 0));
	}

	/** A scroll region can cut a block in half, so the block goes instead. */
	public void testAScrollRegionDropsABlockItWouldCut() {
		withTerminalSized(20, 6);
		// Rows 2 to 4 (one based) scroll; the block sits on rows 3 and 4 (zero based).
		enterString("\033[3;1H\033]66;s=2:w=2;Hi\033\\");
		assertBlock(2, 0, 2, 0, 2, 4);
		enterString("\033[1;3r");
		enterString("\033[3;1H\r\n");
		assertNull(blockAt(1, 0));
		assertNull(blockAt(2, 0));
	}

	/** REP repeats the whole block, record and all. */
	public void testRepeatRepeatsTheLastBlock() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=2;Hi\033\\");
		enterString("\033[2b");
		assertBlock(0, 0, 0, 0, 2, 4);
		assertBlock(0, 4, 0, 4, 2, 4);
		assertBlock(0, 8, 0, 8, 2, 4);
		assertEquals("Hi", textOfBlockAt(0, 8));
		assertCursorAt(0, 12);
	}

	/** Ordinary text after a block puts REP back on characters. */
	public void testRepeatAfterOrdinaryTextRepeatsTheCharacter() {
		withTerminalSized(20, 6);
		enterString("\033]66;w=2;Z\033\\");
		enterString("a\033[2b");
		assertLineIs(0, "Z aaa               ");
	}

	/** A block that scrolled into history is still whole, and still one block. */
	public void testABlockScrolledIntoHistoryStaysWhole() {
		withTerminalSized(10, 4);
		enterString("\r\n\r\n\033]66;s=2:w=2;Hi\033\\");
		assertBlock(2, 0, 2, 0, 2, 4);
		enterString("\033[4;1H\r\n\r\n\r\n");
		assertBlock(-1, 0, -1, 0, 2, 4);
		assertBlock(0, 3, -1, 0, 2, 4);
		assertEquals("Hi", textOfBlockAt(0, 3));
	}

	/** Half a block left behind when its anchor leaves history reads as no block at all. */
	public void testAnOrphanedContinuationCellResolvesToNothing() {
		withTerminalSized(10, 4);
		enterString("\r\n\r\n\033]66;s=2:w=2;Hi\033\\");
		enterString("\033[4;1H\r\n\r\n\r\n");
		assertBlock(0, 0, -1, 0, 2, 4);
		// The anchor row goes with the scrollback, leaving the row below it half a block.
		enterString("\033[3J");
		for (int column = 0; column < 10; column++)
			assertNull("column=" + column, blockAt(0, column));
		assertEquals("", screen().getSelectedText(0, 0, 9, 0));
		assertFalse("the block's text went with its anchor", screen().getTranscriptText().contains("Hi"));
	}

	// --- Selection ----------------------------------------------------------------------

	/** A block is one unit: touching any of its cells copies its text, once. */
	public void testSelectingAnyCellOfABlockCopiesItsTextOnce() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=2;Hi\033\\");
		enterString("tail");
		assertEquals("Hitail", screen().getSelectedText(0, 0, 19, 0));
		// Only the block's second row, and only one of its cells.
		assertEquals("Hi", screen().getSelectedText(2, 1, 3, 1));
		// The whole block plus what follows it, still one copy of the text.
		assertEquals("Hitail\n", screen().getSelectedText(0, 0, 19, 1));
	}

	/** Double tap: a block is one word, and the words after it are still found where they are. */
	public void testWordAtLocationAroundABlock() {
		withTerminalSized(20, 4);
		enterString("\033]66;s=2:w=2;Hi\033\\");
		enterString(" alpha beta");
		assertEquals("Hi", screen().getWordAtLocation(0, 0));
		assertEquals("Hi", screen().getWordAtLocation(2, 0));
		assertEquals("Hi", screen().getWordAtLocation(1, 1));
		assertEquals("alpha", screen().getWordAtLocation(5, 0));
		assertEquals("alpha", screen().getWordAtLocation(9, 0));
		assertEquals("beta", screen().getWordAtLocation(11, 0));
		assertEquals("beta", screen().getWordAtLocation(14, 0));
		assertEquals("", screen().getWordAtLocation(4, 0));
	}

	/** The record is one int a cache can compare, and it describes the same block as the accessors. */
	public void testTheRecordIsReadableAsOneValue() {
		withTerminalSized(20, 4);
		enterString("\033]66;s=2:w=2;Hi\033\\");
		TerminalRow row = screen().mLines[screen().externalToInternalRow(0)];
		assertTrue(row.hasTextSizes());
		assertEquals(0, row.getTextSizeRecord(4));
		assertTrue(row.getTextSizeRecord(0) != 0);
		assertTrue("every cell of a block differs only in its offsets",
			row.getTextSizeRecord(0) != row.getTextSizeRecord(1));
		assertEquals(2, row.getTextScale(3));
		assertEquals(3, row.getTextSizeOffsetX(3));
		assertEquals(0, row.getTextSizeOffsetY(3));
	}

	public void testSelectingBesideABlockDoesNotPickItUp() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=2;Hi\033\\");
		enterString("tail");
		assertEquals("tail", screen().getSelectedText(4, 0, 19, 0));
	}

	// --- Resize -------------------------------------------------------------------------

	/** A row-only resize reuses the rows, so the records are untouched. */
	public void testRowOnlyResizeLeavesBlocksAlone() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=2;Hi\033\\");
		resize(20, 8);
		assertBlock(0, 0, 0, 0, 2, 4);
		assertEquals("Hi", textOfBlockAt(0, 0));
	}

	/** Narrower, then wide again: the block is demoted to one row and grows back. */
	public void testShrinkAndGrowBackDemotesAndPromotes() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=3:w=2;Hi\033\\");
		assertBlock(0, 0, 0, 0, 3, 6);

		resize(4, 6);
		TerminalBuffer.TextBlock demoted = blockAt(0, 0);
		assertNotNull(demoted);
		assertTrue("demoted", demoted.demoted);
		assertEquals(1, demoted.rows);
		assertEquals(2, demoted.columns);
		assertEquals(3, demoted.scale);
		assertEquals(2, demoted.cellWidth);
		assertEquals("Hi", textOfBlockAt(0, 0));

		resize(20, 6);
		TerminalBuffer.TextBlock promoted = blockAt(0, 0);
		assertNotNull(promoted);
		assertFalse("promoted", promoted.demoted);
		assertEquals(3, promoted.rows);
		assertEquals(6, promoted.columns);
		assertEquals("Hi", textOfBlockAt(0, 0));

		resize(4, 6);
		TerminalBuffer.TextBlock again = blockAt(0, 0);
		assertNotNull(again);
		assertTrue("demoted again", again.demoted);
		assertEquals(1, again.rows);
	}

	/** Text after a block on the same line rewraps the way any line does. */
	public void testTextAfterABlockRewraps() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=2:w=2;Hi\033\\");
		enterString("tail");
		resize(10, 6);
		assertBlock(0, 0, 0, 0, 2, 4);
		assertEquals("Hi", textOfBlockAt(0, 0));
		assertEquals("Hitail", screen().getSelectedText(0, 0, 9, 1).replace("\n", ""));
	}

	/** A demoted block still obeys the write-over and erase rules. */
	public void testADemotedBlockStillDropsOnWriteOver() {
		withTerminalSized(20, 6);
		enterString("\033]66;s=3:w=2;Hi\033\\");
		resize(4, 6);
		assertNotNull(blockAt(0, 1));
		mTerminal.resize(4, 6, 1, 1);
		enterString("\033[1;1Hx");
		assertNull(blockAt(0, 0));
		assertNull(blockAt(0, 1));
	}
}
