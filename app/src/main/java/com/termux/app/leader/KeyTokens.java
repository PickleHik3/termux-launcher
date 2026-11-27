package com.termux.app.leader;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How a key is spelled in a binding ({@code q}, {@code left}, {@code minus}), shared by the
 * binding resolver and the leader's chord check. Matching is on key codes at physical US
 * positions, so bindings reach the same keys from non-Latin layouts.
 */
public final class KeyTokens {

    private KeyTokens() {}

    /** The stroke token a key code is spelled with in a binding, or null when it has none. */
    @Nullable
    public static String keyToken(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return String.valueOf((char) ('a' + (keyCode - KeyEvent.KEYCODE_A)));
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return String.valueOf((char) ('0' + (keyCode - KeyEvent.KEYCODE_0)));
        }
        if (keyCode >= KeyEvent.KEYCODE_F1 && keyCode <= KeyEvent.KEYCODE_F12) {
            return "f" + (keyCode - KeyEvent.KEYCODE_F1 + 1);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
            case KeyEvent.KEYCODE_DPAD_UP: return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
            case KeyEvent.KEYCODE_LEFT_BRACKET: return "[";
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return "]";
            // Spelled out because '+' is the stroke separator and '=' / '-' read
            // badly in a config file.
            case KeyEvent.KEYCODE_MINUS: return "minus";
            case KeyEvent.KEYCODE_EQUALS: return "equals";
            case KeyEvent.KEYCODE_PLUS: return "plus";
            case KeyEvent.KEYCODE_SLASH: return "/";
            case KeyEvent.KEYCODE_BACKSLASH: return "\\";
            case KeyEvent.KEYCODE_SEMICOLON: return ";";
            case KeyEvent.KEYCODE_APOSTROPHE: return "'";
            case KeyEvent.KEYCODE_COMMA: return ",";
            case KeyEvent.KEYCODE_PERIOD: return ".";
            case KeyEvent.KEYCODE_GRAVE: return "`";
            case KeyEvent.KEYCODE_SPACE: return "space";
            case KeyEvent.KEYCODE_TAB: return "tab";
            case KeyEvent.KEYCODE_ENTER: return "enter";
            case KeyEvent.KEYCODE_ESCAPE: return "escape";
            case KeyEvent.KEYCODE_DEL: return "backspace";
            case KeyEvent.KEYCODE_FORWARD_DEL: return "delete";
            case KeyEvent.KEYCODE_PAGE_UP: return "pageup";
            case KeyEvent.KEYCODE_PAGE_DOWN: return "pagedown";
            case KeyEvent.KEYCODE_MOVE_HOME: return "home";
            case KeyEvent.KEYCODE_MOVE_END: return "end";
            default: return null;
        }
    }

    /**
     * The stroke token a printable character would carry, so a soft key that only knows the
     * character it types can still be matched against a binding. Public because the in-app
     * keyboard uses it for both jobs it has: lighting the caps a prefix binds, and turning a
     * pressed cap into the key event the binding resolver reads.
     */
    @NonNull
    public static String tokenForChar(char c) {
        char lower = Character.toLowerCase(c);
        switch (lower) {
            // Spelled out for the same reason keyToken spells them out.
            case '-': return "minus";
            case '=': return "equals";
            case '+': return "plus";
            case ' ': return "space";
            default: return String.valueOf(lower);
        }
    }

    @Nullable
    public static Integer keyCodeForToken(@NonNull String token) {
        for (int keyCode = KeyEvent.KEYCODE_A; keyCode <= KeyEvent.KEYCODE_Z; keyCode++)
            if (token.equals(keyToken(keyCode))) return keyCode;
        for (int keyCode = KeyEvent.KEYCODE_0; keyCode <= KeyEvent.KEYCODE_9; keyCode++)
            if (token.equals(keyToken(keyCode))) return keyCode;
        for (int keyCode = KeyEvent.KEYCODE_F1; keyCode <= KeyEvent.KEYCODE_F12; keyCode++)
            if (token.equals(keyToken(keyCode))) return keyCode;
        int[] supported = {KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_LEFT_BRACKET,
            KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS,
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_BACKSLASH,
            KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_COMMA,
            KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END};
        for (int keyCode : supported) if (token.equals(keyToken(keyCode))) return keyCode;
        return null;
    }
}
