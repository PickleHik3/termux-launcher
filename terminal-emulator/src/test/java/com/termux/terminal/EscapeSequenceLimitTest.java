package com.termux.terminal;

/** Regression tests for parser recovery after bounded escape strings are exceeded. */
public class EscapeSequenceLimitTest extends TerminalTestCase {

    public void testOversizedCsiIsAbortedAndPlainTextResumes() {
        withTerminalSized(20, 3);
        enterString("\033[" + repeat('1', TerminalEmulator.MAX_CSI_SEQUENCE_LENGTH + 1) + "VISIBLE");
        assertTrue(mTerminal.getScreen().getTranscriptText().contains("VISIBLE"));
    }

    public void testOversizedOscIsNotAppliedAndPlainTextResumes() {
        withTerminalSized(20, 3);
        enterString("\033]0;" + repeat('x', TerminalEmulator.MAX_STRING_SEQUENCE_LENGTH) + "VISIBLE");
        assertNull(mTerminal.getTitle());
        assertTrue(mTerminal.getScreen().getTranscriptText().contains("VISIBLE"));
    }

    public void testOversizedDcsIsAbortedAndPlainTextResumes() {
        withTerminalSized(20, 3);
        enterString("\033P" + repeat('x', TerminalEmulator.MAX_STRING_SEQUENCE_LENGTH + 1) + "VISIBLE");
        assertTrue(mTerminal.getScreen().getTranscriptText().contains("VISIBLE"));
    }

    public void testOversizedApcIsAbsorbedThroughItsTerminatorWithoutLeaking() {
        // Deliberate contract change: an oversized APC used to abort at the cap and print its
        // remainder as text — which is how a big kitty graphics payload became a wall of base64.
        // Now the remainder is swallowed up to the String Terminator, the way xterm and kitty
        // treat overlong string sequences, and plain text resumes only after it.
        withTerminalSized(20, 3);
        enterString("\033_" + repeat('x', TerminalEmulator.MAX_STRING_SEQUENCE_LENGTH + 1)
            + "HIDDEN\033\\VISIBLE");
        assertFalse(mTerminal.getScreen().getTranscriptText().contains("HIDDEN"));
        assertFalse(mTerminal.getScreen().getTranscriptText().contains("x"));
        assertTrue(mTerminal.getScreen().getTranscriptText().contains("VISIBLE"));
    }

    public void testOversizedApcCancelledByCanStillRecovers() {
        withTerminalSized(20, 3);
        enterString("\033_" + repeat('x', TerminalEmulator.MAX_STRING_SEQUENCE_LENGTH + 1)
            + '\030' + "VISIBLE");
        assertTrue(mTerminal.getScreen().getTranscriptText().contains("VISIBLE"));
    }

    public void testCanCancelsEveryStringSequence() {
        String[] prefixes = {"\033]0;hidden", "\033Phidden", "\033_hidden"};
        for (String prefix : prefixes) {
            withTerminalSized(20, 2);
            enterString(prefix + '\030' + "VISIBLE");
            assertTrue("prefix=" + prefix,
                mTerminal.getScreen().getTranscriptText().contains("VISIBLE"));
        }
    }

    public void testSupplementaryCodePointCannotCrossOscLimit() {
        withTerminalSized(20, 2);
        enterString("\033]0;" + repeat('x', TerminalEmulator.MAX_STRING_SEQUENCE_LENGTH - 2)
            + "\ud83d\ude00VISIBLE");
        assertNull(mTerminal.getTitle());
        assertTrue(mTerminal.getScreen().getTranscriptText().contains("VISIBLE"));
    }

    public void testStringTerminatorsAreAcceptedAtTheExactLimit() {
        withTerminalSized(20, 2);
        enterString("\033]999;" + repeat('x', TerminalEmulator.MAX_STRING_SEQUENCE_LENGTH - 4) + '\007');
        enterString("\033P" + repeat('x', TerminalEmulator.MAX_STRING_SEQUENCE_LENGTH) + "\033\\");
        enterString("\033_" + repeat('x', TerminalEmulator.MAX_STRING_SEQUENCE_LENGTH) + "\033\\");
        enterString("VISIBLE");
        assertTrue(mTerminal.getScreen().getTranscriptText().contains("VISIBLE"));
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
