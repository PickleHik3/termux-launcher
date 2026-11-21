package com.termux.terminal;

/**
 * The screen getting shorter and taller again at a short session, on the Ghostty core: the blank
 * rows below the cursor are the ones given up, so the output above the prompt stays on screen and
 * nothing is pushed into history. Runs only in builds made with {@code -Pghostty=true}; elsewhere
 * every test returns at once.
 */
public class GhosttyShrinkAtPromptTest extends TerminalTestCase {

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

    /** The keyboard comes up and goes down three times under a banner and a prompt. */
    public void testKeyboardCyclesAtAShortSessionKeepTheOutputOnScreen() {
        withTerminalSized(6, 12);
        enterString("b1\r\nb2\r\nb3\r\nb4\r\np$ ");
        for (int cycle = 0; cycle < 3; cycle++) {
            mTerminal.resize(6, 6, 10, 20);
            assertShortSessionIsWhole(6);
            mTerminal.resize(6, 12, 10, 20);
            assertShortSessionIsWhole(12);
        }
    }

    /** A shell that redraws its prompt on every size change writes it over the old one. */
    public void testAShellRedrawingItsPromptOnResizeLeavesOnePrompt() {
        withTerminalSized(6, 12);
        enterString("b1\r\nb2\r\nb3\r\nb4\r\np$ ");
        for (int cycle = 0; cycle < 3; cycle++) {
            mTerminal.resize(6, 6, 10, 20);
            enterString("\r\033[Jp$ ");
            assertShortSessionIsWhole(6);
            mTerminal.resize(6, 12, 10, 20);
            enterString("\r\033[Jp$ ");
            assertShortSessionIsWhole(12);
        }
    }

    private void assertShortSessionIsWhole(int rows) {
        TerminalContent content = mTerminal.getContent();
        assertEquals(rows, content.screenRows());
        assertEquals("rows pushed into history", 0, content.historyRows());
        assertEquals(4, mTerminal.getCursorRow());
        assertLineIs(0, "b1    ");
        assertLineIs(3, "b4    ");
        assertLineIs(4, "p$    ");
        assertLineIs(5, "      ");
    }
}
