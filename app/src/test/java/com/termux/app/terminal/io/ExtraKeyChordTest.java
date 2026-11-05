package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ExtraKeyChordTest {

    private static void accepts(String typed, String macro, String display) {
        ExtraKeyChord.Parsed p = ExtraKeyChord.parse(typed);
        assertNotNull(typed, p);
        assertEquals(macro, p.macro);
        assertEquals(display, p.display);
    }

    @Test
    public void acceptsCombinations() {
        accepts("ctrl+m", "CTRL m", "Ctrl+M");
        accepts("ctrl + m", "CTRL m", "Ctrl+M");
        accepts("ctrl m", "CTRL m", "Ctrl+M");
        accepts("Ctrl+F7", "CTRL F7", "Ctrl+F7");
        accepts("alt+shift+tab", "ALT SHIFT TAB", "Alt+Shift+TAB");
        accepts("Control+c", "CTRL c", "Ctrl+C");
        accepts("fn+up", "FN UP", "Fn+UP");
        accepts("ctrl+/", "CTRL /", "Ctrl+/");
    }

    @Test
    public void rejectsTheRest() {
        assertNull(ExtraKeyChord.parse("ctrl"));
        assertNull(ExtraKeyChord.parse("m"));
        assertNull(ExtraKeyChord.parse("m+ctrl"));
        assertNull(ExtraKeyChord.parse("ctrl+"));
        assertNull(ExtraKeyChord.parse("ctrl+ctrl"));
        assertNull(ExtraKeyChord.parse("ctrl+f13"));
        assertNull(ExtraKeyChord.parse("ctrl+m+n"));
        assertNull(ExtraKeyChord.parse("ctrl+hello"));
        assertNull(ExtraKeyChord.parse("ctrl++"));
        assertNull(ExtraKeyChord.parse(""));
    }

    @Test
    public void stripsStrayPlus() {
        assertEquals("CTRL m", ExtraKeyChord.stripPlus("CTRL + m"));
        assertEquals("ls ENTER", ExtraKeyChord.stripPlus("ls ENTER"));
        assertEquals("+", ExtraKeyChord.stripPlus("+"));
    }
}
