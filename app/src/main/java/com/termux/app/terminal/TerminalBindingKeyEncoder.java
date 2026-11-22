package com.termux.app.terminal;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.GhosttyKeys;
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

    /**
     * A {@code send-key} stroke through the library encoder of the Fast engine, which writes the bytes
     * itself from the terminal's live modes.
     *
     * @return the bytes written, or -1 when the library does not encode this terminal's keys (Standard)
     *         or the stroke has no key; {@link #encode} then has it
     */
    static int sendOnLibrary(@NonNull String normalizedStroke, @NonNull TerminalEmulator emulator) {
        if (!emulator.usesLibraryInputEncoders()) return -1;
        boolean ctrl = false, alt = false, shift = false;
        String token = "";
        for (String part : normalizedStroke.split("\\+")) {
            switch (part) {
                case "ctrl": ctrl = true; break;
                case "alt": alt = true; break;
                case "shift": shift = true; break;
                default: token = part; break;
            }
        }
        Integer keyCode = TerminalKeyBindingResolver.keyCodeForToken(token);
        if (keyCode == null) return -1;
        int key = GhosttyKeys.fromAndroidKeyCode(keyCode);
        Character typed = printable(keyCode, shift);
        Character plain = printable(keyCode, false);
        if (key == GhosttyKeys.KEY_UNIDENTIFIED && typed == null) return -1;
        int mods = (ctrl ? GhosttyKeys.MOD_CTRL : 0) | (alt ? GhosttyKeys.MOD_ALT : 0)
            | (shift ? GhosttyKeys.MOD_SHIFT : 0);
        int consumed = shift && typed != null && !typed.equals(plain) ? GhosttyKeys.MOD_SHIFT : 0;
        return emulator.sendLibraryKey(key, GhosttyKeys.ACTION_PRESS, mods, consumed,
            plain == null ? 0 : plain, typed == null ? "" : String.valueOf(typed), false);
    }

    /**
     * One key of the Fn-combo table (Fn+e is Escape, Fn+. is Ctrl+backslash, the rest are printable)
     * through the library encoder, with Alt as a modifier when asked.
     *
     * @return the bytes written, or -1 when the library does not encode this terminal's keys
     */
    static int sendCodePointOnLibrary(@NonNull TerminalEmulator emulator, int codePoint, boolean alt) {
        if (!emulator.usesLibraryInputEncoders()) return -1;
        int mods = alt ? GhosttyKeys.MOD_ALT : 0;
        if (codePoint == 27)
            return emulator.sendLibraryKey(GhosttyKeys.KEY_ESCAPE, GhosttyKeys.ACTION_PRESS, mods, 0, 0, "", false);
        if (codePoint == 28) {
            codePoint = '\\';
            mods |= GhosttyKeys.MOD_CTRL;
        }
        int consumed = 0;
        if (GhosttyKeys.shiftForCodePoint(codePoint)) {
            mods |= GhosttyKeys.MOD_SHIFT;
            consumed = GhosttyKeys.MOD_SHIFT;
        }
        return emulator.sendLibraryKey(GhosttyKeys.keyForCodePoint(codePoint), GhosttyKeys.ACTION_PRESS, mods,
            consumed, GhosttyKeys.unshiftedForCodePoint(codePoint), new String(Character.toChars(codePoint)), false);
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
