package com.termux.terminal;

import java.util.Arrays;

/** Pure decoding logic for kitty's U+10EEEE Unicode image-placeholder cells. */
public final class KittyUnicodePlaceholder {

    public static final int CODE_POINT = 0x10eeee;

    /**
     * The fixed alphabet from kitty's rowcolumn-diacritics.txt. Its array index is the encoded
     * row, column, or high image-id byte. Keep this list stable: it is protocol data, not a
     * Unicode-property query.
     */
    private static final int[] DIACRITICS = {
        0x0305, 0x030d, 0x030e, 0x0310, 0x0312, 0x033d, 0x033e, 0x033f,
        0x0346, 0x034a, 0x034b, 0x034c, 0x0350, 0x0351, 0x0352, 0x0357,
        0x035b, 0x0363, 0x0364, 0x0365, 0x0366, 0x0367, 0x0368, 0x0369,
        0x036a, 0x036b, 0x036c, 0x036d, 0x036e, 0x036f, 0x0483, 0x0484,
        0x0485, 0x0486, 0x0487, 0x0592, 0x0593, 0x0594, 0x0595, 0x0597,
        0x0598, 0x0599, 0x059c, 0x059d, 0x059e, 0x059f, 0x05a0, 0x05a1,
        0x05a8, 0x05a9, 0x05ab, 0x05ac, 0x05af, 0x05c4, 0x0610, 0x0611,
        0x0612, 0x0613, 0x0614, 0x0615, 0x0616, 0x0617, 0x0657, 0x0658,
        0x0659, 0x065a, 0x065b, 0x065d, 0x065e, 0x06d6, 0x06d7, 0x06d8,
        0x06d9, 0x06da, 0x06db, 0x06dc, 0x06df, 0x06e0, 0x06e1, 0x06e2,
        0x06e4, 0x06e7, 0x06e8, 0x06eb, 0x06ec, 0x0730, 0x0732, 0x0733,
        0x0735, 0x0736, 0x073a, 0x073d, 0x073f, 0x0740, 0x0741, 0x0743,
        0x0745, 0x0747, 0x0749, 0x074a, 0x07eb, 0x07ec, 0x07ed, 0x07ee,
        0x07ef, 0x07f0, 0x07f1, 0x07f3, 0x0816, 0x0817, 0x0818, 0x0819,
        0x081b, 0x081c, 0x081d, 0x081e, 0x081f, 0x0820, 0x0821, 0x0822,
        0x0823, 0x0825, 0x0826, 0x0827, 0x0829, 0x082a, 0x082b, 0x082c,
        0x082d, 0x0951, 0x0953, 0x0954, 0x0f82, 0x0f83, 0x0f86, 0x0f87,
        0x135d, 0x135e, 0x135f, 0x17dd, 0x193a, 0x1a17, 0x1a75, 0x1a76,
        0x1a77, 0x1a78, 0x1a79, 0x1a7a, 0x1a7b, 0x1a7c, 0x1b6b, 0x1b6d,
        0x1b6e, 0x1b6f, 0x1b70, 0x1b71, 0x1b72, 0x1b73, 0x1cd0, 0x1cd1,
        0x1cd2, 0x1cda, 0x1cdb, 0x1ce0, 0x1dc0, 0x1dc1, 0x1dc3, 0x1dc4,
        0x1dc5, 0x1dc6, 0x1dc7, 0x1dc8, 0x1dc9, 0x1dcb, 0x1dcc, 0x1dd1,
        0x1dd2, 0x1dd3, 0x1dd4, 0x1dd5, 0x1dd6, 0x1dd7, 0x1dd8, 0x1dd9,
        0x1dda, 0x1ddb, 0x1ddc, 0x1ddd, 0x1dde, 0x1ddf, 0x1de0, 0x1de1,
        0x1de2, 0x1de3, 0x1de4, 0x1de5, 0x1de6, 0x1dfe, 0x20d0, 0x20d1,
        0x20d4, 0x20d5, 0x20d6, 0x20d7, 0x20db, 0x20dc, 0x20e1, 0x20e7,
        0x20e9, 0x20f0, 0x2cef, 0x2cf0, 0x2cf1, 0x2de0, 0x2de1, 0x2de2,
        0x2de3, 0x2de4, 0x2de5, 0x2de6, 0x2de7, 0x2de8, 0x2de9, 0x2dea,
        0x2deb, 0x2dec, 0x2ded, 0x2dee, 0x2def, 0x2df0, 0x2df1, 0x2df2,
        0x2df3, 0x2df4, 0x2df5, 0x2df6, 0x2df7, 0x2df8, 0x2df9, 0x2dfa,
        0x2dfb, 0x2dfc, 0x2dfd, 0x2dfe, 0x2dff, 0xa66f, 0xa67c, 0xa67d,
        0xa6f0, 0xa6f1, 0xa8e0, 0xa8e1, 0xa8e2, 0xa8e3, 0xa8e4, 0xa8e5,
        0xa8e6, 0xa8e7, 0xa8e8, 0xa8e9, 0xa8ea, 0xa8eb, 0xa8ec, 0xa8ed,
        0xa8ee, 0xa8ef, 0xa8f0, 0xa8f1, 0xaab0, 0xaab2, 0xaab3, 0xaab7,
        0xaab8, 0xaabe, 0xaabf, 0xaac1, 0xfe20, 0xfe21, 0xfe22, 0xfe23,
        0xfe24, 0xfe25, 0xfe26, 0x10a0f, 0x10a38, 0x1d185, 0x1d186,
        0x1d187, 0x1d188, 0x1d189, 0x1d1aa, 0x1d1ab, 0x1d1ac, 0x1d1ad,
        0x1d242, 0x1d243, 0x1d244
    };

    private KittyUnicodePlaceholder() {}

    /** One decoded placeholder cell. Color fields retain their packed terminal values for inheritance. */
    public static final class Cell {
        public final long imageId;
        public final long placementId;
        public final int row;
        public final int column;
        public final int imageIdHighByte;
        final int foregroundColor;
        final int underlineColor;

        private Cell(long imageId, long placementId, int row, int column, int imageIdHighByte,
                     int foregroundColor, int underlineColor) {
            this.imageId = imageId;
            this.placementId = placementId;
            this.row = row;
            this.column = column;
            this.imageIdHighByte = imageIdHighByte;
            this.foregroundColor = foregroundColor;
            this.underlineColor = underlineColor;
        }
    }

    /** Return the protocol value encoded by a diacritic, or -1 when it is outside the fixed alphabet. */
    public static int diacriticValue(int codePoint) {
        int index = Arrays.binarySearch(DIACRITICS, codePoint);
        return index >= 0 ? index : -1;
    }

    /**
     * Decode the combining-code-point portion of one placeholder grapheme. The foreground encodes
     * the low 24 image-id bits, and the underline color encodes the placement id. The terminal's
     * default-color sentinel must be supplied because it is not a protocol id.
     */
    public static Cell decode(char[] text, int start, int end, int foregroundColor,
                              int underlineColor, int defaultColor, Cell previous) {
        if (text == null || start < 0 || end < start || end > text.length) return null;
        int[] values = {-1, -1, -1};
        int count = 0;
        for (int index = start; index < end; ) {
            int codePoint = Character.codePointAt(text, index, end);
            index += Character.charCount(codePoint);
            int value = diacriticValue(codePoint);
            if (value < 0 || count == values.length) return null;
            values[count++] = value;
        }

        int lowImageId = colorValue(foregroundColor, defaultColor);
        if (lowImageId < 0) return null;
        int placementId = colorValue(underlineColor, defaultColor);
        if (placementId < 0) placementId = 0;

        int row = values[0];
        int column = values[1];
        int highByte = values[2];
        boolean sameColors = previous != null
            && previous.foregroundColor == foregroundColor
            && previous.underlineColor == underlineColor;
        if (count == 0 && sameColors) {
            row = previous.row;
            column = previous.column + 1;
            highByte = previous.imageIdHighByte;
        } else if (count == 1 && sameColors && row == previous.row) {
            column = previous.column + 1;
            highByte = previous.imageIdHighByte;
        } else if (count == 2 && sameColors && row == previous.row
            && column == previous.column + 1) {
            highByte = previous.imageIdHighByte;
        }
        if (row < 0 || column < 0) return null;
        if (highByte < 0) highByte = 0;
        if (highByte > 0xff) return null;
        long imageId = (lowImageId & 0x00ffffffL) | ((long) highByte << 24);
        return new Cell(imageId, placementId & 0x00ffffffL, row, column, highByte,
            foregroundColor, underlineColor);
    }

    private static int colorValue(int color, int defaultColor) {
        if (color == defaultColor) return -1;
        if ((color & 0xff000000) == 0xff000000) return color & 0x00ffffff;
        return color >= 0 && color <= 0xff ? color : -1;
    }
}
