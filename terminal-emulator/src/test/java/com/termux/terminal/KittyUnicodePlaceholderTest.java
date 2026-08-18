package com.termux.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KittyUnicodePlaceholderTest {

    @Test
    public void decodesRowsColumnsIdsAndSupplementaryDiacritics() {
        KittyUnicodePlaceholder.Cell cell = decode("\u030e\u0310\u030d",
            0xff00002a, 0xff001234, null);
        assertEquals(2, cell.row);
        assertEquals(3, cell.column);
        assertEquals(1, cell.imageIdHighByte);
        assertEquals(0x0100002aL, cell.imageId);
        assertEquals(0x001234L, cell.placementId);

        assertEquals(296, KittyUnicodePlaceholder.diacriticValue(0x1d244));
        assertEquals(-1, KittyUnicodePlaceholder.diacriticValue(0x0300));
    }

    @Test
    public void inheritsOmittedValuesOnlyFromCompatibleLeftCell() {
        KittyUnicodePlaceholder.Cell first = decode("\u030d\u0305\u030e", 42, 7, null);
        KittyUnicodePlaceholder.Cell noMarks = decode("", 42, 7, first);
        assertEquals(1, noMarks.row);
        assertEquals(1, noMarks.column);
        assertEquals(2, noMarks.imageIdHighByte);

        KittyUnicodePlaceholder.Cell rowOnly = decode("\u030d", 42, 7, noMarks);
        assertEquals(1, rowOnly.row);
        assertEquals(2, rowOnly.column);
        assertEquals(2, rowOnly.imageIdHighByte);

        KittyUnicodePlaceholder.Cell rowAndColumn = decode("\u030d\u0310", 42, 7, rowOnly);
        assertEquals(1, rowAndColumn.row);
        assertEquals(3, rowAndColumn.column);
        assertEquals(2, rowAndColumn.imageIdHighByte);

        assertNull("a foreground change prevents inheritance", decode("", 43, 7, rowAndColumn));
        assertNull("an underline-color change prevents inheritance", decode("", 42, 8, rowAndColumn));
    }

    @Test
    public void rejectsMissingCoordinatesInvalidMarksAndOversizedHighByte() {
        assertNull(decode("", 42, 7, null));
        assertNull(decode("\u0300", 42, 7, null));
        assertNull(decode("\u0305\u0305\ua8e6", 42, 7, null));
        assertNull("default foreground is not image id 256",
            KittyUnicodePlaceholder.decode(new char[0], 0, 0, 256, 256, 256, null));
    }

    private static KittyUnicodePlaceholder.Cell decode(String marks, int foreground,
                                                        int underline,
                                                        KittyUnicodePlaceholder.Cell previous) {
        char[] chars = marks.toCharArray();
        return KittyUnicodePlaceholder.decode(chars, 0, chars.length, foreground, underline,
            256, previous);
    }
}
