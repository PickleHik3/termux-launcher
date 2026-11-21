package com.termux.app.x11;

import android.view.KeyEvent;

/**
 * Which key of a US keyboard types a character, and whether Shift is held for it. Printable ASCII
 * goes to X as real key presses, the way a physical keyboard types: the server then needs no
 * keymap change per character, which a slow client sees as a stale map and answers with wrong
 * characters. Characters with no US key are not here and go as text.
 */
final class X11AsciiKeys {

    private static final int[] CODES = new int[128];
    private static final boolean[] SHIFTED = new boolean[128];

    static {
        for (char c = 'a'; c <= 'z'; c++) set(c, KeyEvent.KEYCODE_A + (c - 'a'), false);
        for (char c = 'A'; c <= 'Z'; c++) set(c, KeyEvent.KEYCODE_A + (c - 'A'), true);
        for (char c = '0'; c <= '9'; c++) set(c, KeyEvent.KEYCODE_0 + (c - '0'), false);
        String shiftedDigits = ")!@#$%^&*(";
        for (int i = 0; i < shiftedDigits.length(); i++)
            set(shiftedDigits.charAt(i), KeyEvent.KEYCODE_0 + i, true);
        set(' ', KeyEvent.KEYCODE_SPACE, false);
        set('\t', KeyEvent.KEYCODE_TAB, false);
        set('\n', KeyEvent.KEYCODE_ENTER, false);
        set('\r', KeyEvent.KEYCODE_ENTER, false);
        pair('-', '_', KeyEvent.KEYCODE_MINUS);
        pair('=', '+', KeyEvent.KEYCODE_EQUALS);
        pair('[', '{', KeyEvent.KEYCODE_LEFT_BRACKET);
        pair(']', '}', KeyEvent.KEYCODE_RIGHT_BRACKET);
        pair('\\', '|', KeyEvent.KEYCODE_BACKSLASH);
        pair(';', ':', KeyEvent.KEYCODE_SEMICOLON);
        pair('\'', '"', KeyEvent.KEYCODE_APOSTROPHE);
        pair(',', '<', KeyEvent.KEYCODE_COMMA);
        pair('.', '>', KeyEvent.KEYCODE_PERIOD);
        pair('/', '?', KeyEvent.KEYCODE_SLASH);
        pair('`', '~', KeyEvent.KEYCODE_GRAVE);
    }

    private X11AsciiKeys() {}

    private static void set(char c, int code, boolean shift) {
        CODES[c] = code;
        SHIFTED[c] = shift;
    }

    private static void pair(char plain, char shifted, int code) {
        set(plain, code, false);
        set(shifted, code, true);
    }

    /** The Android keycode that types {@code c}, or {@link KeyEvent#KEYCODE_UNKNOWN}. */
    static int keyCode(char c) {
        return c < CODES.length ? CODES[c] : KeyEvent.KEYCODE_UNKNOWN;
    }

    /** Whether {@code c} needs Shift held on its key. */
    static boolean needsShift(char c) {
        return c < SHIFTED.length && SHIFTED[c];
    }
}
