package com.termux.terminal;

/**
 * A resize while one screen shows, then a switch to the other, on the Ghostty core: the buffer
 * switched to takes the terminal's size, so its rows are mirrored and every row the view asks for
 * exists. Runs only in builds made with {@code -Pghostty=true}; elsewhere every test returns at once.
 */
public class GhosttyScreenSwitchResizeTest extends TerminalTestCase {

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

    /** The keyboard goes down while a full-screen program runs; the program then exits. */
    public void testLeavingTheAlternateScreenAfterAResizeGivesTheMainScreenTheNewRows() {
        withTerminalSized(6, 3);
        enterString("main");
        enterString("\033[?1049h");
        mTerminal.resize(6, 5, 10, 20);
        enterString("\033[?1049l");
        enterString("\033[5;1Hlast");

        TerminalContent content = mTerminal.getContent();
        assertEquals(5, content.screenRows());
        assertNotNull(content.row(4));
        assertLineIs(4, "last  ");
    }

    /** The keyboard comes up at the prompt; a full-screen program then starts. */
    public void testEnteringTheAlternateScreenAfterAResizeGivesItTheNewRows() {
        withTerminalSized(6, 5);
        enterString("main");
        mTerminal.resize(6, 3, 10, 20);
        mTerminal.resize(6, 7, 10, 20);
        enterString("\033[?1049h\033[7;1Hfull");

        TerminalContent content = mTerminal.getContent();
        assertEquals(7, content.screenRows());
        assertNotNull(content.row(6));
        assertLineIs(6, "full  ");
    }
}
