package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The terminal cleanup table: trailing punctuation, single-word case, joining. */
public class VoiceTerminalCleanupTest {

    @Test
    public void trailingSentencePunctuationIsStripped() {
        assertEquals("ls", VoiceTerminalCleanup.clean("ls."));
        assertEquals("git status", VoiceTerminalCleanup.clean("git status."));
        assertEquals("what is this", VoiceTerminalCleanup.clean("what is this?"));
        assertEquals("make it stop", VoiceTerminalCleanup.clean("make it stop!!"));
        assertEquals("sudo apt update", VoiceTerminalCleanup.clean(" sudo apt update. "));
    }

    @Test
    public void aSingleWordIsLowercasedAndProseIsLeftAsSpoken() {
        assertEquals("ls", VoiceTerminalCleanup.clean("LS."));
        assertEquals("git", VoiceTerminalCleanup.clean("Git"));
        assertEquals("Please summarise the README", VoiceTerminalCleanup.clean("Please summarise the README."));
        assertEquals("Get status", VoiceTerminalCleanup.clean("Get status"));
    }

    @Test
    public void innerPunctuationSurvives() {
        assertEquals("cd ~/.config", VoiceTerminalCleanup.clean("cd ~/.config."));
        assertEquals("v1.2", VoiceTerminalCleanup.clean("V1.2"));
        assertEquals("", VoiceTerminalCleanup.clean("..."));
        assertEquals("", VoiceTerminalCleanup.clean("   "));
    }

    @Test
    public void controlCharactersBecomeASpaceAndRunsCollapse() {
        assertEquals("ls rm -rf", VoiceTerminalCleanup.clean("ls\n\nrm -rf"));
        assertEquals("git status", VoiceTerminalCleanup.clean("git\rstatus"));
        assertEquals("a b", VoiceTerminalCleanup.clean("a\u0000\u0001\u0007b"));
    }

    @Test
    public void nonSpeechCaptionsAndPunctuationOnlySegmentsAreDropped() {
        assertEquals("", VoiceTerminalCleanup.clean("[Music]"));
        assertEquals("", VoiceTerminalCleanup.clean("[BLANK_AUDIO]"));
        assertEquals("", VoiceTerminalCleanup.clean("(B)"));
        assertEquals("", VoiceTerminalCleanup.clean("*"));
        assertEquals("", VoiceTerminalCleanup.clean("¶¶"));
        assertEquals("", VoiceTerminalCleanup.clean("."));
        // A segment is dropped only when nothing but such spans and punctuation is left;
        // real speech that happens to carry one still comes through untouched.
        assertEquals("ls [pause]", VoiceTerminalCleanup.clean("ls [pause]"));
    }

    @Test
    public void consecutiveTextSegmentsAreJoinedWithOneSpace() {
        assertEquals("ls", VoiceTerminalCleanup.join(false, "ls"));
        assertEquals(" -la", VoiceTerminalCleanup.join(true, "-la"));
        assertEquals("", VoiceTerminalCleanup.join(true, ""));
    }
}
