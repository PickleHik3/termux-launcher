package com.termux.terminal;

/**
 * Turns a cell as libghostty-vt describes it into the {@link TextStyle} long the renderer reads.
 * Colours arrive packed as {@code tag << 24 | value}: tag 0 is "the default", 1 a palette index in
 * the low byte, 2 an 0xRRGGBB colour.
 */
final class GhosttyStyle {

    static final int COLOR_TAG_NONE = 0;
    static final int COLOR_TAG_PALETTE = 1;
    static final int COLOR_TAG_RGB = 2;

    /** libghostty-vt style flags, bits 0-7 of {@link GhosttyNative#CELL_ATTRS}. */
    static final int FLAG_BOLD = 1;
    static final int FLAG_ITALIC = 1 << 1;
    static final int FLAG_FAINT = 1 << 2;
    static final int FLAG_BLINK = 1 << 3;
    static final int FLAG_INVERSE = 1 << 4;
    static final int FLAG_INVISIBLE = 1 << 5;
    static final int FLAG_STRIKETHROUGH = 1 << 6;
    static final int FLAG_OVERLINE = 1 << 7;
    static final int UNDERLINE_SHIFT = 8;

    private GhosttyStyle() {}

    /** The style long for a cell's attributes and packed foreground and background. */
    static long toTextStyle(int attrs, int foreground, int background) {
        int underline = underlineStyle(attrs);
        int effect = effect(attrs);
        if (underline != TextStyle.UNDERLINE_STYLE_NONE) effect |= TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
        return TextStyle.encode(color(foreground, TextStyle.COLOR_INDEX_FOREGROUND),
            color(background, TextStyle.COLOR_INDEX_BACKGROUND), effect, underline);
    }

    /** The underline colour as {@link TerminalRow} stores it: a palette index or 0xFFRRGGBB. */
    static int decorationColor(int packed) {
        return color(packed, TextStyle.DECORATION_COLOR_DEFAULT);
    }

    /** A packed colour as an index (0-255, or {@code fallback}) or as 0xFFRRGGBB. */
    static int color(int packed, int fallback) {
        switch (packed >>> 24) {
            case COLOR_TAG_PALETTE:
                return packed & 0xff;
            case COLOR_TAG_RGB:
                return 0xff000000 | (packed & 0xffffff);
            default:
                return fallback;
        }
    }

    /** The {@link TextStyle} effect bits for the flags; overline has no counterpart and is dropped. */
    static int effect(int attrs) {
        int effect = 0;
        if ((attrs & FLAG_BOLD) != 0) effect |= TextStyle.CHARACTER_ATTRIBUTE_BOLD;
        if ((attrs & FLAG_ITALIC) != 0) effect |= TextStyle.CHARACTER_ATTRIBUTE_ITALIC;
        if ((attrs & FLAG_FAINT) != 0) effect |= TextStyle.CHARACTER_ATTRIBUTE_DIM;
        if ((attrs & FLAG_BLINK) != 0) effect |= TextStyle.CHARACTER_ATTRIBUTE_BLINK;
        if ((attrs & FLAG_INVERSE) != 0) effect |= TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
        if ((attrs & FLAG_INVISIBLE) != 0) effect |= TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE;
        if ((attrs & FLAG_STRIKETHROUGH) != 0) effect |= TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
        return effect;
    }

    /** One of the {@code TextStyle.UNDERLINE_STYLE_*} values; both libraries number them alike. */
    static int underlineStyle(int attrs) {
        int style = (attrs >> UNDERLINE_SHIFT) & 0b111;
        return style > TextStyle.UNDERLINE_STYLE_MAX ? TextStyle.UNDERLINE_STYLE_SINGLE : style;
    }

    /** The cell's width kind, one of {@code GhosttyNative.WIDE_*}. */
    static int wide(int attrs) {
        return (attrs >> 16) & 0b11;
    }

    /** How many code points follow the cell's first one in its grapheme cluster. */
    static int extraCodePoints(int attrs) {
        return (attrs >>> 20) & 0xff;
    }
}
