package com.termux.terminal;

/**
 * <p>
 * Encodes effects, foreground and background colors into a 64 bit long, which are stored for each cell in a terminal
 * row in {@link TerminalRow#mStyle}.
 * </p>
 * <p>
 * The bit layout is:
 * </p>
 * - 16 flags (bits 0-10 attributes, bits 11-13 underline style, bit 15 bitmap).
 * - 24 for foreground color (only 9 first bits if a color index).
 * - 24 for background color (only 9 first bits if a color index).
 * <p>
 * Bit 14 is the only unallocated bit. Per-cell state that does not fit here - the underline
 * decoration color and the OSC 8 hyperlink id - lives in side tables on {@link TerminalRow}.
 * </p>
 */
public final class TextStyle {

    public final static int CHARACTER_ATTRIBUTE_BOLD = 1;

    public final static int CHARACTER_ATTRIBUTE_ITALIC = 1 << 1;

    public final static int CHARACTER_ATTRIBUTE_UNDERLINE = 1 << 2;

    public final static int CHARACTER_ATTRIBUTE_BLINK = 1 << 3;

    public final static int CHARACTER_ATTRIBUTE_INVERSE = 1 << 4;

    public final static int CHARACTER_ATTRIBUTE_INVISIBLE = 1 << 5;

    public final static int CHARACTER_ATTRIBUTE_STRIKETHROUGH = 1 << 6;

    /**
     * The selective erase control functions (DECSED and DECSEL) can only erase characters defined as erasable.
     * <p>
     * This bit is set if DECSCA (Select Character Protection Attribute) has been used to define the characters that
     * come after it as erasable from the screen.
     * </p>
     */
    public final static int CHARACTER_ATTRIBUTE_PROTECTED = 1 << 7;

    /**
     * Dim colors. Also known as faint or half intensity.
     */
    public final static int CHARACTER_ATTRIBUTE_DIM = 1 << 8;

    /**
     * If true (24-bit) color is used for the cell for foreground.
     */
    private final static int CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND = 1 << 9;

    /**
     * If true (24-bit) color is used for the cell for foreground.
     */
    private final static int CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND = 1 << 10;

    /**
     * If true, character represents a bitmap slice, not text.
     */
    public final static int BITMAP = 1 << 15;

    /** No underline. Note that {@link #CHARACTER_ATTRIBUTE_UNDERLINE} is then also expected to be unset. */
    public final static int UNDERLINE_STYLE_NONE = 0;

    /** A single straight line, the classic SGR 4 underline. */
    public final static int UNDERLINE_STYLE_SINGLE = 1;

    /** Two straight lines, SGR 4:2 and SGR 21. */
    public final static int UNDERLINE_STYLE_DOUBLE = 2;

    /** A wavy line, SGR 4:3. Commonly used by editors to mark errors. */
    public final static int UNDERLINE_STYLE_CURLY = 3;

    /** A dotted line, SGR 4:4. */
    public final static int UNDERLINE_STYLE_DOTTED = 4;

    /** A dashed line, SGR 4:5. */
    public final static int UNDERLINE_STYLE_DASHED = 5;

    /** The highest defined underline style. See https://sw.kovidgoyal.net/kitty/underlines/. */
    public final static int UNDERLINE_STYLE_MAX = UNDERLINE_STYLE_DASHED;

    private final static int UNDERLINE_STYLE_SHIFT = 11;

    /** Bits 11-13, holding one of the {@code UNDERLINE_STYLE_*} values. */
    private final static long UNDERLINE_STYLE_MASK = 0b111L << UNDERLINE_STYLE_SHIFT;

    public final static int COLOR_INDEX_FOREGROUND = 256;

    public final static int COLOR_INDEX_BACKGROUND = 257;

    public final static int COLOR_INDEX_CURSOR = 258;

    /**
     * The 256 standard color entries and the three special (foreground, background and cursor) ones.
     */
    public final static int NUM_INDEXED_COLORS = 259;

    /**
     * The underline decoration color meaning "follow the cell's foreground color", which is what SGR
     * 59 restores and what a cell that never saw SGR 58 has.
     */
    public final static int DECORATION_COLOR_DEFAULT = COLOR_INDEX_FOREGROUND;

    /**
     * Normal foreground and background colors and no effects.
     */
    final static long NORMAL = encode(COLOR_INDEX_FOREGROUND, COLOR_INDEX_BACKGROUND, 0);

    static long encode(int foreColor, int backColor, int effect) {
        return encode(foreColor, backColor, effect, UNDERLINE_STYLE_NONE);
    }

    static long encode(int foreColor, int backColor, int effect, int underlineStyle) {
        long result = effect & 0b111111111;
        result |= ((long) underlineStyle & 0b111L) << UNDERLINE_STYLE_SHIFT;
        if ((0xff000000 & foreColor) == 0xff000000) {
            // 24-bit color.
            result |= CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND | ((foreColor & 0x00ffffffL) << 40L);
        } else {
            // Indexed color.
            result |= (foreColor & 0b111111111L) << 40;
        }
        if ((0xff000000 & backColor) == 0xff000000) {
            // 24-bit color.
            result |= CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND | ((backColor & 0x00ffffffL) << 16L);
        } else {
            // Indexed color.
            result |= (backColor & 0b111111111L) << 16L;
        }
        return result;
    }

    public static int decodeForeColor(long style) {
        if ((style & CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND) == 0) {
            return (int) ((style >>> 40) & 0b111111111L);
        } else {
            return 0xff000000 | (int) ((style >>> 40) & 0x00ffffffL);
        }
    }

    public static int decodeBackColor(long style) {
        if ((style & CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND) == 0) {
            return (int) ((style >>> 16) & 0b111111111L);
        } else {
            return 0xff000000 | (int) ((style >>> 16) & 0x00ffffffL);
        }
    }

    public static int decodeEffect(long style) {
        return (int) (style & 0b11111111111);
    }

    /** One of the {@code UNDERLINE_STYLE_*} values. */
    public static int decodeUnderlineStyle(long style) {
        return (int) ((style & UNDERLINE_STYLE_MASK) >>> UNDERLINE_STYLE_SHIFT);
    }

    /**
     * Re-encode a style with new colors and effect bits, keeping the fields that live outside of
     * them. Used by operations such as DECCARA which rewrite only the effect of existing cells and
     * must not silently drop the underline style.
     */
    public static long withColorsAndEffect(long style, int foreColor, int backColor, int effect) {
        return encode(foreColor, backColor, effect) | (style & UNDERLINE_STYLE_MASK);
    }

    public static long encodeBitmap(int num, int X, int Y) {
        return ((long) num << 16) | ((long) Y << 32) | ((long) X << 48) | BITMAP;
    }

    public static boolean isBitmap(long style) {
        return (style & 0x8000) != 0;
    }

    public static int bitmapNum(long style) {
        return (int) (style & 0xffff0000) >> 16;
    }

    public static int bitmapX(long style) {
        return (int) ((style >> 48) & 0xfff);
    }

    public static int bitmapY(long style) {
        return (int) ((style >> 32) & 0xfff);
    }
}
