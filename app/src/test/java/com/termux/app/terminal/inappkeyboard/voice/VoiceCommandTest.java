package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The segment → command table: a whole segment matches, anything longer stays text. */
public class VoiceCommandTest {

    @Test
    public void commandWordsSaidAloneBecomeKeys() {
        assertEquals(VoiceCommand.ENTER, VoiceCommand.classify("enter"));
        assertEquals(VoiceCommand.ENTER, VoiceCommand.classify(" Enter."));
        assertEquals(VoiceCommand.ENTER, VoiceCommand.classify("Return"));
        assertEquals(VoiceCommand.ENTER, VoiceCommand.classify("send"));
        assertEquals(VoiceCommand.ENTER, VoiceCommand.classify("Submit!"));
        assertEquals(VoiceCommand.TAB, VoiceCommand.classify("tab"));
        assertEquals(VoiceCommand.ESC, VoiceCommand.classify("Escape"));
        assertEquals(VoiceCommand.BACKSPACE, VoiceCommand.classify("backspace"));
        assertEquals(VoiceCommand.BACKSPACE, VoiceCommand.classify("Delete."));
        assertEquals(VoiceCommand.SPACE, VoiceCommand.classify("space"));
        assertEquals(VoiceCommand.CTRL_C, VoiceCommand.classify("control c"));
        assertEquals(VoiceCommand.CTRL_C, VoiceCommand.classify("Control-C."));
        assertEquals(VoiceCommand.CTRL_C, VoiceCommand.classify("Ctrl+C"));
        assertEquals(VoiceCommand.CTRL_C, VoiceCommand.classify("ctrl c"));
        assertEquals(VoiceCommand.CTRL_C, VoiceCommand.classify("cancel"));
    }

    @Test
    public void onlyAWholeSegmentMatches() {
        assertNull(VoiceCommand.classify("enter the directory"));
        assertNull(VoiceCommand.classify("press enter"));
        assertNull(VoiceCommand.classify("tab completion"));
        assertNull(VoiceCommand.classify("git status"));
        assertNull(VoiceCommand.classify("ls"));
        assertNull(VoiceCommand.classify(""));
        assertNull(VoiceCommand.classify("   "));
        assertNull(VoiceCommand.classify(null));
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
}
