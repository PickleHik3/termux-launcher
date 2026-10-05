package com.termux.terminal;

public class TerminalLinksTest extends TerminalTestCase {

    private static final String URI = "https://example.com/a";

    public void testHyperlinkWinsOverText() {
        withTerminalSized(40, 3).enterString("\033]8;;" + URI + "\033\\https://other.example\033]8;;\033\\");
        TerminalLinks.Link link = TerminalLinks.at(mTerminal, 3, 0, true);
        assertNotNull(link);
        assertEquals(URI, link.uri);
        assertTrue(link.hyperlink);
    }

    public void testPlainUrlIsReadFromTheText() {
        withTerminalSized(40, 3).enterString("see " + URI + " now");
        TerminalLinks.Link link = TerminalLinks.at(mTerminal, 8, 0, true);
        assertNotNull(link);
        assertEquals(URI, link.uri);
        assertFalse(link.hyperlink);
        assertNull(TerminalLinks.at(mTerminal, 1, 0, true));
    }

    public void testPlainUrlNeedsDetectionOn() {
        withTerminalSized(40, 3).enterString(URI);
        assertNull(TerminalLinks.at(mTerminal, 2, 0, false));
        assertNotNull(TerminalLinks.at(mTerminal, 2, 0, true));
    }

    public void testHyperlinkIsFoundWithDetectionOff() {
        withTerminalSized(40, 3).enterString("\033]8;;" + URI + "\033\\link\033]8;;\033\\");
        TerminalLinks.Link link = TerminalLinks.at(mTerminal, 1, 0, false);
        assertNotNull(link);
        assertTrue(link.hyperlink);
    }

    public void testOutsideTheScreenIsNull() {
        withTerminalSized(10, 2).enterString(URI);
        assertNull(TerminalLinks.at(mTerminal, -1, 0, true));
        assertNull(TerminalLinks.at(mTerminal, 50, 0, true));
    }
}
