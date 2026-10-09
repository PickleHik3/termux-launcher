package com.termux.terminal;

/**
 * {@link TerminalEmulator#predictRowsOnlyResizeShift} answers, before a rows-only resize, exactly
 * where that resize will put the rows on screen: every case here predicts, then resizes, and
 * checks that each old row is found where the prediction said.
 */
public class ResizeShiftPredictionTest extends TerminalTestCase {

    private static final int COLS = 3;

    private String[] screenLines() {
        String[] lines = new String[mTerminal.mRows];
        for (int row = 0; row < lines.length; row++)
            lines[row] = mTerminal.getScreen().getSelectedText(0, row, COLS, row, false);
        return lines;
    }

    /** Predicts, resizes to {@code rows}, and asserts every surviving old row landed at row + shift. */
    private void assertPredictionHolds(int rows, boolean keepCursorAtBottom) {
        String[] before = screenLines();
        int shift = mTerminal.predictRowsOnlyResizeShift(rows, keepCursorAtBottom);
        mTerminal.resize(COLS, rows, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS,
            keepCursorAtBottom);
        String[] after = screenLines();
        int checked = 0;
        for (int row = 0; row < before.length; row++) {
            int landed = row + shift;
            if (landed < 0 || landed >= after.length) continue;
            assertEquals("old row " + row + " with shift " + shift, before[row], after[landed]);
            checked++;
        }
        assertTrue("nothing to compare", checked > 0);
    }

    public void testShrinkingWithThePromptAtTheBottomScrollsTheTopRowsAway() {
        withTerminalSized(COLS, 5).enterString("111222333444555").assertCursorAt(4, 2);
        assertEquals(-2, mTerminal.predictRowsOnlyResizeShift(3, true));
        assertPredictionHolds(3, true);
        assertLinesAre("333", "444", "555");
    }

    public void testShrinkingDropsBlankRowsBelowTheCursorFirst() {
        withTerminalSized(COLS, 5).enterString("111\r\n222").assertCursorAt(1, 2);
        // Three blank rows under the cursor cover the two rows the shrink takes: nothing moves.
        assertEquals(0, mTerminal.predictRowsOnlyResizeShift(3, true));
        assertPredictionHolds(3, true);
        assertLinesAre("111", "222", "   ");
    }

    public void testShrinkingPastTheBlankRowsMovesTheRestUp() {
        withTerminalSized(COLS, 5).enterString("111\r\n222\r\n333\r\n444").assertCursorAt(3, 2);
        // One blank row under the cursor, two rows to take: one row of movement is left.
        assertEquals(-1, mTerminal.predictRowsOnlyResizeShift(3, true));
        assertPredictionHolds(3, true);
        assertLinesAre("222", "333", "444");
    }

    public void testGrowingAnchoredAtTheBottomRevealsTheTranscriptAbove() {
        withTerminalSized(COLS, 3).enterString("111222333444555").assertLinesAre("333", "444", "555");
        assertEquals(2, mTerminal.predictRowsOnlyResizeShift(5, true));
        assertPredictionHolds(5, true);
        assertLinesAre("111", "222", "333", "444", "555");
    }

    public void testGrowingUnanchoredMovesDownOnlyAsFarAsTheTranscriptGoes() {
        withTerminalSized(COLS, 3).enterString("111222333444").assertLinesAre("222", "333", "444");
        // One transcript row for two new rows: the screen moves down one, a blank row is added.
        assertEquals(1, mTerminal.predictRowsOnlyResizeShift(5, false));
        assertPredictionHolds(5, false);
        assertLinesAre("111", "222", "333", "444", "   ");
    }

    public void testGrowingWithNothingAboveKeepsTheRowsWhereTheyAre() {
        withTerminalSized(COLS, 3).enterString("a\r\ndef$").assertCursorAt(2, 1);
        assertEquals(0, mTerminal.predictRowsOnlyResizeShift(5, false));
        assertPredictionHolds(5, false);
        assertLinesAre("a  ", "def", "$  ", "   ", "   ");
    }

    public void testTheBottomAnchorIsIgnoredForACursorHighUpTheScreen() {
        withTerminalSized(COLS, 5).enterString("111222333444555").assertCursorAt(4, 2);
        // A cleared screen leaves the prompt at the top with the transcript behind it: growing
        // must not float that prompt down over blank padding, and neither must the prediction.
        enterString("\033[H\033[2J").assertCursorAt(0, 0);
        assertEquals(mTerminal.predictRowsOnlyResizeShift(7, false),
            mTerminal.predictRowsOnlyResizeShift(7, true));
        assertPredictionHolds(7, true);
    }

    public void testGrowingTheAlternateScreenIsTopAligned() {
        withTerminalSized(COLS, 3).enterString("\033[?1049h").enterString("abc\r\ndef");
        assertEquals(0, mTerminal.predictRowsOnlyResizeShift(5, true));
        assertPredictionHolds(5, true);
    }

    public void testTheAlternateScreenPredictsNoShift() {
        withTerminalSized(COLS, 5).enterString("\033[?1049h").enterString("abc\r\ndef\r\nghi\r\njkl\r\nmno");
        assertEquals(0, mTerminal.predictRowsOnlyResizeShift(3, true));
        assertEquals(0, mTerminal.predictRowsOnlyResizeShift(7, true));
    }

    public void testTheSameRowCountMovesNothing() {
        withTerminalSized(COLS, 4).enterString("111222");
        assertEquals(0, mTerminal.predictRowsOnlyResizeShift(4, true));
    }
}
