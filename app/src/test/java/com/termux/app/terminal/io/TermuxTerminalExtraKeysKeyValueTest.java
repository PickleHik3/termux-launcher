package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.view.KeyEvent;

import org.junit.Test;

import juloo.keyboard2.KeyValue;

/** The extra-keys column's keys as the in-app keyboard would have produced them. */
public class TermuxTerminalExtraKeysKeyValueTest {

    @Test
    public void namedKeysBecomeKeyEvents() {
        assertEquals(KeyEvent.KEYCODE_ESCAPE, TermuxTerminalExtraKeys.keyValueFor("ESC").getKeyevent());
        assertEquals(KeyEvent.KEYCODE_TAB, TermuxTerminalExtraKeys.keyValueFor("TAB").getKeyevent());
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, TermuxTerminalExtraKeys.keyValueFor("LEFT").getKeyevent());
        assertEquals(KeyEvent.KEYCODE_MOVE_HOME, TermuxTerminalExtraKeys.keyValueFor("HOME").getKeyevent());
        assertEquals(KeyEvent.KEYCODE_F1, TermuxTerminalExtraKeys.keyValueFor("F1").getKeyevent());
    }

    @Test
    public void aSingleCharacterStaysACharacterSoCtrlCanChordIt() {
        KeyValue dash = TermuxTerminalExtraKeys.keyValueFor("-");
        assertEquals(KeyValue.Kind.Char, dash.getKind());
        assertEquals('-', dash.getChar());
    }

    @Test
    public void longerTextIsText() {
        KeyValue value = TermuxTerminalExtraKeys.keyValueFor("ls ");
        assertEquals(KeyValue.Kind.String, value.getKind());
        assertEquals("ls ", value.getString());
        assertNull(TermuxTerminalExtraKeys.keyValueFor(""));
    }
}
