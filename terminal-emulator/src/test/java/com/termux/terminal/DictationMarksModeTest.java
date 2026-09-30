package com.termux.terminal;

/**
 * Dictation marks, private mode 7727: the emulator only keeps the bit, which the launcher's voice
 * dictation reads to decide whether to wrap its pastes in OSC 7727 marks. Mode off is the default.
 */
public class DictationMarksModeTest extends TerminalTestCase {

    public void testOffByDefault() {
        withTerminalSized(10, 4);
        assertFalse(mTerminal.isDictationMarksEnabled());
    }

    public void testSetAndResetSayNothing() {
        withTerminalSized(10, 4);
        enterString("\033[?7727h");
        assertTrue(mTerminal.isDictationMarksEnabled());
        assertEquals("Setting says nothing of its own", "", mOutput.getOutputAndClear());
        enterString("\033[?7727l");
        assertFalse(mTerminal.isDictationMarksEnabled());
        assertEquals("Resetting says nothing of its own", "", mOutput.getOutputAndClear());
    }

    /** The mode is independent of bracketed paste; the dictation code checks both. */
    public void testIndependentOfBracketedPaste() {
        withTerminalSized(10, 4);
        enterString("\033[?7727h");
        assertTrue(mTerminal.isDictationMarksEnabled());
        assertFalse(mTerminal.isBracketedPasteMode());
        enterString("\033[?2004h");
        assertTrue(mTerminal.isBracketedPasteMode());
        enterString("\033[?7727l");
        assertFalse(mTerminal.isDictationMarksEnabled());
        assertTrue(mTerminal.isBracketedPasteMode());
    }

    /** DECRQM: 1 while set, 2 while not, so a program can detect support. */
    public void testDecrqmReportsTheMode() {
        withTerminalSized(10, 4);
        assertEnteringStringGivesResponse("\033[?7727$p", "\033[?7727;2$y");
        enterString("\033[?7727h");
        assertEnteringStringGivesResponse("\033[?7727$p", "\033[?7727;1$y");
        enterString("\033[?7727l");
        assertEnteringStringGivesResponse("\033[?7727$p", "\033[?7727;2$y");
    }

    public void testRisResetsTheMode() {
        withTerminalSized(10, 4);
        enterString("\033[?7727h");
        enterString("\033c");
        assertFalse(mTerminal.isDictationMarksEnabled());
    }

    public void testDecstrResetsTheMode() {
        withTerminalSized(10, 4);
        enterString("\033[?7727h");
        enterString("\033[!p");
        assertFalse(mTerminal.isDictationMarksEnabled());
    }

    /** XTSAVE / XTRESTORE carry the mode like the other private modes. */
    public void testSaveAndRestore() {
        withTerminalSized(10, 4);
        enterString("\033[?7727h");
        enterString("\033[?7727s");
        enterString("\033[?7727l");
        assertFalse(mTerminal.isDictationMarksEnabled());
        enterString("\033[?7727r");
        assertTrue(mTerminal.isDictationMarksEnabled());
    }

    /** A paste by itself is untouched by the mode: the marks are the dictation code's business. */
    public void testPasteIsUnchangedByTheMode() {
        withTerminalSized(10, 4);
        enterString("\033[?2004h\033[?7727h");
        mOutput.getOutputAndClear();
        mTerminal.paste("hello");
        assertEquals("\033[200~hello\033[201~", mOutput.getOutputAndClear());
    }
}
