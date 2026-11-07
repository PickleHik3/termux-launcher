package com.termux.terminal;

/**
 * The emulator running on the real libghostty-vt, built for this machine. Runs only in builds made
 * with {@code -Pghostty=true}, which build the host library and point the tests at it; elsewhere
 * every test returns at once.
 */
public class GhosttyCoreTest extends TerminalTestCase {

    /** A terminal on the Ghostty core, or false when the library is not in this build. */
    private boolean ghostty(int columns, int rows) {
        if (System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) == null) return false;
        withTerminalSized(columns, rows);
        assertTrue("library failed to load: " + GhosttyNative.loadError(), mTerminal.useGhosttyCore());
        return true;
    }

    public void testTextReachesTheJavaBuffer() {
        if (!ghostty(5, 3)) return;
        enterString("hi\r\nyou");
        assertLinesAre("hi   ", "you  ", "     ");
        assertEquals(1, mTerminal.getCursorRow());
        assertEquals(3, mTerminal.getCursorCol());
    }

    public void testRowsScrollIntoTheTranscript() {
        if (!ghostty(5, 3)) return;
        enterString("1\r\n2\r\n3\r\n4\r\n5");
        assertLinesAre("3    ", "4    ", "5    ");
        assertEquals(2, mTerminal.getScreen().getActiveTranscriptRows());
        assertLineIs(-2, "1    ");
        assertLineIs(-1, "2    ");
    }

    public void testLongBurstKeepsTheNewestTranscriptRows() {
        if (!ghostty(6, 3)) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3000; i++) sb.append(i).append("\r\n");
        enterString(sb.toString());
        // Thousands of rows, across many write slices: the newest ones must be in order.
        assertLinesAre("2998  ", "2999  ", "      ");
        assertLineIs(-1, "2997  ");
        assertLineIs(-6, "2992  ");
        assertLineIs(-500, "2498  ");
    }

    public void testStylesAreMirrored() {
        if (!ghostty(5, 2)) return;
        enterString("\033[1;31mR\033[0m\033[48;2;1;2;3mB");
        long red = mTerminal.getScreen().getStyleAt(0, 0);
        assertEquals(1, TextStyle.decodeForeColor(red));
        assertEquals(TextStyle.CHARACTER_ATTRIBUTE_BOLD, TextStyle.decodeEffect(red));
        assertEquals(0xff010203, TextStyle.decodeBackColor(mTerminal.getScreen().getStyleAt(0, 1)));
    }

    public void testModesComeFromGhostty() {
        if (!ghostty(5, 2)) return;
        enterString("\033[?1h\033[?2004h\033[?1006h\033[?1002h\033[?25l");
        assertTrue(mTerminal.isCursorKeysApplicationMode());
        assertTrue(mTerminal.isBracketedPasteMode());
        assertTrue(mTerminal.isMouseTrackingActive());
        assertTrue(mTerminal.isMouseTrackingMotionActive());
        assertFalse(mTerminal.isCursorEnabled());
        enterString("\033[?1l\033[?2004l\033[?1002l\033[?25h");
        assertFalse(mTerminal.isCursorKeysApplicationMode());
        assertFalse(mTerminal.isBracketedPasteMode());
        assertFalse(mTerminal.isMouseTrackingActive());
        assertTrue(mTerminal.isCursorEnabled());
    }

    public void testKittyKeyboardFlags() {
        if (!ghostty(5, 2)) return;
        enterString("\033[>5u");
        assertEquals(5, mTerminal.getKeyboardFlags());
        enterString("\033[<u");
        assertEquals(0, mTerminal.getKeyboardFlags());
    }

    public void testAlternateScreen() {
        if (!ghostty(5, 2)) return;
        enterString("main");
        enterString("\033[?1049h");
        assertTrue(mTerminal.isAlternateBufferActive());
        enterString("\033[Halt");
        assertLinesAre("alt  ", "     ");
        enterString("\033[?1049l");
        assertFalse(mTerminal.isAlternateBufferActive());
        assertLineIs(0, "main ");
    }

    public void testRepliesGoToThePty() {
        if (!ghostty(5, 2)) return;
        enterString("\033[6n");
        assertEquals("\033[1;1R", mOutput.getOutputAndClear());
        enterString("\033[c");
        assertTrue(mOutput.getOutputAndClear().startsWith("\033[?"));
    }

    public void testTitleBellAndClipboard() {
        if (!ghostty(5, 2)) return;
        enterString("\033]2;hello\007");
        assertEquals("hello", mTerminal.getTitle());
        assertEquals(1, mOutput.titleChanges.size());
        enterString("\007");
        assertEquals(1, mOutput.bellsRung);
        enterString("\033]52;c;aGk=\007");
        assertEquals(1, mOutput.clipboardPuts.size());
        assertEquals("hi", mOutput.clipboardPuts.get(0));
    }

    public void testResizeReflowsTheScreen() {
        if (!ghostty(6, 3)) return;
        enterString("abcdef");
        mTerminal.resize(3, 3, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS);
        assertEquals(3, mTerminal.mColumns);
        assertLineIs(0, "abc");
        assertLineIs(1, "def");
    }

    public void testReleasedCoreHandsOverToTheJavaParser() {
        if (!ghostty(5, 2)) return;
        enterString("ab");
        mTerminal.releaseGhosttyCore();
        assertFalse(mTerminal.isGhosttyCoreActive());
        enterString("c");
        assertLineIs(0, "abc  ");
    }
}
