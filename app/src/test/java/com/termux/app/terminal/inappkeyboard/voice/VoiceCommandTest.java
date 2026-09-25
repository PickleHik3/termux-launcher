package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * The segment → command table: a whole segment matches, anything longer stays text. Default mode
 * needs a trailing "key"; the "Bare command words" setting (bareWordsAllowed = true) restores the
 * original bare-word matching.
 */
public class VoiceCommandTest {

    @Test
    public void defaultModeNeedsTheKeySuffix() {
        assertEquals(VoiceCommand.ENTER, VoiceCommand.classify("enter key", false));
        assertEquals(VoiceCommand.ENTER, VoiceCommand.classify(" Enter Key.", false));
        assertEquals(VoiceCommand.ENTER, VoiceCommand.classify("Return key", false));
        assertEquals(VoiceCommand.ENTER, VoiceCommand.classify("send key", false));
        assertEquals(VoiceCommand.ENTER, VoiceCommand.classify("Submit key!", false));
        assertEquals(VoiceCommand.TAB, VoiceCommand.classify("tab key", false));
        assertEquals(VoiceCommand.ESC, VoiceCommand.classify("Escape key", false));
        assertEquals(VoiceCommand.BACKSPACE, VoiceCommand.classify("backspace key", false));
        assertEquals(VoiceCommand.BACKSPACE, VoiceCommand.classify("Delete key.", false));
        assertEquals(VoiceCommand.SPACE, VoiceCommand.classify("space key", false));
        assertEquals(VoiceCommand.CTRL_C, VoiceCommand.classify("control c key", false));
        assertEquals(VoiceCommand.CTRL_C, VoiceCommand.classify("Control-C key.", false));
        assertEquals(VoiceCommand.CTRL_C, VoiceCommand.classify("Ctrl+C Key", false));
        assertEquals(VoiceCommand.CTRL_C, VoiceCommand.classify("ctrl c key", false));
        // What small.en makes of "control c key" (replay rig, 2026-09-25).
        assertEquals(VoiceCommand.CTRL_C, VoiceCommand.classify("c key", false));
        assertEquals(VoiceCommand.CTRL_C, VoiceCommand.classify("Ckey.", false));
        assertNull(VoiceCommand.classify("c", false));
        assertNull(VoiceCommand.classify("see the c key", false));
        assertEquals(VoiceCommand.CTRL_C, VoiceCommand.classify("cancel key", false));
    }

    @Test
    public void defaultModeTreatsTheBareWordAndBareKeyAsText() {
        assertNull(VoiceCommand.classify("enter", false));
        assertNull(VoiceCommand.classify("tab", false));
        assertNull(VoiceCommand.classify("key", false));
        assertNull(VoiceCommand.classify("control c", false));
        assertNull(VoiceCommand.classify("ctrl c", false));
    }

    @Test
    public void bareWordsAllowedRestoresTheOriginalMatching() {
        assertEquals(VoiceCommand.ENTER, VoiceCommand.classify("enter", true));
        assertEquals(VoiceCommand.ENTER, VoiceCommand.classify(" Enter.", true));
        assertEquals(VoiceCommand.ENTER, VoiceCommand.classify("Return", true));
        assertEquals(VoiceCommand.TAB, VoiceCommand.classify("tab", true));
        assertEquals(VoiceCommand.ESC, VoiceCommand.classify("Escape", true));
        assertEquals(VoiceCommand.BACKSPACE, VoiceCommand.classify("backspace", true));
        assertEquals(VoiceCommand.SPACE, VoiceCommand.classify("space", true));
        assertEquals(VoiceCommand.CTRL_C, VoiceCommand.classify("control c", true));
        assertEquals(VoiceCommand.CTRL_C, VoiceCommand.classify("cancel", true));
        // The "key" suffix still matches too, in either mode.
        assertEquals(VoiceCommand.ENTER, VoiceCommand.classify("enter key", true));
    }

    @Test
    public void onlyAWholeSegmentMatchesInEitherMode() {
        for (boolean bareWordsAllowed : new boolean[] {false, true}) {
            assertNull(VoiceCommand.classify("enter the directory", bareWordsAllowed));
            assertNull(VoiceCommand.classify("press enter key", bareWordsAllowed));
            assertNull(VoiceCommand.classify("tab completion", bareWordsAllowed));
            assertNull(VoiceCommand.classify("git status", bareWordsAllowed));
            assertNull(VoiceCommand.classify("ls", bareWordsAllowed));
            assertNull(VoiceCommand.classify("", bareWordsAllowed));
            assertNull(VoiceCommand.classify("   ", bareWordsAllowed));
            assertNull(VoiceCommand.classify(null, bareWordsAllowed));
        }
    }

    @Test
    public void theKeysAreTheOnesTheLayoutNames() {
        assertEquals("enter", VoiceCommand.ENTER.keyName);
        assertEquals("tab", VoiceCommand.TAB.keyName);
        assertEquals("esc", VoiceCommand.ESC.keyName);
        assertEquals("backspace", VoiceCommand.BACKSPACE.keyName);
        assertEquals("space", VoiceCommand.SPACE.keyName);
        assertEquals("c", VoiceCommand.CTRL_C.keyName);
        assertEquals(true, VoiceCommand.CTRL_C.ctrl);
        assertEquals(false, VoiceCommand.ENTER.ctrl);
    }

    @Test
    public void normalizationLowercasesStripsPunctuationAndSaysCtrl() {
        assertEquals("ctrl c", VoiceCommand.normalize("  Control - C. "));
        assertEquals("enter the directory", VoiceCommand.normalize("Enter, the directory!"));
        assertEquals("ctrl x", VoiceCommand.normalize("control x"));
        assertEquals("controller", VoiceCommand.normalize("controller"));
    }

    @Test
    public void chipLabelsMatchTheKeySent() {
        assertEquals("⏎ Enter", VoiceCommand.ENTER.chipLabel());
        assertEquals("⇥ Tab", VoiceCommand.TAB.chipLabel());
        assertEquals("Esc", VoiceCommand.ESC.chipLabel());
        assertEquals("⌫", VoiceCommand.BACKSPACE.chipLabel());
        assertEquals("Space", VoiceCommand.SPACE.chipLabel());
        assertEquals("Ctrl+C", VoiceCommand.CTRL_C.chipLabel());
    }
}
