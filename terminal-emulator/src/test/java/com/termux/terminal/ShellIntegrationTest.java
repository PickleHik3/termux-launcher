package com.termux.terminal;

/**
 * OSC 133 shell integration marks: the rows a prompt, typed input, and command output start on, plus
 * the exit status the shell reports when a command finishes.
 */
public class ShellIntegrationTest extends TerminalTestCase {

    private byte markAt(int row) {
        return mTerminal.getScreen().getShellIntegrationMark(row);
    }

    /** A row holds one mark: several marks on the same row leave the last one, as kitty does. */
    public void testMarksLandOnTheCursorRowAndTheLastOneWins() {
        withTerminalSized(6, 4).enterString("\033]133;A\033\\$ \033]133;B\033\\ls\033]133;C\033\\\r\nout");
        assertEquals(TerminalRow.MARK_OUTPUT_START, markAt(0));
        assertEquals(TerminalRow.MARK_NONE, markAt(1));
    }

    /** A, B and C on separate rows keep their own marks. */
    public void testEachMarkIsRecordedOnItsOwnRow() {
        withTerminalSized(4, 4).enterString("\033]133;A\033\\a\r\n\033]133;B\033\\b\r\n\033]133;C\033\\c");
        assertEquals(TerminalRow.MARK_PROMPT_START, markAt(0));
        assertEquals(TerminalRow.MARK_COMMAND_START, markAt(1));
        assertEquals(TerminalRow.MARK_OUTPUT_START, markAt(2));
    }

    public void testExitCodeIsReported() {
        withTerminalSized(4, 4);
        assertEquals(TerminalEmulator.COMMAND_EXIT_CODE_UNKNOWN, mTerminal.getLastCommandExitCode());
        enterString("\033]133;D;7\033\\");
        assertEquals(7, mTerminal.getLastCommandExitCode());
        // Trailing shell bookkeeping parameters after the status are ignored.
        enterString("\033]133;D;0;aid=1\033\\");
        assertEquals(0, mTerminal.getLastCommandExitCode());
        // A D without a status resets it to unknown rather than keeping a stale value.
        enterString("\033]133;D\033\\");
        assertEquals(TerminalEmulator.COMMAND_EXIT_CODE_UNKNOWN, mTerminal.getLastCommandExitCode());
    }

    public void testGarbageStatusLeavesTheCodeUnknown() {
        withTerminalSized(4, 4).enterString("\033]133;D;notanumber\033\\");
        assertEquals(TerminalEmulator.COMMAND_EXIT_CODE_UNKNOWN, mTerminal.getLastCommandExitCode());
    }

    public void testIntegrationIsDetectedAndUnknownMarksAreIgnored() {
        withTerminalSized(4, 4);
        assertFalse(mTerminal.hasShellIntegration());
        enterString("\033]133;Z\033\\");
        assertFalse("An unknown mark must not claim integration is set up", mTerminal.hasShellIntegration());
        enterString("\033]133;A\033\\");
        assertTrue(mTerminal.hasShellIntegration());
    }

    public void testCommandLifecycleRunsFromOutputStartUntilExitOrPrompt() {
        withTerminalSized(4, 4);
        assertFalse(mTerminal.isShellIntegrationCommandRunning());
        enterString("\033]133;C\033\\");
        assertTrue(mTerminal.isShellIntegrationCommandRunning());
        enterString("\033]133;D;0\033\\");
        assertFalse(mTerminal.isShellIntegrationCommandRunning());
        enterString("\033]133;C\033\\");
        enterString("\033]133;A\033\\");
        assertFalse(mTerminal.isShellIntegrationCommandRunning());
    }

    public void testFindPromptRowSearchesBothWays() {
        withTerminalSized(4, 4).enterString("\033]133;A\033\\a\r\nb\r\n\033]133;A\033\\c\r\nd");
        assertEquals(0, mTerminal.findPromptRow(2, true));
        assertEquals(2, mTerminal.findPromptRow(0, false));
        assertEquals(Integer.MIN_VALUE, mTerminal.findPromptRow(0, true));
        assertEquals(Integer.MIN_VALUE, mTerminal.findPromptRow(2, false));
    }

    public void testMarksFollowRowsIntoHistory() {
        withTerminalSized(4, 2).enterString("\033]133;A\033\\a\r\nb\r\nc");
        assertEquals(TerminalRow.MARK_PROMPT_START, markAt(-1));
        assertEquals(-1, mTerminal.findPromptRow(0, true));
    }

    public void testMarkSurvivesReflow() {
        withTerminalSized(4, 4).enterString("\033]133;A\033\\abc");
        resize(3, 4);
        assertEquals(TerminalRow.MARK_PROMPT_START, markAt(0));
    }

    public void testEraseClearsMarks() {
        withTerminalSized(4, 4).enterString("\033]133;A\033\\a\033[H\033[2J");
        assertEquals(TerminalRow.MARK_NONE, markAt(0));
    }

    public void testResetClearsIntegrationState() {
        withTerminalSized(4, 4).enterString("\033]133;A\033\\a\033]133;D;3\033\\");
        mTerminal.reset();
        assertFalse(mTerminal.hasShellIntegration());
        assertFalse(mTerminal.isShellIntegrationCommandRunning());
        assertEquals(TerminalEmulator.COMMAND_EXIT_CODE_UNKNOWN, mTerminal.getLastCommandExitCode());
    }

    /** The marks are invisible: nothing of the sequence may reach the screen. */
    public void testMarksPrintNothing() {
        withTerminalSized(6, 2).enterString("\033]133;A\033\\ok\033]133;C\033\\");
        assertTrue(mTerminal.getScreen().getTranscriptText().contains("ok"));
        assertFalse(mTerminal.getScreen().getTranscriptText().contains("133"));
    }
}
