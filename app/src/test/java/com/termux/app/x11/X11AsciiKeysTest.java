package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class X11AsciiKeysTest {

    @Test
    public void everyPrintableAsciiCharacterHasAKey() {
        for (char c = 0x20; c <= 0x7e; c++)
            assertNotEquals("no key for '" + c + "'", KeyEvent.KEYCODE_UNKNOWN, X11AsciiKeys.keyCode(c));
    }

    @Test
    public void lettersShareAKeyAndCapitalsHoldShift() {
        for (char c = 'a'; c <= 'z'; c++) {
            char up = Character.toUpperCase(c);
            assertEquals(KeyEvent.KEYCODE_A + (c - 'a'), X11AsciiKeys.keyCode(c));
            assertEquals(X11AsciiKeys.keyCode(c), X11AsciiKeys.keyCode(up));
            assertFalse(X11AsciiKeys.needsShift(c));
            assertTrue(X11AsciiKeys.needsShift(up));
        }
    }

    @Test
    public void digitsAndTheirShiftedSymbols() {
        String shifted = ")!@#$%^&*(";
        for (int i = 0; i < 10; i++) {
            char d = (char) ('0' + i);
            assertEquals(KeyEvent.KEYCODE_0 + i, X11AsciiKeys.keyCode(d));
            assertFalse(X11AsciiKeys.needsShift(d));
            assertEquals(KeyEvent.KEYCODE_0 + i, X11AsciiKeys.keyCode(shifted.charAt(i)));
            assertTrue(X11AsciiKeys.needsShift(shifted.charAt(i)));
        }
    }

    @Test
    public void punctuationPairsShareAKey() {
        String plain = "-=[]\\;',./`";
        String shifted = "_+{}|:\"<>?~";
        for (int i = 0; i < plain.length(); i++) {
            assertEquals(X11AsciiKeys.keyCode(plain.charAt(i)), X11AsciiKeys.keyCode(shifted.charAt(i)));
            assertFalse(X11AsciiKeys.needsShift(plain.charAt(i)));
            assertTrue(X11AsciiKeys.needsShift(shifted.charAt(i)));
        }
    }

    @Test
    public void noTwoUnshiftedCharactersShareAKey() {
        Set<Integer> seen = new HashSet<>();
        for (char c = 0x20; c <= 0x7e; c++)
            if (!X11AsciiKeys.needsShift(c))
                assertTrue("'" + c + "' repeats a key", seen.add(X11AsciiKeys.keyCode(c)));
    }

    @Test
    public void spaceAndControlWhitespaceNeedNoShift() {
        assertEquals(KeyEvent.KEYCODE_SPACE, X11AsciiKeys.keyCode(' '));
        assertEquals(KeyEvent.KEYCODE_ENTER, X11AsciiKeys.keyCode('\n'));
        assertEquals(KeyEvent.KEYCODE_TAB, X11AsciiKeys.keyCode('\t'));
        assertFalse(X11AsciiKeys.needsShift(' '));
    }

    @Test
    public void nonAsciiHasNoKey() {
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, X11AsciiKeys.keyCode('é'));
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, X11AsciiKeys.keyCode((char) 0x7f));
        assertFalse(X11AsciiKeys.needsShift('é'));
    }
}
