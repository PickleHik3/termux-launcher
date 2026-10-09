package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The sanitiser table: control characters, non-speech drops, joining. */
public class VoiceTextSanitizerTest {

    @Test
    public void ordinaryTextIsLeftAsSpoken() {
        assertEquals("ls.", VoiceTextSanitizer.clean("ls."));
        assertEquals("git status.", VoiceTextSanitizer.clean("git status."));
        assertEquals("what is this?", VoiceTextSanitizer.clean("what is this?"));
        assertEquals("LS.", VoiceTextSanitizer.clean("LS."));
        assertEquals("Git", VoiceTextSanitizer.clean("Git"));
        assertEquals("Please summarise the README.", VoiceTextSanitizer.clean("Please summarise the README."));
        assertEquals("sudo apt update.", VoiceTextSanitizer.clean(" sudo apt update. "));
    }

    @Test
    public void innerPunctuationSurvives() {
        assertEquals("cd ~/.config.", VoiceTextSanitizer.clean("cd ~/.config."));
        assertEquals("V1.2", VoiceTextSanitizer.clean("V1.2"));
        assertEquals("", VoiceTextSanitizer.clean("..."));
        assertEquals("", VoiceTextSanitizer.clean("   "));
    }

    @Test
    public void controlCharactersBecomeASpaceAndRunsCollapse() {
        assertEquals("ls rm -rf", VoiceTextSanitizer.clean("ls\n\nrm -rf"));
        assertEquals("git status", VoiceTextSanitizer.clean("git\rstatus"));
        assertEquals("a b", VoiceTextSanitizer.clean("a\u0000\u0001\u0007b"));
    }

    @Test
    public void nonSpeechCaptionsAndPunctuationOnlySegmentsAreDropped() {
        assertEquals("", VoiceTextSanitizer.clean("[Music]"));
        assertEquals("", VoiceTextSanitizer.clean("[BLANK_AUDIO]"));
        assertEquals("", VoiceTextSanitizer.clean("(B)"));
        assertEquals("", VoiceTextSanitizer.clean("*"));
        assertEquals("", VoiceTextSanitizer.clean("¶¶"));
        assertEquals("", VoiceTextSanitizer.clean("."));
        // A segment is dropped only when nothing but such spans and punctuation is left;
        // real speech that happens to carry one still comes through untouched.
        assertEquals("ls [pause]", VoiceTextSanitizer.clean("ls [pause]"));
    }

    @Test
    public void consecutiveTextSegmentsAreJoinedWithOneSpace() {
        assertEquals("ls", VoiceTextSanitizer.join(false, "ls"));
        assertEquals(" -la", VoiceTextSanitizer.join(true, "-la"));
        assertEquals("", VoiceTextSanitizer.join(true, ""));
    }
}
