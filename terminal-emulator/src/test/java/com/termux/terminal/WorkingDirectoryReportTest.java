package com.termux.terminal;

/**
 * OSC 7, the shell saying which folder it is in: {@code ESC ] 7 ; file://host/path ST}. The launcher
 * opens a new pane there, so a wrong answer is worse than no answer and anything doubtful is dropped.
 */
public class WorkingDirectoryReportTest extends TerminalTestCase {

    public void testNothingIsReportedUntilTheShellSaysSo() {
        withTerminalSized(5, 3);
        assertNull(mTerminal.getReportedWorkingDirectory());
    }

    public void testAnEmptyHostIsThisDevice() {
        withTerminalSized(5, 3).enterString("\033]7;file:///data/data/com.termux/files/home\007");
        assertEquals("/data/data/com.termux/files/home", mTerminal.getReportedWorkingDirectory());
    }

    public void testLocalhostIsThisDeviceToo() {
        withTerminalSized(5, 3).enterString("\033]7;file://localhost/tmp\033\\");
        assertEquals("/tmp", mTerminal.getReportedWorkingDirectory());
    }

    /** A percent-encoded path comes back as the path it names, including multi-byte characters. */
    public void testThePathIsPercentDecoded() {
        withTerminalSized(5, 3).enterString("\033]7;file:///home/my%20dir/na%C3%AFve%2Bx\007");
        assertEquals("/home/my dir/naïve+x", mTerminal.getReportedWorkingDirectory());
    }

    /** "+" is a plus sign in a path, not the space it means in a query string. */
    public void testPlusIsNotASpace() {
        withTerminalSized(5, 3).enterString("\033]7;file:///home/a+b\007");
        assertEquals("/home/a+b", mTerminal.getReportedWorkingDirectory());
    }

    public void testAnotherHostIsIgnored() {
        withTerminalSized(5, 3).enterString("\033]7;file:///tmp\007");
        enterString("\033]7;file://elsewhere/var\007");
        assertEquals("/tmp", mTerminal.getReportedWorkingDirectory());
    }

    public void testAnotherSchemeIsIgnored() {
        withTerminalSized(5, 3).enterString("\033]7;file:///tmp\007");
        enterString("\033]7;http://example.com/var\007");
        assertEquals("/tmp", mTerminal.getReportedWorkingDirectory());
    }

    public void testAMalformedEscapeIsIgnored() {
        withTerminalSized(5, 3).enterString("\033]7;file:///tmp\007");
        enterString("\033]7;file:///home/%zz\007");
        assertEquals("/tmp", mTerminal.getReportedWorkingDirectory());
    }

    public void testAUriWithoutAPathIsIgnored() {
        withTerminalSized(5, 3).enterString("\033]7;file:///tmp\007");
        enterString("\033]7;file://localhost\007");
        assertEquals("/tmp", mTerminal.getReportedWorkingDirectory());
    }

    /** An empty report means the shell no longer knows where it is. */
    public void testAnEmptyReportClearsIt() {
        withTerminalSized(5, 3).enterString("\033]7;file:///tmp\007");
        enterString("\033]7;\007");
        assertNull(mTerminal.getReportedWorkingDirectory());
    }

    /** The screen is untouched by a report — no stray text, no moved cursor. */
    public void testTheReportPrintsNothing() {
        withTerminalSized(5, 3).enterString("ab\033]7;file:///tmp\007cd");
        assertLineIs(0, "abcd ");
        assertEquals("", mOutput.getOutputAndClear());
    }
}
