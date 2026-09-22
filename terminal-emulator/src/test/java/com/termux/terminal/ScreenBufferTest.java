package com.termux.terminal;

public class ScreenBufferTest extends TerminalTestCase {

    public void testBasics() {
        TerminalBuffer screen = new TerminalBuffer(5, 3, 3);
        assertEquals("", screen.getTranscriptText());
        screen.setChar(0, 0, 'a', 0);
        assertEquals("a", screen.getTranscriptText());
        screen.setChar(0, 0, 'b', 0);
        assertEquals("b", screen.getTranscriptText());
        screen.setChar(2, 0, 'c', 0);
        assertEquals("b c", screen.getTranscriptText());
        screen.setChar(2, 2, 'f', 0);
        assertEquals("b c\n\n  f", screen.getTranscriptText());
        screen.blockSet(0, 0, 2, 2, 'X', 0);
    }

    public void testBlockSet() {
        TerminalBuffer screen = new TerminalBuffer(5, 3, 3);
        screen.blockSet(0, 0, 2, 2, 'X', 0);
        assertEquals("XX\nXX", screen.getTranscriptText());
        screen.blockSet(1, 1, 2, 2, 'Y', 0);
        assertEquals("XX\nXYY\n YY", screen.getTranscriptText());
    }

    public void testBottomAnchoredExpansionAddsBlankRowsAboveCursor() {
        TerminalBuffer screen = new TerminalBuffer(5, 10, 3);
        screen.setChar(0, 0, 'a', TextStyle.NORMAL);
        screen.setChar(0, 2, 'p', TextStyle.NORMAL);
        int[] cursor = {0, 2};

        screen.resize(5, 5, 10, cursor, TextStyle.NORMAL, false, true);

        assertEquals(4, cursor[1]);
        assertEquals("\n\na\n\np", screen.getSelectedText(0, 0, 4, 4, false));
    }

    public void testGetSelectedText() {
        withTerminalSized(5, 3).enterString("ABCDEFGHIJ").assertLinesAre("ABCDE", "FGHIJ", "     ");
        assertEquals("AB", mTerminal.getSelectedText(0, 0, 1, 0));
        assertEquals("BC", mTerminal.getSelectedText(1, 0, 2, 0));
        assertEquals("CDE", mTerminal.getSelectedText(2, 0, 4, 0));
        assertEquals("FG", mTerminal.getSelectedText(0, 1, 1, 1));
        assertEquals("GH", mTerminal.getSelectedText(1, 1, 2, 1));
        assertEquals("HIJ", mTerminal.getSelectedText(2, 1, 4, 1));
        assertEquals("ABCDEFG", mTerminal.getSelectedText(0, 0, 1, 1));
        withTerminalSized(5, 3).enterString("ABCDE\r\nFGHIJ").assertLinesAre("ABCDE", "FGHIJ", "     ");
        assertEquals("ABCDE\nFG", mTerminal.getSelectedText(0, 0, 1, 1));
    }

    public void testGetSelectedTextJoinFullLines() {
        withTerminalSized(5, 3).enterString("ABCDE\r\nFG");
        assertEquals("ABCDEFG", mTerminal.getScreen().getSelectedText(0, 0, 1, 1, true, true));
        withTerminalSized(5, 3).enterString("ABC\r\nFG");
        assertEquals("ABC\nFG", mTerminal.getScreen().getSelectedText(0, 0, 1, 1, true, true));
    }

    /** Trimming on (the shipped default): a wrapped row's trailing padding is dropped. */
    public void testGetSelectedTextTrimsWrappedRowPaddingByDefault() {
        // Row 0 fills all 5 columns as "AB   " and wraps; row 1 continues as "  CD ".
        // Row 1 starts with a space, so no join space is added back.
        withTerminalSized(5, 3).enterString("AB     CD");
        assertEquals("AB  CD", mTerminal.getSelectedText(0, 0, 4, 1));
    }

    /** A trimmed wrapped row that ends mid-word keeps exactly one space before the next word. */
    public void testGetSelectedTextKeepsOneSpaceBeforeNextWord() {
        // Row 0 fills all 5 columns as "AB   " and wraps; row 1 continues as "CD   ".
        withTerminalSized(5, 3).enterString("AB   CD");
        assertEquals("AB CD", mTerminal.getSelectedText(0, 0, 4, 1));
    }

    /** With the setting off, a wrapped row's trailing padding is kept in full, as before. */
    public void testGetSelectedTextKeepsWrappedRowPaddingWhenTrimDisabled() {
        withTerminalSized(5, 3).enterString("AB   CD");
        mTerminal.setTrimWrappedTrailingSpaces(false);
        assertEquals("AB   CD", mTerminal.getSelectedText(0, 0, 4, 1));
    }

    /** An unwrapped row's trailing padding is always trimmed, regardless of the setting. */
    public void testGetSelectedTextUnwrappedRowUnaffectedByTrimSetting() {
        withTerminalSized(5, 3).enterString("ABC  \r\nDEF");
        assertEquals("ABC\nDEF", mTerminal.getSelectedText(0, 0, 4, 1));
        mTerminal.setTrimWrappedTrailingSpaces(false);
        assertEquals("ABC\nDEF", mTerminal.getSelectedText(0, 0, 4, 1));
    }

    public void testGetWordAtLocation() {
        withTerminalSized(5, 3).enterString("ABCDEFGHIJ\r\nKLMNO");
        assertEquals("ABCDEFGHIJKLMNO", mTerminal.getScreen().getWordAtLocation(0, 0));
        assertEquals("ABCDEFGHIJKLMNO", mTerminal.getScreen().getWordAtLocation(4, 1));
        assertEquals("ABCDEFGHIJKLMNO", mTerminal.getScreen().getWordAtLocation(4, 2));
        withTerminalSized(5, 3).enterString("ABC DEF GHI ");
        assertEquals("ABC", mTerminal.getScreen().getWordAtLocation(0, 0));
        assertEquals("", mTerminal.getScreen().getWordAtLocation(3, 0));
        assertEquals("DEF", mTerminal.getScreen().getWordAtLocation(4, 0));
        assertEquals("DEF", mTerminal.getScreen().getWordAtLocation(0, 1));
        assertEquals("DEF", mTerminal.getScreen().getWordAtLocation(1, 1));
        assertEquals("GHI", mTerminal.getScreen().getWordAtLocation(0, 2));
        assertEquals("", mTerminal.getScreen().getWordAtLocation(1, 2));
        assertEquals("", mTerminal.getScreen().getWordAtLocation(2, 2));
    }
}
