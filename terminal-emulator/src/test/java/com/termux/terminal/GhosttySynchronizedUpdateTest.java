package com.termux.terminal;

/**
 * Synchronized output (mode 2026) on the Ghostty core: every case of {@link SynchronizedUpdateTest}
 * as the client sees it, plus what only this core does, which is keep the frozen frame itself. The
 * library's render hold freezes its render state when a hold begins, so rows, cursor and scrolled
 * rows stay as the program left them before the hold until it ends, times out, or a reset or resize
 * ends it. Runs only in builds made with {@code -Pghostty=true}; elsewhere every test returns at once.
 */
public class GhosttySynchronizedUpdateTest extends SynchronizedUpdateTest {

    @Override
    protected void runTest() throws Throwable {
        if (System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) == null) return;
        super.runTest();
    }

    @Override
    protected TerminalTestCase withTerminalSized(int columns, int rows) {
        super.withTerminalSized(columns, rows);
        assertTrue("library failed to load: " + GhosttyNative.loadError(), mTerminal.useGhosttyCore());
        return this;
    }

    public void testHeldOutputReachesNeitherRowsNorCursor() {
        withTerminalSized(5, 3);
        enterString("one");
        enterString("\033[?2026h\033[2;1Htwo");
        assertLinesAre("one  ", "     ", "     ");
        assertEquals(0, mTerminal.getCursorRow());
        assertEquals(3, mTerminal.getCursorCol());

        enterString("\033[?2026l");
        assertLinesAre("one  ", "two  ", "     ");
        assertEquals(1, mTerminal.getCursorRow());
    }

    /** The frame the hold keeps is the one from just before it, even within the same write. */
    public void testTheFrameBeforeTheHoldInTheSameWriteIsKept() {
        withTerminalSized(5, 3);
        enterString("one\033[?2026h\rtwo");
        assertTrue(mTerminal.isScreenUpdateHeld());
        assertLineIs(0, "one  ");
    }

    /**
     * A hold that ends and another that begins between two syncs leave the finished frame between
     * them, not the half-drawn one after, and the second hold gets its own deadline.
     */
    public void testAReleaseAndRestartBetweenSyncsKeepsTheFinishedFrame() {
        withTerminalSized(5, 3);
        enterString("\033[?2026hA\033[?2026l\033[?2026h\rB");
        assertTrue(mTerminal.isScreenUpdateHeld());
        assertLineIs(0, "A    ");
        enterString("\033[?2026l");
        assertLineIs(0, "B    ");
    }

    /** A program that never releases gets the live frame once the timeout passes. */
    public void testTimeoutDeliversTheLiveFrame() {
        withTerminalSized(5, 3);
        mTerminal.setSynchronizedUpdateTimeoutMillisForTests(0);
        enterString("\033[?2026habc");
        assertLineIs(0, "     ");

        assertFalse("the deadline has passed", mTerminal.isScreenUpdateHeld());
        assertLineIs(0, "abc  ");
        assertEquals(0, mTerminal.getCursorRow());
        assertEquals(3, mTerminal.getCursorCol());
        // Ended in the library too: more output is not held.
        enterString("d");
        assertLineIs(0, "abcd ");
    }

    /** Setting the mode again inside a hold does not move the deadline out (the Java core's does). */
    public void testSettingTheModeAgainDoesNotPushTheDeadline() throws InterruptedException {
        withTerminalSized(5, 3);
        enterString("\033[?2026h");
        long first = mTerminal.screenUpdateHoldRemainingMillis();
        Thread.sleep(30);
        enterString("\033[?2026h");
        long second = mTerminal.screenUpdateHoldRemainingMillis();
        assertTrue("first=" + first + " second=" + second, second <= first - 20);
    }

    /** Rows scrolled off during a hold reach history and the scroll count with the frame that shows them. */
    public void testRowsScrolledDuringAHoldArriveWithTheRelease() {
        withTerminalSized(5, 3);
        enterString("1\r\n2\r\n3");
        mTerminal.clearScrollCounter();
        enterString("\033[?2026h\r\n4\r\n5\r\n6");
        assertEquals(0, mTerminal.getContent().historyRows());
        assertEquals(0, mTerminal.getScrollCounter());
        assertLinesAre("1    ", "2    ", "3    ");

        enterString("\033[?2026l");
        assertEquals(3, mTerminal.getScrollCounter());
        assertEquals(3, mTerminal.getContent().historyRows());
        assertLinesAre("4    ", "5    ", "6    ");
        assertLineIs(-1, "3    ");
    }

    /** Cursor visibility belongs to the frame; input modes stay live, as on the Java core. */
    public void testCursorVisibilityIsHeldAndInputModesAreNot() {
        withTerminalSized(5, 3);
        enterString("\033[?2026h\033[?25l\033[?2004h\033[?1h");
        assertTrue("the held frame shows the cursor", mTerminal.isCursorEnabled());
        assertTrue(mTerminal.isBracketedPasteMode());
        assertTrue(mTerminal.isCursorKeysApplicationMode());

        enterString("\033[?2026l");
        assertFalse(mTerminal.isCursorEnabled());
    }

    /** The alternate screen switched to inside a hold appears only with the release. */
    public void testAScreenSwitchInsideAHoldWaitsForTheRelease() {
        withTerminalSized(5, 3);
        enterString("main");
        enterString("\033[?2026h\033[?1049h\033[Halt");
        assertFalse(mTerminal.isAlternateBufferActive());
        assertLineIs(0, "main ");

        enterString("\033[?2026l");
        assertTrue(mTerminal.isAlternateBufferActive());
        assertLineIs(0, "alt  ");
    }

    /** A reset from the app while held shows the reset screen at once. */
    public void testAResetWhileHeldShowsTheResetScreen() {
        withTerminalSized(5, 3);
        enterString("abc\033[?2026hdef");
        mTerminal.reset();
        assertFalse(mTerminal.isScreenUpdateHeld());
        assertLinesAre("     ", "     ", "     ");
        enterString("x");
        assertLineIs(0, "x    ");
    }

    /**
     * Links and the pointer shape belong to the frozen frame: a row the hold keeps keeps the link it
     * had, and a pointer shape set during the hold waits for its end, like the rows.
     */
    public void testTheHeldFrameKeepsItsLinksAndPointerShape() {
        withTerminalSized(5, 3);
        String before = mTerminal.getPointerShape();
        enterString("\033]8;;https://a.example\033\\aaa\033]8;;\033\\"
            + "\033[?2026h\r\033]8;;https://b.example\033\\bbb\033]8;;\033\\\033]22;pointer\007");
        assertTrue(mTerminal.isScreenUpdateHeld());
        assertLineIs(0, "aaa  ");
        assertEquals("https://a.example", mTerminal.getHyperlinkUriAt(0, 0));
        assertEquals(before, mTerminal.getPointerShape());

        enterString("\033[?2026l");
        assertLineIs(0, "bbb  ");
        assertEquals("https://b.example", mTerminal.getHyperlinkUriAt(0, 0));
        assertEquals("pointer", mTerminal.getPointerShape());
    }
}
