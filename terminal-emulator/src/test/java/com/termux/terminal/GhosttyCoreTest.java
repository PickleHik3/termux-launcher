package com.termux.terminal;

import java.nio.charset.StandardCharsets;

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

    /** Like {@link #ghostty}, with a transcript of {@code transcriptRows} rows. */
    private boolean ghostty(int columns, int rows, int transcriptRows) {
        if (System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) == null) return false;
        mTerminal = new TerminalEmulator(mOutput, false, columns, rows, INITIAL_CELL_WIDTH_PIXELS,
            INITIAL_CELL_HEIGHT_PIXELS, transcriptRows, null);
        assertTrue("library failed to load: " + GhosttyNative.loadError(), mTerminal.useGhosttyCore());
        return true;
    }

    /** Numbered lines {@code from} to {@code to - 1}, each ending in CR LF. */
    private static String numberedLines(int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) sb.append(i).append("\r\n");
        return sb.toString();
    }

    /** Transcript row -1 is {@code newest}, -2 the number before it, back to row {@code -count}. */
    private void assertTranscriptCountsDownFrom(int newest, int count, int columns) {
        for (int i = 1; i <= count; i++) {
            StringBuilder line = new StringBuilder(Integer.toString(newest - i + 1));
            while (line.length() < columns) line.append(' ');
            assertLineIs(-i, line.toString());
        }
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
        assertEquals(2, mTerminal.getContent().historyRows());
        assertLineIs(-2, "1    ");
        assertLineIs(-1, "2    ");
    }

    public void testScrolledRowsAdvanceTheScrollCounters() {
        if (!ghostty(5, 3)) return;
        mTerminal.clearScrollCounter();
        long eventsBefore = mTerminal.scrollEventCount();
        enterString(numberedLines(1, 8)); // 7 line feeds on 3 rows scroll 5 rows
        int counter = mTerminal.getScrollCounter();
        long events = mTerminal.scrollEventCount() - eventsBefore;
        assertEquals(5, counter);
        assertEquals(5, events);
        // The Java core reports the same for the same input.
        withTerminalSized(5, 3);
        mTerminal.clearScrollCounter();
        long javaBefore = mTerminal.scrollEventCount();
        enterString(numberedLines(1, 8));
        assertEquals(mTerminal.getScrollCounter(), counter);
        assertEquals(mTerminal.scrollEventCount() - javaBefore, events);
        // A second burst adds to the counter until the view clears it.
        mTerminal.clearScrollCounter();
        assertEquals(0, mTerminal.getScrollCounter());
    }

    public void testScrollCountersFollowAcrossSeparateSyncs() {
        if (!ghostty(5, 3)) return;
        mTerminal.clearScrollCounter();
        enterString(numberedLines(1, 6)); // 3 rows scrolled
        enterString(numberedLines(6, 9)); // 3 more
        assertEquals(6, mTerminal.getScrollCounter());
    }

    public void testLongBurstKeepsTheNewestTranscriptRows() {
        if (!ghostty(6, 3)) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3000; i++) sb.append(i).append("\r\n");
        enterString(sb.toString());
        // Thousands of rows in one write: the newest ones must be in order.
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
        // Ghostty's reflow, not the Java buffer's: the cursor's row stays put, so the line's first
        // half goes into history, and the screen and cursor agree on where the text is.
        assertEquals(1, mTerminal.getContent().historyRows());
        assertLineIs(-1, "abc");
        assertLineIs(0, "def");
        assertEquals(0, mTerminal.getCursorRow());
    }

    public void testOneWriteLongerThanAPageLandsWholeAndInOrder() {
        // 6000 rows in one append, more than the 2048 rows the library used to keep: every row
        // must come through the library's history at the one sync.
        if (!ghostty(6, 3, 10000)) return;
        enterString(numberedLines(0, 6000));
        assertEquals(6000 - 2, mTerminal.getContent().historyRows());
        assertLinesAre("5998  ", "5999  ", "      ");
        assertTranscriptCountsDownFrom(5997, 5998, 6);
    }

    public void testWriteThatOutrunsTheHistoryLimitRewritesTheTranscript() {
        // A 100-row transcript, history already at its limit, then 50000 rows in one write:
        // Ghostty prunes the page holding the row the last sync pinned, so how much history grew
        // says nothing, and the whole transcript comes from what history still holds.
        if (!ghostty(8, 3, 100)) return;
        enterString(numberedLines(0, 20000));
        enterString(numberedLines(100000, 150000));
        int capacity = 100 - 3;
        assertEquals(capacity, mTerminal.getContent().historyRows());
        assertLinesAre("149998  ", "149999  ", "        ");
        assertTranscriptCountsDownFrom(149997, capacity, 8);
    }

    public void testBatchedOutputWaitsForTheFlush() {
        if (!ghostty(6, 3, 1000)) return;
        byte[] first = numberedLines(0, 40).getBytes(StandardCharsets.UTF_8);
        byte[] second = numberedLines(40, 90).getBytes(StandardCharsets.UTF_8);
        mTerminal.appendBatched(first, first.length);
        mTerminal.appendBatched(second, second.length);
        // Parsed by Ghostty, not yet mirrored.
        assertLineIs(0, "      ");
        assertEquals(0, mTerminal.getCursorRow());
        mTerminal.flushBatchedOutput();
        assertLinesAre("88    ", "89    ", "      ");
        assertEquals(2, mTerminal.getCursorRow());
        assertTranscriptCountsDownFrom(87, 88, 6);
    }

    public void testRowsScrolledBeforeTheAlternateScreenReachTheTranscript() {
        // One write scrolls rows off and enters the alternate screen; they reach the transcript
        // once the program leaves it.
        if (!ghostty(6, 3, 1000)) return;
        enterString(numberedLines(0, 10) + "\033[?1049h\033[Halt");
        assertTrue(mTerminal.isAlternateBufferActive());
        enterString("\033[?1049l");
        assertFalse(mTerminal.isAlternateBufferActive());
        assertLinesAre("8     ", "9     ", "      ");
        assertTranscriptCountsDownFrom(7, 8, 6);
    }

    public void testPageRowsBoundCoversTheLibrary() {
        // With a limit of one row Ghostty keeps about one page of history, so the most history
        // ever seen is a page's rows: the margin historyLimit adds must cover it.
        if (System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) == null) return;
        byte[] chunk = numberedLines(0, 100).getBytes(StandardCharsets.UTF_8);
        for (int columns : new int[]{6, 52, 200}) {
            GhosttyCore core = GhosttyCore.create(columns, 3, 0, 0, null, 1);
            assertNotNull(core);
            try {
                int most = 0;
                for (int i = 0; i < 300; i++) {
                    core.write(chunk, 0, chunk.length);
                    assertTrue(core.sync());
                    most = Math.max(most, core.state()[GhosttyNative.STATE_HISTORY]);
                }
                assertTrue("history never pruned at " + columns + " columns", most < 300 * 100 - 3);
                assertTrue(columns + " columns: a page held " + most + " rows, bound " + GhosttyCore.pageRowsBound(columns),
                    GhosttyCore.pageRowsBound(columns) >= most);
            } finally {
                core.release();
            }
        }
    }

    public void testReleasedCoreHandsOverToTheJavaParser() {
        if (!ghostty(5, 2)) return;
        enterString("ab");
        mTerminal.releaseGhosttyCore();
        assertFalse(mTerminal.isGhosttyCoreActive());
        enterString("c");
        assertLineIs(0, "abc  ");
    }

    public void testXtVersionIsOurNameWithTheLibraryOnGhostty() {
        TerminalEmulator.setXtVersion("x.y");
        try {
            withTerminalSized(10, 3);
            assertEnteringStringGivesResponse("\033[>q", "\033P>|termux-launcher(x.y)\033\\");
            if (!ghostty(10, 3)) return;
            // Ghostty draws kitty images, so programs matching "ghostty" may pick them.
            assertEnteringStringGivesResponse("\033[>q", "\033P>|termux-launcher(x.y) (libghostty-vt 1.0)\033\\");
            // DA1 claims sixel, which this core now draws (GhosttySixelTest), and still no more.
            enterString("\033[c");
            assertEquals("\033[?62;4;22c", mOutput.getOutputAndClear());
        } finally {
            TerminalEmulator.setXtVersion(null);
        }
    }
}
