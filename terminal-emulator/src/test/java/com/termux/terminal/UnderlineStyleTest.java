package com.termux.terminal;

/**
 * Styled and colored underlines: SGR 4:x, SGR 21, SGR 58/59, and the preservation of both through the
 * operations that rewrite cells. See https://sw.kovidgoyal.net/kitty/underlines/.
 */
public class UnderlineStyleTest extends TerminalTestCase {

    private int underlineStyleAt(int row, int column) {
        return TextStyle.decodeUnderlineStyle(getStyleAt(row, column));
    }

    private boolean underlineAttributeAt(int row, int column) {
        return (TextStyle.decodeEffect(getStyleAt(row, column)) & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
    }

    private int decorationColorAt(int row, int column) {
        return mTerminal.getScreen().getDecorationColorAt(row, column);
    }

    public void testPlainUnderlineIsSingle() {
        withTerminalSized(3, 2).enterString("\033[4mA");
        assertEquals(TextStyle.UNDERLINE_STYLE_SINGLE, underlineStyleAt(0, 0));
        assertTrue(underlineAttributeAt(0, 0));
    }

    public void testEveryUnderlineVariant() {
        withTerminalSized(6, 2).enterString("\033[4:1mA\033[4:2mB\033[4:3mC\033[4:4mD\033[4:5mE");
        assertEquals(TextStyle.UNDERLINE_STYLE_SINGLE, underlineStyleAt(0, 0));
        assertEquals(TextStyle.UNDERLINE_STYLE_DOUBLE, underlineStyleAt(0, 1));
        assertEquals(TextStyle.UNDERLINE_STYLE_CURLY, underlineStyleAt(0, 2));
        assertEquals(TextStyle.UNDERLINE_STYLE_DOTTED, underlineStyleAt(0, 3));
        assertEquals(TextStyle.UNDERLINE_STYLE_DASHED, underlineStyleAt(0, 4));
        for (int column = 0; column < 5; column++) assertTrue("column=" + column, underlineAttributeAt(0, column));
    }

    public void testUnderlineSubParameterZeroTurnsItOff() {
        withTerminalSized(3, 2).enterString("\033[4:3mA\033[4:0mB");
        assertEquals(TextStyle.UNDERLINE_STYLE_CURLY, underlineStyleAt(0, 0));
        assertEquals(TextStyle.UNDERLINE_STYLE_NONE, underlineStyleAt(0, 1));
        assertFalse(underlineAttributeAt(0, 1));
    }

    /** An unknown style must still underline, rather than be dropped. */
    public void testUnknownUnderlineStyleFallsBackToSingle() {
        withTerminalSized(3, 2).enterString("\033[4:9mA");
        assertEquals(TextStyle.UNDERLINE_STYLE_SINGLE, underlineStyleAt(0, 0));
        assertTrue(underlineAttributeAt(0, 0));
    }

    public void testSgr21IsDoubleUnderline() {
        withTerminalSized(3, 2).enterString("\033[21mA");
        assertEquals(TextStyle.UNDERLINE_STYLE_DOUBLE, underlineStyleAt(0, 0));
        assertTrue(underlineAttributeAt(0, 0));
    }

    public void testSgr24AndSgr0ClearTheStyle() {
        withTerminalSized(4, 2).enterString("\033[4:3mA\033[24mB\033[4:4mC\033[0mD");
        assertEquals(TextStyle.UNDERLINE_STYLE_CURLY, underlineStyleAt(0, 0));
        assertEquals(TextStyle.UNDERLINE_STYLE_NONE, underlineStyleAt(0, 1));
        assertEquals(TextStyle.UNDERLINE_STYLE_DOTTED, underlineStyleAt(0, 2));
        assertEquals(TextStyle.UNDERLINE_STYLE_NONE, underlineStyleAt(0, 3));
    }

    public void testStyleSurvivesSaveAndRestoreCursor() {
        withTerminalSized(3, 2).enterString("\033[4:3m\0337\033[24m\0338A");
        assertEquals(TextStyle.UNDERLINE_STYLE_CURLY, underlineStyleAt(0, 0));
    }

    public void testDecorationColorIsStoredPerCell() {
        withTerminalSized(4, 2).enterString("\033[58;5;9mA\033[58;2;1;2;3mB\033[59mC");
        assertEquals(9, decorationColorAt(0, 0));
        assertEquals(0xff010203, decorationColorAt(0, 1));
        assertEquals(TextStyle.DECORATION_COLOR_DEFAULT, decorationColorAt(0, 2));
    }

    public void testSgr0ResetsDecorationColor() {
        withTerminalSized(3, 2).enterString("\033[58;5;9mA\033[0mB");
        assertEquals(9, decorationColorAt(0, 0));
        assertEquals(TextStyle.DECORATION_COLOR_DEFAULT, decorationColorAt(0, 1));
    }

    public void testDecorationColorSurvivesSaveAndRestoreCursor() {
        withTerminalSized(3, 2).enterString("\033[58;5;9m\0337\033[59m\0338A");
        assertEquals(9, decorationColorAt(0, 0));
    }

    /** A row only pays for the side table once a cell in it actually needs one. */
    public void testDecorationTableIsOnlyAllocatedWhenUsed() {
        withTerminalSized(3, 2).enterString("AB");
        TerminalRow row = mTerminal.getScreen().allocateFullLineIfNecessary(mTerminal.getScreen().externalToInternalRow(0));
        assertFalse(row.hasDecorationColors());
        enterString("\033[58;5;9mC");
        assertTrue(row.hasDecorationColors());
    }

    public void testStyleAndColorSurviveReflow() {
        withTerminalSized(4, 4).enterString("\033[4:3m\033[58;5;9mABCDEF");
        resize(3, 4);
        // "ABCDEF" now wraps over two rows; every cell of it keeps its decoration.
        assertEquals(TextStyle.UNDERLINE_STYLE_CURLY, underlineStyleAt(0, 0));
        assertEquals(9, decorationColorAt(0, 0));
        assertEquals(TextStyle.UNDERLINE_STYLE_CURLY, underlineStyleAt(1, 0));
        assertEquals(9, decorationColorAt(1, 0));
    }

    public void testStyleSurvivesScrollIntoHistory() {
        withTerminalSized(3, 2).enterString("\033[4:3m\033[58;5;9mA\r\n\033[0mB\r\nC");
        assertEquals(TextStyle.UNDERLINE_STYLE_CURLY, underlineStyleAt(-1, 0));
        assertEquals(9, decorationColorAt(-1, 0));
    }

    /** DECCARA rewrites the effect of existing cells and must not drop what it does not know about. */
    public void testDeccaraKeepsUnderlineStyle() {
        withTerminalSized(4, 2).enterString("\033[4:3m\033[58;5;9mAB");
        // Set the bold attribute over the whole screen.
        enterString("\033[1;1;2;4;1$r");
        assertEquals(TextStyle.UNDERLINE_STYLE_CURLY, underlineStyleAt(0, 0));
        assertTrue((TextStyle.decodeEffect(getStyleAt(0, 0)) & TextStyle.CHARACTER_ATTRIBUTE_BOLD) != 0);
        assertEquals(9, decorationColorAt(0, 0));
    }

    public void testEraseDropsDecorationColor() {
        withTerminalSized(3, 2).enterString("\033[58;5;9mAB\033[H\033[2J");
        assertEquals(TextStyle.DECORATION_COLOR_DEFAULT, decorationColorAt(0, 0));
    }

    public void testEncodeAndDecodeRoundTripAcrossVariants() {
        for (int style = TextStyle.UNDERLINE_STYLE_NONE; style <= TextStyle.UNDERLINE_STYLE_MAX; style++) {
            long encoded = TextStyle.encode(0xff112233, 0xff445566, TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE | TextStyle.CHARACTER_ATTRIBUTE_BOLD, style);
            assertEquals(style, TextStyle.decodeUnderlineStyle(encoded));
            assertEquals(0xff112233, TextStyle.decodeForeColor(encoded));
            assertEquals(0xff445566, TextStyle.decodeBackColor(encoded));
            int expectedAttributes = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE | TextStyle.CHARACTER_ATTRIBUTE_BOLD;
            assertEquals(expectedAttributes, TextStyle.decodeEffect(encoded) & expectedAttributes);
        }
    }

    /** The underline style must not leak into the bits that mark a cell as a bitmap slice. */
    public void testUnderlineStyleDoesNotCollideWithBitmapBit() {
        long encoded = TextStyle.encode(0, 0, 0, TextStyle.UNDERLINE_STYLE_DASHED);
        assertFalse(TextStyle.isBitmap(encoded));
    }
}
