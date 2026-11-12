package com.termux.terminal;

import android.view.KeyEvent;

/**
 * The values of libghostty-vt's key API (ghostty/vt/key/event.h, pinned 1.0.0) and the tables that
 * turn Android key codes, meta state and typed characters into them. Pure Java: {@link KeyEvent}
 * is read for its compile-time int constants only.
 */
public final class GhosttyKeys {

    private GhosttyKeys() {}

    // GhosttyKeyAction.
    public static final int ACTION_RELEASE = 0;
    public static final int ACTION_PRESS = 1;
    public static final int ACTION_REPEAT = 2;

    // GhosttyMods. A side bit is set when the right-hand key is the one held.
    public static final int MOD_SHIFT = 1 << 0;
    public static final int MOD_CTRL = 1 << 1;
    public static final int MOD_ALT = 1 << 2;
    public static final int MOD_SUPER = 1 << 3;
    public static final int MOD_CAPS_LOCK = 1 << 4;
    public static final int MOD_NUM_LOCK = 1 << 5;
    public static final int MOD_SHIFT_SIDE = 1 << 6;
    public static final int MOD_CTRL_SIDE = 1 << 7;
    public static final int MOD_ALT_SIDE = 1 << 8;
    public static final int MOD_SUPER_SIDE = 1 << 9;

    // GhosttyKey (W3C physical key codes), the ones the mapping uses and their neighbours.
    public static final int KEY_UNIDENTIFIED = 0;
    public static final int KEY_BACKQUOTE = 1;
    public static final int KEY_BACKSLASH = 2;
    public static final int KEY_BRACKET_LEFT = 3;
    public static final int KEY_BRACKET_RIGHT = 4;
    public static final int KEY_COMMA = 5;
    public static final int KEY_DIGIT_0 = 6;
    public static final int KEY_DIGIT_1 = 7;
    public static final int KEY_DIGIT_2 = 8;
    public static final int KEY_DIGIT_3 = 9;
    public static final int KEY_DIGIT_4 = 10;
    public static final int KEY_DIGIT_5 = 11;
    public static final int KEY_DIGIT_6 = 12;
    public static final int KEY_DIGIT_7 = 13;
    public static final int KEY_DIGIT_8 = 14;
    public static final int KEY_DIGIT_9 = 15;
    public static final int KEY_EQUAL = 16;
    public static final int KEY_INTL_RO = 18;
    public static final int KEY_INTL_YEN = 19;
    public static final int KEY_A = 20;  // KEY_A + n is the letter n of the alphabet, through KEY_Z = 45
    public static final int KEY_Z = 45;
    public static final int KEY_MINUS = 46;
    public static final int KEY_PERIOD = 47;
    public static final int KEY_QUOTE = 48;
    public static final int KEY_SEMICOLON = 49;
    public static final int KEY_SLASH = 50;
    public static final int KEY_ALT_LEFT = 51;
    public static final int KEY_ALT_RIGHT = 52;
    public static final int KEY_BACKSPACE = 53;
    public static final int KEY_CAPS_LOCK = 54;
    public static final int KEY_CONTEXT_MENU = 55;
    public static final int KEY_CONTROL_LEFT = 56;
    public static final int KEY_CONTROL_RIGHT = 57;
    public static final int KEY_ENTER = 58;
    public static final int KEY_META_LEFT = 59;
    public static final int KEY_META_RIGHT = 60;
    public static final int KEY_SHIFT_LEFT = 61;
    public static final int KEY_SHIFT_RIGHT = 62;
    public static final int KEY_SPACE = 63;
    public static final int KEY_TAB = 64;
    public static final int KEY_CONVERT = 65;
    public static final int KEY_KANA_MODE = 66;
    public static final int KEY_NON_CONVERT = 67;
    public static final int KEY_DELETE = 68;
    public static final int KEY_END = 69;
    public static final int KEY_HELP = 70;
    public static final int KEY_HOME = 71;
    public static final int KEY_INSERT = 72;
    public static final int KEY_PAGE_DOWN = 73;
    public static final int KEY_PAGE_UP = 74;
    public static final int KEY_ARROW_DOWN = 75;
    public static final int KEY_ARROW_LEFT = 76;
    public static final int KEY_ARROW_RIGHT = 77;
    public static final int KEY_ARROW_UP = 78;
    public static final int KEY_NUM_LOCK = 79;
    public static final int KEY_NUMPAD_0 = 80;  // KEY_NUMPAD_0 + n through KEY_NUMPAD_9 = 89
    public static final int KEY_NUMPAD_1 = 81;
    public static final int KEY_NUMPAD_2 = 82;
    public static final int KEY_NUMPAD_3 = 83;
    public static final int KEY_NUMPAD_4 = 84;
    public static final int KEY_NUMPAD_5 = 85;
    public static final int KEY_NUMPAD_6 = 86;
    public static final int KEY_NUMPAD_7 = 87;
    public static final int KEY_NUMPAD_8 = 88;
    public static final int KEY_NUMPAD_9 = 89;
    public static final int KEY_NUMPAD_ADD = 90;
    public static final int KEY_NUMPAD_COMMA = 94;
    public static final int KEY_NUMPAD_DECIMAL = 95;
    public static final int KEY_NUMPAD_DIVIDE = 96;
    public static final int KEY_NUMPAD_ENTER = 97;
    public static final int KEY_NUMPAD_EQUAL = 98;
    public static final int KEY_NUMPAD_MULTIPLY = 104;
    public static final int KEY_NUMPAD_PAREN_LEFT = 105;
    public static final int KEY_NUMPAD_PAREN_RIGHT = 106;
    public static final int KEY_NUMPAD_SUBTRACT = 107;
    public static final int KEY_ESCAPE = 120;
    public static final int KEY_F1 = 121;  // KEY_F1 + n through KEY_F12 = 132
    public static final int KEY_F2 = 122;
    public static final int KEY_F3 = 123;
    public static final int KEY_F4 = 124;
    public static final int KEY_F5 = 125;
    public static final int KEY_F6 = 126;
    public static final int KEY_F7 = 127;
    public static final int KEY_F8 = 128;
    public static final int KEY_F9 = 129;
    public static final int KEY_F10 = 130;
    public static final int KEY_F11 = 131;
    public static final int KEY_F12 = 132;
    public static final int KEY_FN = 146;
    public static final int KEY_PRINT_SCREEN = 148;
    public static final int KEY_SCROLL_LOCK = 149;
    public static final int KEY_PAUSE = 150;
    public static final int KEY_BROWSER_BACK = 151;
    public static final int KEY_BROWSER_FORWARD = 153;
    public static final int KEY_BROWSER_SEARCH = 156;
    public static final int KEY_EJECT = 158;
    public static final int KEY_LAUNCH_MAIL = 161;
    public static final int KEY_MEDIA_PLAY_PAUSE = 162;
    public static final int KEY_MEDIA_STOP = 164;
    public static final int KEY_MEDIA_TRACK_NEXT = 165;
    public static final int KEY_MEDIA_TRACK_PREVIOUS = 166;
    public static final int KEY_POWER = 167;
    public static final int KEY_SLEEP = 168;
    public static final int KEY_AUDIO_VOLUME_DOWN = 169;
    public static final int KEY_AUDIO_VOLUME_MUTE = 170;
    public static final int KEY_AUDIO_VOLUME_UP = 171;
    public static final int KEY_WAKE_UP = 172;
    public static final int KEY_COPY = 173;
    public static final int KEY_CUT = 174;
    public static final int KEY_PASTE = 175;

    /**
     * The physical key of an Android key code, or {@link #KEY_UNIDENTIFIED}. KEYCODE_PLUS, AT, POUND
     * and STAR are characters of the layout with no physical key of their own, so they map to
     * UNIDENTIFIED too; the text they produce still reaches the program.
     */
    public static int fromAndroidKeyCode(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z)
            return KEY_A + (keyCode - KeyEvent.KEYCODE_A);
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)
            return KEY_DIGIT_0 + (keyCode - KeyEvent.KEYCODE_0);
        if (keyCode >= KeyEvent.KEYCODE_F1 && keyCode <= KeyEvent.KEYCODE_F12)
            return KEY_F1 + (keyCode - KeyEvent.KEYCODE_F1);
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9)
            return KEY_NUMPAD_0 + (keyCode - KeyEvent.KEYCODE_NUMPAD_0);
        switch (keyCode) {
            case KeyEvent.KEYCODE_GRAVE: return KEY_BACKQUOTE;
            case KeyEvent.KEYCODE_MINUS: return KEY_MINUS;
            case KeyEvent.KEYCODE_EQUALS: return KEY_EQUAL;
            case KeyEvent.KEYCODE_LEFT_BRACKET: return KEY_BRACKET_LEFT;
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return KEY_BRACKET_RIGHT;
            case KeyEvent.KEYCODE_BACKSLASH: return KEY_BACKSLASH;
            case KeyEvent.KEYCODE_SEMICOLON: return KEY_SEMICOLON;
            case KeyEvent.KEYCODE_APOSTROPHE: return KEY_QUOTE;
            case KeyEvent.KEYCODE_COMMA: return KEY_COMMA;
            case KeyEvent.KEYCODE_PERIOD: return KEY_PERIOD;
            case KeyEvent.KEYCODE_SLASH: return KEY_SLASH;
            case KeyEvent.KEYCODE_SPACE: return KEY_SPACE;
            case KeyEvent.KEYCODE_ENTER: return KEY_ENTER;
            case KeyEvent.KEYCODE_TAB: return KEY_TAB;
            case KeyEvent.KEYCODE_ESCAPE: return KEY_ESCAPE;
            case KeyEvent.KEYCODE_DEL: return KEY_BACKSPACE;
            case KeyEvent.KEYCODE_FORWARD_DEL: return KEY_DELETE;
            case KeyEvent.KEYCODE_INSERT: return KEY_INSERT;
            case KeyEvent.KEYCODE_MOVE_HOME: return KEY_HOME;
            case KeyEvent.KEYCODE_MOVE_END: return KEY_END;
            case KeyEvent.KEYCODE_PAGE_UP: return KEY_PAGE_UP;
            case KeyEvent.KEYCODE_PAGE_DOWN: return KEY_PAGE_DOWN;
            case KeyEvent.KEYCODE_DPAD_UP: return KEY_ARROW_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return KEY_ARROW_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return KEY_ARROW_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return KEY_ARROW_RIGHT;
            case KeyEvent.KEYCODE_NUMPAD_DOT: return KEY_NUMPAD_DECIMAL;
            case KeyEvent.KEYCODE_NUMPAD_ENTER: return KEY_NUMPAD_ENTER;
            case KeyEvent.KEYCODE_NUMPAD_ADD: return KEY_NUMPAD_ADD;
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT: return KEY_NUMPAD_SUBTRACT;
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY: return KEY_NUMPAD_MULTIPLY;
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE: return KEY_NUMPAD_DIVIDE;
            case KeyEvent.KEYCODE_NUMPAD_EQUALS: return KEY_NUMPAD_EQUAL;
            case KeyEvent.KEYCODE_NUMPAD_COMMA: return KEY_NUMPAD_COMMA;
            case KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN: return KEY_NUMPAD_PAREN_LEFT;
            case KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN: return KEY_NUMPAD_PAREN_RIGHT;
            case KeyEvent.KEYCODE_SHIFT_LEFT: return KEY_SHIFT_LEFT;
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return KEY_SHIFT_RIGHT;
            case KeyEvent.KEYCODE_CTRL_LEFT: return KEY_CONTROL_LEFT;
            case KeyEvent.KEYCODE_CTRL_RIGHT: return KEY_CONTROL_RIGHT;
            case KeyEvent.KEYCODE_ALT_LEFT: return KEY_ALT_LEFT;
            case KeyEvent.KEYCODE_ALT_RIGHT: return KEY_ALT_RIGHT;
            case KeyEvent.KEYCODE_META_LEFT: return KEY_META_LEFT;
            case KeyEvent.KEYCODE_META_RIGHT: return KEY_META_RIGHT;
            case KeyEvent.KEYCODE_CAPS_LOCK: return KEY_CAPS_LOCK;
            case KeyEvent.KEYCODE_NUM_LOCK: return KEY_NUM_LOCK;
            case KeyEvent.KEYCODE_SCROLL_LOCK: return KEY_SCROLL_LOCK;
            case KeyEvent.KEYCODE_FUNCTION: return KEY_FN;
            case KeyEvent.KEYCODE_BREAK: return KEY_PAUSE;
            case KeyEvent.KEYCODE_SYSRQ: return KEY_PRINT_SCREEN;
            case KeyEvent.KEYCODE_MENU: return KEY_CONTEXT_MENU;
            case KeyEvent.KEYCODE_HELP: return KEY_HELP;
            case KeyEvent.KEYCODE_RO: return KEY_INTL_RO;
            case KeyEvent.KEYCODE_YEN: return KEY_INTL_YEN;
            case KeyEvent.KEYCODE_HENKAN: return KEY_CONVERT;
            case KeyEvent.KEYCODE_MUHENKAN: return KEY_NON_CONVERT;
            case KeyEvent.KEYCODE_KANA: return KEY_KANA_MODE;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return KEY_MEDIA_PLAY_PAUSE;
            case KeyEvent.KEYCODE_MEDIA_STOP: return KEY_MEDIA_STOP;
            case KeyEvent.KEYCODE_MEDIA_NEXT: return KEY_MEDIA_TRACK_NEXT;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: return KEY_MEDIA_TRACK_PREVIOUS;
            case KeyEvent.KEYCODE_MEDIA_EJECT: return KEY_EJECT;
            case KeyEvent.KEYCODE_VOLUME_UP: return KEY_AUDIO_VOLUME_UP;
            case KeyEvent.KEYCODE_VOLUME_DOWN: return KEY_AUDIO_VOLUME_DOWN;
            case KeyEvent.KEYCODE_VOLUME_MUTE: return KEY_AUDIO_VOLUME_MUTE;
            case KeyEvent.KEYCODE_POWER: return KEY_POWER;
            case KeyEvent.KEYCODE_SLEEP: return KEY_SLEEP;
            case KeyEvent.KEYCODE_WAKEUP: return KEY_WAKE_UP;
            case KeyEvent.KEYCODE_ENVELOPE: return KEY_LAUNCH_MAIL;
            case KeyEvent.KEYCODE_FORWARD: return KEY_BROWSER_FORWARD;
            case KeyEvent.KEYCODE_SEARCH: return KEY_BROWSER_SEARCH;
            case KeyEvent.KEYCODE_COPY: return KEY_COPY;
            case KeyEvent.KEYCODE_CUT: return KEY_CUT;
            case KeyEvent.KEYCODE_PASTE: return KEY_PASTE;
            // KEYCODE_PLUS, AT, POUND and STAR: no physical key of their own.
            default: return KEY_UNIDENTIFIED;
        }
    }

    private static final String UNSHIFTED_PUNCT = "`-=[]\\;',./";
    private static final int[] PUNCT_KEYS = {KEY_BACKQUOTE, KEY_MINUS, KEY_EQUAL, KEY_BRACKET_LEFT,
        KEY_BRACKET_RIGHT, KEY_BACKSLASH, KEY_SEMICOLON, KEY_QUOTE, KEY_COMMA, KEY_PERIOD, KEY_SLASH};
    private static final String SHIFTED_PUNCT = "~_+{}|:\"<>?";
    private static final String SHIFTED_DIGITS = ")!@#$%^&*(";

    /** The US-layout key that types ASCII printable {@code cp}, or {@link #KEY_UNIDENTIFIED}. */
    public static int keyForCodePoint(int cp) {
        if (cp >= 'a' && cp <= 'z') return KEY_A + (cp - 'a');
        if (cp >= 'A' && cp <= 'Z') return KEY_A + (cp - 'A');
        if (cp >= '0' && cp <= '9') return KEY_DIGIT_0 + (cp - '0');
        if (cp == ' ') return KEY_SPACE;
        if (cp > 0 && cp < 0x80) {
            int i = SHIFTED_DIGITS.indexOf(cp);
            if (i >= 0) return KEY_DIGIT_0 + i;
            i = UNSHIFTED_PUNCT.indexOf(cp);
            if (i >= 0) return PUNCT_KEYS[i];
            i = SHIFTED_PUNCT.indexOf(cp);
            if (i >= 0) return PUNCT_KEYS[i];
        }
        return KEY_UNIDENTIFIED;
    }

    /** The code point that key types unshifted: {@code cp} on its own key, else {@code cp} lower-cased. */
    public static int unshiftedForCodePoint(int cp) {
        if (cp >= 'A' && cp <= 'Z') return cp + ('a' - 'A');
        if (cp > 0 && cp < 0x80) {
            int i = SHIFTED_DIGITS.indexOf(cp);
            if (i >= 0) return '0' + i;
            i = SHIFTED_PUNCT.indexOf(cp);
            if (i >= 0) return UNSHIFTED_PUNCT.charAt(i);
            return cp;
        }
        return Character.toLowerCase(cp);
    }

    /** Whether the US layout needs Shift to type ASCII printable {@code cp}. */
    public static boolean shiftForCodePoint(int cp) {
        if (cp >= 'A' && cp <= 'Z') return true;
        return cp > 0 && cp < 0x80 && (SHIFTED_DIGITS.indexOf(cp) >= 0 || SHIFTED_PUNCT.indexOf(cp) >= 0);
    }

    /** GhosttyMods of an Android meta state; a side bit is set when only the right-hand key is down. */
    public static int fromAndroidMetaState(int metaState) {
        int mods = 0;
        mods |= side(metaState, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_RIGHT_ON,
            KeyEvent.META_SHIFT_LEFT_ON, KeyEvent.META_SHIFT_RIGHT_ON, MOD_SHIFT, MOD_SHIFT_SIDE);
        mods |= side(metaState, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CTRL_RIGHT_ON,
            KeyEvent.META_CTRL_LEFT_ON, KeyEvent.META_CTRL_RIGHT_ON, MOD_CTRL, MOD_CTRL_SIDE);
        mods |= side(metaState, KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON | KeyEvent.META_ALT_RIGHT_ON,
            KeyEvent.META_ALT_LEFT_ON, KeyEvent.META_ALT_RIGHT_ON, MOD_ALT, MOD_ALT_SIDE);
        mods |= side(metaState, KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_RIGHT_ON,
            KeyEvent.META_META_LEFT_ON, KeyEvent.META_META_RIGHT_ON, MOD_SUPER, MOD_SUPER_SIDE);
        if ((metaState & KeyEvent.META_CAPS_LOCK_ON) != 0) mods |= MOD_CAPS_LOCK;
        if ((metaState & KeyEvent.META_NUM_LOCK_ON) != 0) mods |= MOD_NUM_LOCK;
        return mods;
    }

    private static int side(int meta, int anyMask, int leftBit, int rightBit, int mod, int sideMod) {
        if ((meta & anyMask) == 0) return 0;
        boolean rightOnly = (meta & rightBit) != 0 && (meta & leftBit) == 0;
        return rightOnly ? mod | sideMod : mod;
    }
}
