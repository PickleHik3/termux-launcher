package com.termux.app.terminal;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.KeyHandler;
import com.termux.terminal.TerminalEmulator;

/** Encodes a configured {@code send-key} stroke using the focused terminal's modes. */
final class TerminalBindingKeyEncoder {

    private TerminalBindingKeyEncoder() {}

    @Nullable
    static String encode(@NonNull String normalizedStroke, @NonNull TerminalEmulator emulator) {
        return encode(normalizedStroke, emulator.isCursorKeysApplicationMode(),
            emulator.isKeypadApplicationMode());
    }

    @Nullable
    static String encode(@NonNull String normalizedStroke, boolean cursorApplication,
                         boolean keypadApplication) {
        boolean ctrl = false, alt = false, shift = false;
        String key = "";
        for (String part : normalizedStroke.split("\\+")) {
            switch (part) {
                case "ctrl": ctrl = true; break;
                case "alt": alt = true; break;
                case "shift": shift = true; break;
                default: key = part; break;
            }
        }
        Integer keyCode = TerminalKeyBindingResolver.keyCodeForToken(key);
        if (keyCode == null) return null;
        int modifiers = (ctrl ? KeyHandler.KEYMOD_CTRL : 0)
            | (alt ? KeyHandler.KEYMOD_ALT : 0)
            | (shift ? KeyHandler.KEYMOD_SHIFT : 0);
        String special = KeyHandler.getCode(keyCode, modifiers,
            cursorApplication, keypadApplication);
        if (special != null) return special;

        Character printable = printable(keyCode, shift);
        if (printable == null) return null;
        int codePoint = printable;
        if (ctrl) codePoint = controlCode(codePoint);
        if (codePoint < 0) return null;
        String text = new String(Character.toChars(codePoint));
        return alt ? "\033" + text : text;
    }

    @Nullable
    private static Character printable(int keyCode, boolean shift) {
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            char base = (char) ('a' + keyCode - KeyEvent.KEYCODE_A);
            return shift ? Character.toUpperCase(base) : base;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            String plain = "0123456789";
            String shifted = ")!@#$%^&*(";
            int index = keyCode - KeyEvent.KEYCODE_0;
            return (shift ? shifted : plain).charAt(index);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_MINUS: return shift ? '_' : '-';
            case KeyEvent.KEYCODE_EQUALS: return shift ? '+' : '=';
            case KeyEvent.KEYCODE_PLUS: return '+';
            case KeyEvent.KEYCODE_LEFT_BRACKET: return shift ? '{' : '[';
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return shift ? '}' : ']';
            case KeyEvent.KEYCODE_BACKSLASH: return shift ? '|' : '\\';
            case KeyEvent.KEYCODE_SEMICOLON: return shift ? ':' : ';';
            case KeyEvent.KEYCODE_APOSTROPHE: return shift ? '"' : '\'';
            case KeyEvent.KEYCODE_COMMA: return shift ? '<' : ',';
            case KeyEvent.KEYCODE_PERIOD: return shift ? '>' : '.';
            case KeyEvent.KEYCODE_SLASH: return shift ? '?' : '/';
            case KeyEvent.KEYCODE_GRAVE: return shift ? '~' : '`';
            default: return null;
        }
    }

    private static int controlCode(int codePoint) {
        if (codePoint >= 'a' && codePoint <= 'z') return codePoint - 'a' + 1;
        if (codePoint >= 'A' && codePoint <= 'Z') return codePoint - 'A' + 1;
        switch (codePoint) {
            case ' ': case '@': case '2': return 0;
            case '[': case '{': case '3': return 27;
            case '\\': case '|': case '4': return 28;
            case ']': case '}': case '5': return 29;
            case '^': case '6': return 30;
            case '_': case '/': case '?': case '7': return 31;
            case '8': return 127;
            default: return -1;
        }
    }
}
