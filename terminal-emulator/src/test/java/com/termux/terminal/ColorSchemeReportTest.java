package com.termux.terminal;

/**
 * Color scheme signalling: DECSET 2031 color preference notifications, the "CSI ? 996 n" query and
 * its "CSI ? 997 ; Ps n" answer, and the "OSC 4 ; index ; ?" indexed color query.
 */
public class ColorSchemeReportTest extends TerminalTestCase {

    /** The white the default palette calls bright white, and a black background. */
    private static final String LIGHT = "#ffffff";
    private static final String DARK = "#000000";

    private void setBackground(String spec) {
        enterString("\033]11;" + spec + "\007");
    }

    /** DECRQM must recognise mode 2031 and answer with its state, not with "not recognised". */
    public void testDecrqmForColorPreferenceNotifications() {
        withTerminalSized(3, 3);
        enterString("\033[?2031$p");
        assertEquals("An unset but recognised mode answers 2", "\033[?2031;2$y", mOutput.getOutputAndClear());
        enterString("\033[?2031h");
        enterString("\033[?2031$p");
        assertEquals("A set mode answers 1", "\033[?2031;1$y", mOutput.getOutputAndClear());
        enterString("\033[?2031l");
        enterString("\033[?2031$p");
        assertEquals("A reset mode answers 2", "\033[?2031;2$y", mOutput.getOutputAndClear());
    }

    /** "CSI ? 996 n" is answered whether or not the notification mode is on. */
    public void testColorPreferenceQuery() {
        withTerminalSized(3, 3);
        enterString("\033[?996n");
        assertEquals("The default background is black, so dark", "\033[?997;1n", mOutput.getOutputAndClear());
        setBackground(LIGHT);
        mOutput.getOutputAndClear();
        enterString("\033[?996n");
        assertEquals("A white background is light", "\033[?997;2n", mOutput.getOutputAndClear());
        // Still answered with the notification mode enabled.
        enterString("\033[?2031h");
        mOutput.getOutputAndClear();
        enterString("\033[?996n");
        assertEquals("\033[?997;2n", mOutput.getOutputAndClear());
    }

    /** A background set through OSC 11 reports only when it crosses between dark and light. */
    public void testUnsolicitedReportOnBackgroundClassFlip() {
        withTerminalSized(3, 3);
        setBackground(LIGHT);
        assertEquals("Nothing is reported while the mode is off", "", mOutput.getOutputAndClear());
        setBackground(DARK);
        assertEquals("", mOutput.getOutputAndClear());

        enterString("\033[?2031h");
        assertEquals("Enabling the mode does not itself report", "", mOutput.getOutputAndClear());
        setBackground(LIGHT);
        assertEquals("The flip to light is reported once", "\033[?997;2n", mOutput.getOutputAndClear());
        setBackground("#eeeeee");
        assertEquals("A background that keeps the class is silent", "", mOutput.getOutputAndClear());
        setBackground("#101010");
        assertEquals("The flip back to dark is reported", "\033[?997;1n", mOutput.getOutputAndClear());

        enterString("\033[?2031l");
        setBackground(LIGHT);
        assertEquals("Nothing is reported once the mode is off again", "", mOutput.getOutputAndClear());
    }

    /** OSC 111 puts the default background back, which can itself be a flip. */
    public void testUnsolicitedReportOnBackgroundReset() {
        withTerminalSized(3, 3);
        enterString("\033[?2031h");
        setBackground(LIGHT);
        assertEquals("\033[?997;2n", mOutput.getOutputAndClear());
        enterString("\033]111\007");
        assertEquals("Resetting to the dark default background reports", "\033[?997;1n", mOutput.getOutputAndClear());
        enterString("\033]111\007");
        assertEquals("A reset that changes nothing is silent", "", mOutput.getOutputAndClear());
    }

    /** "OSC 4 ; index ; ?" reports the color the way OSC 10/11/12 queries do. */
    public void testIndexedColorQuery() {
        withTerminalSized(3, 3);
        enterString("\033]4;1;#ff0000\007");
        mOutput.getOutputAndClear();
        enterString("\033]4;1;?\007");
        assertEquals("\033]4;1;rgb:ffff/0000/0000\007", mOutput.getOutputAndClear());
        // The reply carries the terminator the query used.
        enterString("\033]4;1;?\033\\");
        assertEquals("\033]4;1;rgb:ffff/0000/0000\033\\", mOutput.getOutputAndClear());
        // A query and a set can be mixed in one sequence.
        enterString("\033]4;2;#00cd00;3;?\007");
        assertEquals("\033]4;3;rgb:cdcd/cdcd/0000\007", mOutput.getOutputAndClear());
        assertColor(2, 0xff00cd00);
    }

    /** A terminal reset turns the notification mode back off. */
    public void testResetClearsTheMode() {
        withTerminalSized(3, 3);
        enterString("\033[?2031h");
        enterString("\033[?2031$p");
        assertEquals("\033[?2031;1$y", mOutput.getOutputAndClear());
        // RIS.
        enterString("\033c");
        mOutput.getOutputAndClear();
        enterString("\033[?2031$p");
        assertEquals("\033[?2031;2$y", mOutput.getOutputAndClear());
        setBackground(LIGHT);
        assertEquals("No report after a reset turned the mode off", "", mOutput.getOutputAndClear());
    }
}
