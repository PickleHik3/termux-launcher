package com.termux.shared.termux.extrakeys;

import androidx.annotation.NonNull;

/**
 * The code points a key cap uses for an icon drawn from a drawable instead of a font glyph: one
 * table, read by this module's key maps and by the app, which registers the drawable behind each
 * one with {@code NerdFontSpans.registerDrawableSymbol}. They sit in the plane-16 private-use
 * block, outside every Nerd Font range, so nothing the symbols face draws can collide with them.
 *
 * <p>Stored key rows carry these as literal {@code display} characters once a key picker has
 * written one, so a number here is never reused for another icon.
 */
public final class ExtraKeyIcons {

    private ExtraKeyIcons() {}

    public static final int ASSISTANT = 0x10FF00;
    public static final int PAGE_SWITCHER = 0x10FF01;
    public static final int HOME = 0x10FF02;
    public static final int TERMINAL = 0x10FF03;
    public static final int DISPLAY = 0x10FF04;
    public static final int MOUSE = 0x10FF05;
    public static final int KEYBOARD = 0x10FF06;
    public static final int APPS = 0x10FF07;
    public static final int MIC = 0x10FF08;
    public static final int SPLIT = 0x10FF09;
    public static final int SESSIONS = 0x10FF0A;
    public static final int PASTE = 0x10FF0B;
    public static final int WINDOW_ADD = 0x10FF0C;
    public static final int WINDOW_CLOSE = 0x10FF0D;
    public static final int COMMAND = 0x10FF0E;
    public static final int ARROW_LEFT = 0x10FF10;
    public static final int ARROW_RIGHT = 0x10FF11;
    public static final int ARROW_UP = 0x10FF12;
    public static final int ARROW_DOWN = 0x10FF13;
    public static final int ENTER = 0x10FF14;
    public static final int TAB = 0x10FF15;
    public static final int BACKSPACE = 0x10FF16;
    public static final int DELETE_FORWARD = 0x10FF17;
    public static final int MENU = 0x10FF18;
    public static final int SCROLL = 0x10FF19;
    public static final int KEY_HOME = 0x10FF1A;
    public static final int KEY_END = 0x10FF1B;
    public static final int PAGE_UP = 0x10FF1C;
    public static final int PAGE_DOWN = 0x10FF1D;

    /** Every code point above; the app registers a drawable for each. */
    public static final int[] ALL = {
        ASSISTANT, PAGE_SWITCHER, HOME, TERMINAL, DISPLAY, MOUSE, KEYBOARD, APPS, MIC, SPLIT,
        SESSIONS, PASTE, WINDOW_ADD, WINDOW_CLOSE, COMMAND, ARROW_LEFT, ARROW_RIGHT, ARROW_UP,
        ARROW_DOWN, ENTER, TAB, BACKSPACE, DELETE_FORWARD, MENU, SCROLL, KEY_HOME, KEY_END,
        PAGE_UP, PAGE_DOWN,
    };

    /** The one-character label that draws {@code codePoint}'s icon. */
    @NonNull
    public static String string(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    /** Whether {@code label} holds any of this table's icons, which a screen reader cannot say. */
    public static boolean containsIcon(@androidx.annotation.Nullable CharSequence label) {
        if (label == null) return false;
        int index = 0;
        while (index < label.length()) {
            int codePoint = Character.codePointAt(label, index);
            if (isIcon(codePoint)) return true;
            index += Character.charCount(codePoint);
        }
        return false;
    }

    /** Whether {@code codePoint} is one of this table's icons. */
    public static boolean isIcon(int codePoint) {
        for (int icon : ALL) if (icon == codePoint) return true;
        return false;
    }
}
