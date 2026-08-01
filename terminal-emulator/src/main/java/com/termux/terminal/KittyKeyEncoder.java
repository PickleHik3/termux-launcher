package com.termux.terminal;

import android.view.KeyEvent;

/**
 * Encoder for the kitty keyboard protocol, the {@code CSI u} form that removes the ambiguity of legacy
 * key encoding and can report key release, repeat, and the text a key produced.
 * <p>
 * The protocol is opt-in per session: an application turns on the enhancements it wants and the
 * terminal keeps using legacy encoding for everything it did not ask for. That makes the return value
 * of {@link #encode} three-valued:
 * </p>
 * <ul>
 *   <li>{@code null} - this event is not the protocol's business; encode it with {@link KeyHandler}.</li>
 *   <li>{@code ""} - the protocol says this event produces no bytes, so it must be swallowed rather
 *       than falling through to the legacy encoder.</li>
 *   <li>anything else - the bytes to write to the shell.</li>
 * </ul>
 *
 * @see <a href="https://sw.kovidgoyal.net/kitty/keyboard-protocol/">the protocol specification</a>
 */
public final class KittyKeyEncoder {

    /** Report keys that legacy encoding cannot distinguish, such as Esc and ctrl+key, as CSI u. */
    public static final int FLAG_DISAMBIGUATE = 0b1;

    /** Report key repeat and key release, not only key press. */
    public static final int FLAG_REPORT_EVENTS = 0b10;

    /** Also report the shifted and base-layout form of a key, to help shortcut matching. */
    public static final int FLAG_REPORT_ALTERNATE_KEYS = 0b100;

    /** Report every key as an escape code, including the ones that would produce text. */
    public static final int FLAG_REPORT_ALL_KEYS = 0b1000;

    /** Include the text a key produced in its escape code. Only defined together with all keys. */
    public static final int FLAG_REPORT_TEXT = 0b10000;

    /** Every defined flag. Bits outside this are rejected rather than stored. */
    public static final int FLAGS_MASK = 0b11111;

    public static final int EVENT_PRESS = 1;

    public static final int EVENT_REPEAT = 2;

    public static final int EVENT_RELEASE = 3;

    public static final int MOD_SHIFT = 0b1;

    public static final int MOD_ALT = 0b10;

    public static final int MOD_CTRL = 0b100;

    public static final int MOD_SUPER = 0b1000;

    public static final int MOD_HYPER = 0b10000;

    public static final int MOD_META = 0b100000;

    public static final int MOD_CAPS_LOCK = 0b1000000;

    public static final int MOD_NUM_LOCK = 0b10000000;

    /** The lock modifiers, which legacy programs must not see on text producing keys. */
    private static final int MOD_LOCKS = MOD_CAPS_LOCK | MOD_NUM_LOCK;

    /** Returned by {@link #functionalKey} for a key code with no functional encoding. */
    private static final int NOT_FUNCTIONAL = -1;

    /**
     * A functional key packed as {@code (number << 8) | finalByte}, so that the table below stays a
     * plain switch with no allocation and no map lookup on the input path.
     */
    private static int key(int number, char finalByte) {
        return (number << 8) | finalByte;
    }

    private KittyKeyEncoder() {
    }

    /**
     * Encode one key event.
     *
     * @param androidKeyCode     the {@link KeyEvent} key code.
     * @param unshiftedCodePoint the code point the key produces with no modifiers, which the protocol
     *                           uses as the key's identity. 0 when the key produces no text.
     * @param shiftedCodePoint   the code point the key produces with shift held, or 0. Only reported
     *                           when alternate key reporting is on and shift is held.
     * @param textCodePoint      the code point this event actually produced, or 0. Only reported when
     *                           text reporting is on.
     * @param modifiers          a bit set of the {@code MOD_*} values.
     * @param eventType          one of {@code EVENT_PRESS}, {@code EVENT_REPEAT}, {@code EVENT_RELEASE}.
     * @param flags              the session's active progressive enhancement flags.
     * @return the bytes to send, an empty string to swallow the event, or null to use legacy encoding.
     */
    public static String encode(int androidKeyCode, int unshiftedCodePoint, int shiftedCodePoint, int textCodePoint, int modifiers, int eventType, int flags) {
        if ((flags & FLAGS_MASK) == 0)
            return null;
        final boolean disambiguate = (flags & FLAG_DISAMBIGUATE) != 0;
        final boolean reportEvents = (flags & FLAG_REPORT_EVENTS) != 0;
        final boolean reportAll = (flags & FLAG_REPORT_ALL_KEYS) != 0;
        final boolean reportAlternates = (flags & FLAG_REPORT_ALTERNATE_KEYS) != 0;
        // Text reporting is an enhancement of all-keys reporting and undefined without it.
        final boolean reportText = (flags & FLAG_REPORT_TEXT) != 0 && reportAll;
        if (eventType == EVENT_REPEAT && !reportEvents)
            eventType = EVENT_PRESS;
        if (eventType == EVENT_RELEASE && !reportEvents)
            return "";
        final int functional = functionalKey(androidKeyCode);
        final boolean isModifierKey = isModifierKeyCode(androidKeyCode);
        if (isModifierKey && !reportAll) {
            // A modifier press is not an event in legacy terms, and must not reach the shell as one.
            return "";
        }
        int number;
        char finalByte;
        boolean textProducing = false;
        if (functional != NOT_FUNCTIONAL) {
            number = functional >> 8;
            finalByte = (char) (functional & 0xff);
            if (isLegacyC0Key(androidKeyCode) && !reportAll) {
                // Enter, Tab and Backspace keep their legacy bytes so that a user can still type
                // "reset" at a shell prompt after a program exits without clearing the mode.
                return null;
            }
        } else if (unshiftedCodePoint > 0) {
            number = Character.toLowerCase(unshiftedCodePoint);
            finalByte = 'u';
            textProducing = true;
            if (!reportAll) {
                boolean ctrlOrAlt = (modifiers & (MOD_CTRL | MOD_ALT)) != 0;
                // Only the combinations legacy encoding gets wrong are taken over; plain and
                // shift-only typing must keep producing plain text.
                if (!disambiguate || !ctrlOrAlt)
                    return null;
            }
        } else {
            // A key this encoder has no number for. The legacy handler may still know it.
            return null;
        }
        if (textProducing && !reportAll)
            modifiers &= ~MOD_LOCKS;
        StringBuilder out = new StringBuilder(16);
        out.append("\033[");
        boolean letterForm = finalByte != 'u' && finalByte != '~';
        boolean hasModifierField = modifiers != 0 || eventType != EVENT_PRESS;
        boolean hasTextField = reportText && isReportableText(textCodePoint);
        if (letterForm && !hasModifierField && !hasTextField) {
            // "CSI A" rather than "CSI 1 A": the number is 1 by default and must then be omitted.
            out.append(finalByte);
            return out.toString();
        }
        out.append(number);
        if (reportAlternates && finalByte == 'u' && (modifiers & MOD_SHIFT) != 0 && shiftedCodePoint > 0 && shiftedCodePoint != number) {
            out.append(':').append(shiftedCodePoint);
        }
        if (hasModifierField) {
            out.append(';').append(1 + modifiers);
            if (eventType != EVENT_PRESS)
                out.append(':').append(eventType);
        } else if (hasTextField) {
            // The text field is the third one, so its separator is needed even with no modifiers.
            out.append(';');
        }
        if (hasTextField)
            out.append(';').append(textCodePoint);
        out.append(finalByte);
        return out.toString();
    }

    /** Whether a code point may appear in the text field, which excludes all control characters. */
    private static boolean isReportableText(int codePoint) {
        if (codePoint < 0x20 || codePoint == 0x7f)
            return false;
        // C1 controls are excluded as well.
        return codePoint < 0x80 || codePoint > 0x9f;
    }

    /**
     * Enter, Tab and Backspace: the keys that keep their legacy bytes unless all keys are reported.
     * Escape is deliberately not one of them - disambiguating it is the point of the protocol.
     */
    private static boolean isLegacyC0Key(int androidKeyCode) {
        switch(androidKeyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_TAB:
            case KeyEvent.KEYCODE_DEL:
                return true;
            default:
                return false;
        }
    }

    public static boolean isModifierKeyCode(int androidKeyCode) {
        switch(androidKeyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
            case KeyEvent.KEYCODE_META_LEFT:
            case KeyEvent.KEYCODE_META_RIGHT:
                return true;
            default:
                return false;
        }
    }

    /**
     * The protocol's number and CSI final byte for a non-text key, or {@link #NOT_FUNCTIONAL}.
     * <p>
     * Numbers below 32 and 127 are the C0 keys; the rest come from the Unicode Private Use Area block
     * the specification reserves for functional keys.
     * </p>
     */
    private static int functionalKey(int androidKeyCode) {
        switch(androidKeyCode) {
            case KeyEvent.KEYCODE_ESCAPE:
                return key(27, 'u');
            case KeyEvent.KEYCODE_ENTER:
                return key(13, 'u');
            case KeyEvent.KEYCODE_TAB:
                return key(9, 'u');
            case KeyEvent.KEYCODE_DEL:
                return key(127, 'u');
            case KeyEvent.KEYCODE_INSERT:
                return key(2, '~');
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return key(3, '~');
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return key(1, 'D');
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return key(1, 'C');
            case KeyEvent.KEYCODE_DPAD_UP:
                return key(1, 'A');
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return key(1, 'B');
            case KeyEvent.KEYCODE_PAGE_UP:
                return key(5, '~');
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return key(6, '~');
            case KeyEvent.KEYCODE_MOVE_HOME:
                return key(1, 'H');
            case KeyEvent.KEYCODE_MOVE_END:
                return key(1, 'F');
            case KeyEvent.KEYCODE_CAPS_LOCK:
                return key(57358, 'u');
            case KeyEvent.KEYCODE_SCROLL_LOCK:
                return key(57359, 'u');
            case KeyEvent.KEYCODE_NUM_LOCK:
                return key(57360, 'u');
            case KeyEvent.KEYCODE_SYSRQ:
                return key(57361, 'u');
            case KeyEvent.KEYCODE_BREAK:
                return key(57362, 'u');
            case KeyEvent.KEYCODE_MENU:
                return key(57363, 'u');
            case KeyEvent.KEYCODE_F1:
                return key(1, 'P');
            case KeyEvent.KEYCODE_F2:
                return key(1, 'Q');
            case KeyEvent.KEYCODE_F3:
                // Never "CSI R", which would collide with the Cursor Position Report.
                return key(13, '~');
            case KeyEvent.KEYCODE_F4:
                return key(1, 'S');
            case KeyEvent.KEYCODE_F5:
                return key(15, '~');
            case KeyEvent.KEYCODE_F6:
                return key(17, '~');
            case KeyEvent.KEYCODE_F7:
                return key(18, '~');
            case KeyEvent.KEYCODE_F8:
                return key(19, '~');
            case KeyEvent.KEYCODE_F9:
                return key(20, '~');
            case KeyEvent.KEYCODE_F10:
                return key(21, '~');
            case KeyEvent.KEYCODE_F11:
                return key(23, '~');
            case KeyEvent.KEYCODE_F12:
                return key(24, '~');
            case KeyEvent.KEYCODE_NUMPAD_0:
                return key(57399, 'u');
            case KeyEvent.KEYCODE_NUMPAD_1:
                return key(57400, 'u');
            case KeyEvent.KEYCODE_NUMPAD_2:
                return key(57401, 'u');
            case KeyEvent.KEYCODE_NUMPAD_3:
                return key(57402, 'u');
            case KeyEvent.KEYCODE_NUMPAD_4:
                return key(57403, 'u');
            case KeyEvent.KEYCODE_NUMPAD_5:
                return key(57404, 'u');
            case KeyEvent.KEYCODE_NUMPAD_6:
                return key(57405, 'u');
            case KeyEvent.KEYCODE_NUMPAD_7:
                return key(57406, 'u');
            case KeyEvent.KEYCODE_NUMPAD_8:
                return key(57407, 'u');
            case KeyEvent.KEYCODE_NUMPAD_9:
                return key(57408, 'u');
            case KeyEvent.KEYCODE_NUMPAD_DOT:
                return key(57409, 'u');
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE:
                return key(57410, 'u');
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY:
                return key(57411, 'u');
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                return key(57412, 'u');
            case KeyEvent.KEYCODE_NUMPAD_ADD:
                return key(57413, 'u');
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return key(57414, 'u');
            case KeyEvent.KEYCODE_NUMPAD_EQUALS:
                return key(57415, 'u');
            case KeyEvent.KEYCODE_NUMPAD_COMMA:
                return key(57416, 'u');
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                return key(57428, 'u');
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                return key(57429, 'u');
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                return key(57430, 'u');
            case KeyEvent.KEYCODE_MEDIA_STOP:
                return key(57432, 'u');
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                return key(57433, 'u');
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                return key(57434, 'u');
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                return key(57435, 'u');
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                return key(57436, 'u');
            case KeyEvent.KEYCODE_MEDIA_RECORD:
                return key(57437, 'u');
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return key(57438, 'u');
            case KeyEvent.KEYCODE_VOLUME_UP:
                return key(57439, 'u');
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return key(57440, 'u');
            case KeyEvent.KEYCODE_SHIFT_LEFT:
                return key(57441, 'u');
            case KeyEvent.KEYCODE_CTRL_LEFT:
                return key(57442, 'u');
            case KeyEvent.KEYCODE_ALT_LEFT:
                return key(57443, 'u');
            case KeyEvent.KEYCODE_META_LEFT:
                return key(57444, 'u');
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return key(57447, 'u');
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return key(57448, 'u');
            case KeyEvent.KEYCODE_ALT_RIGHT:
                return key(57449, 'u');
            case KeyEvent.KEYCODE_META_RIGHT:
                return key(57450, 'u');
            default:
                return NOT_FUNCTIONAL;
        }
    }
}
