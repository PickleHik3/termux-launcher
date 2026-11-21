package com.termux.terminal;

import junit.framework.TestCase;

public class GhosttyStyleTest extends TestCase {

    private static int palette(int index) {
        return (GhosttyStyle.COLOR_TAG_PALETTE << 24) | index;
    }

    private static int rgb(int rgb) {
        return (GhosttyStyle.COLOR_TAG_RGB << 24) | rgb;
    }

    public void testDefaultCellIsNormal() {
        assertEquals(TextStyle.NORMAL, GhosttyStyle.toTextStyle(0, 0, 0));
    }

    public void testPaletteAndTrueColors() {
        long style = GhosttyStyle.toTextStyle(0, palette(1), rgb(0x123456));
        assertEquals(1, TextStyle.decodeForeColor(style));
        assertEquals(0xff123456, TextStyle.decodeBackColor(style));
        assertEquals(TextStyle.encode(1, 0xff123456, 0), style);
    }

    public void testPaletteIndexAbove255IsNotPossible() {
        // Only the low byte is an index; the tag decides the kind.
        assertEquals(255, GhosttyStyle.color(palette(255), 0));
        assertEquals(7, GhosttyStyle.color(0, 7));
    }

    public void testFlagsMapToEffects() {
        int flags = GhosttyStyle.FLAG_BOLD | GhosttyStyle.FLAG_ITALIC | GhosttyStyle.FLAG_FAINT
            | GhosttyStyle.FLAG_BLINK | GhosttyStyle.FLAG_INVERSE | GhosttyStyle.FLAG_INVISIBLE
            | GhosttyStyle.FLAG_STRIKETHROUGH | GhosttyStyle.FLAG_OVERLINE;
        int expected = TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_ITALIC
            | TextStyle.CHARACTER_ATTRIBUTE_DIM | TextStyle.CHARACTER_ATTRIBUTE_BLINK
            | TextStyle.CHARACTER_ATTRIBUTE_INVERSE | TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE
            | TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
        assertEquals(expected, TextStyle.decodeEffect(GhosttyStyle.toTextStyle(flags, 0, 0)));
    }

    public void testUnderlineStyleSetsTheUnderlineEffect() {
        int curly = TextStyle.UNDERLINE_STYLE_CURLY << GhosttyStyle.UNDERLINE_SHIFT;
        long style = GhosttyStyle.toTextStyle(curly, 0, 0);
        assertEquals(TextStyle.UNDERLINE_STYLE_CURLY, TextStyle.decodeUnderlineStyle(style));
        assertEquals(TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE, TextStyle.decodeEffect(style));
        assertEquals(TextStyle.encode(TextStyle.COLOR_INDEX_FOREGROUND, TextStyle.COLOR_INDEX_BACKGROUND,
            TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE, TextStyle.UNDERLINE_STYLE_CURLY), style);
    }

    public void testUnknownUnderlineStyleFallsBackToSingle() {
        assertEquals(TextStyle.UNDERLINE_STYLE_SINGLE, GhosttyStyle.underlineStyle(7 << GhosttyStyle.UNDERLINE_SHIFT));
    }

    public void testDecorationColor() {
        assertEquals(TextStyle.DECORATION_COLOR_DEFAULT, GhosttyStyle.decorationColor(0));
        assertEquals(5, GhosttyStyle.decorationColor(palette(5)));
        assertEquals(0xffabcdef, GhosttyStyle.decorationColor(rgb(0xabcdef)));
    }

    public void testWideAndExtrasLiveAboveTheFlags() {
        int attrs = GhosttyStyle.FLAG_BOLD | (GhosttyNative.WIDE_SPACER_TAIL << 16) | (3 << 20);
        assertEquals(GhosttyNative.WIDE_SPACER_TAIL, GhosttyStyle.wide(attrs));
        assertEquals(3, GhosttyStyle.extraCodePoints(attrs));
        assertEquals(TextStyle.CHARACTER_ATTRIBUTE_BOLD, GhosttyStyle.effect(attrs));
        assertEquals(255, GhosttyStyle.extraCodePoints(0xff << 20));
    }
}
