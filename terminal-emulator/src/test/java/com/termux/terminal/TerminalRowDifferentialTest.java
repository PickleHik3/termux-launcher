package com.termux.terminal;

import junit.framework.TestCase;

import java.util.Random;

/**
 * {@link TerminalRow} must hold exactly what the frozen {@link LegacyTerminalRow} holds after any
 * sequence of operations. Both are driven by the same seeded random calls (ASCII, CJK, emoji,
 * combining marks, surrogates, controls, overwrites of either half of a wide character, narrow to
 * wide and wide to narrow replacement, widening, interval copies, resizes, clears) and compared
 * cell by cell after every call.
 */
public class TerminalRowDifferentialTest extends TestCase {

    private static final int[] COLUMN_COUNTS = {1, 2, 3, 4, 5, 7, 10, 16, 40, 80};

    private static final int[] SPECIAL = {
        0x4E2D, 0x679C, 0x679D, 0xFF21, 0x3042, 0xAC00, 0x1100, // wide, BMP
        0x2070E, 0x20731, 0x1F600, 0x1F468, 0x1F525, 0x1F9D1, // wide, supplementary
        0x1D11E, 0x1D11F, 0x1F781, // narrow, supplementary
        0x0301, 0x0308, 0x036F, 0x20D0, 0x0E31, 0x094D, // combining
        0x200D, 0xFE0F, 0x200B, 0x2060, 0x1F3FB, 0x1F3FF, 0xE0020, // zero width and modifiers
        0x1F1E6, 0x1F1FA, // regional indicators
        0x2500, 0x253C, 0x2588, 0xE9, 0xFF, 0x7F, 0x85, 0xA0, 0xFFFD, // narrow BMP
        0x09, 0x00, 0x07, 0x1B, // controls
        0x20, 0x41, 0x7E, // ASCII
    };

    private static final long[] STYLES = {
        TextStyle.NORMAL, TextStyle.encode(1, 2, 0), TextStyle.encode(3, 4, 1),
        TextStyle.encode(200, 100, 8), TextStyle.encode(7, 0, 3),
    };

    private static int pickCodePoint(Random random) {
        switch (random.nextInt(6)) {
            case 0:
            case 1:
            case 2:
                return 0x20 + random.nextInt(0x5F);
            case 3:
                return 0xA1 + random.nextInt(0x2000);
            default:
                return SPECIAL[random.nextInt(SPECIAL.length)];
        }
    }

    /** Both rows of a kind, so interval copies can go between rows as well as within one. */
    private static final class Rows {
        TerminalRow actualA, actualB;
        LegacyTerminalRow legacyA, legacyB;

        Rows(int columns, long style) {
            actualA = new TerminalRow(columns, style);
            actualB = new TerminalRow(columns, style);
            legacyA = new LegacyTerminalRow(columns, style);
            legacyB = new LegacyTerminalRow(columns, style);
        }
    }

    private static int columnsOf(TerminalRow row) {
        return row.mStyle.length;
    }

    private static String describe(Throwable thrown) {
        // The frozen copy names itself in messages; the text is otherwise compared as is.
        return thrown == null ? "no exception"
            : thrown.getClass().getName() + ": " + String.valueOf(thrown.getMessage()).replace("LegacyTerminalRow", "TerminalRow");
    }

    private static int outcomeOfFindStart(TerminalRow row, int column) {
        try {
            return row.findStartOfColumn(column);
        } catch (RuntimeException e) {
            return Integer.MIN_VALUE;
        }
    }

    private static int outcomeOfFindStart(LegacyTerminalRow row, int column) {
        try {
            return row.findStartOfColumn(column);
        } catch (RuntimeException e) {
            return Integer.MIN_VALUE;
        }
    }

    private static void assertSameRow(String label, TerminalRow actual, LegacyTerminalRow legacy) {
        int columns = columnsOf(actual);
        assertEquals(label + " columns", legacy.mStyle.length, columns);
        assertEquals(label + " spaceUsed", legacy.getSpaceUsed(), actual.getSpaceUsed());
        assertEquals(label + " capacity", legacy.mText.length, actual.mText.length);
        for (int i = 0; i < legacy.getSpaceUsed(); i++) {
            assertEquals(label + " char " + i, legacy.mText[i], actual.mText[i]);
            assertEquals(label + " width " + i, legacy.getDisplayWidthAt(i), actual.getDisplayWidthAt(i));
        }
        for (int c = 0; c < columns; c++) {
            assertEquals(label + " style " + c, legacy.getStyle(c), actual.getStyle(c));
            assertEquals(label + " decoration " + c, legacy.getDecorationColor(c), actual.getDecorationColor(c));
            assertEquals(label + " hyperlink " + c, legacy.getHyperlinkId(c), actual.getHyperlinkId(c));
            assertEquals(label + " textsize " + c, legacy.getTextSizeRecord(c), actual.getTextSizeRecord(c));
        }
        for (int c = 0; c <= columns; c++) {
            assertEquals(label + " findStartOfColumn " + c, outcomeOfFindStart(legacy, c),
                outcomeOfFindStart(actual, c));
        }
        assertEquals(label + " flag", legacy.mHasNonOneWidthOrSurrogateChars, actual.mHasNonOneWidthOrSurrogateChars);
        assertEquals(label + " bitmap", legacy.mHasBitmap, actual.mHasBitmap);
        assertEquals(label + " hasDecorationColors", legacy.hasDecorationColors(), actual.hasDecorationColors());
        assertEquals(label + " hasHyperlinks", legacy.hasHyperlinks(), actual.hasHyperlinks());
        assertEquals(label + " hasTextSizes", legacy.hasTextSizes(), actual.hasTextSizes());
        assertEquals(label + " isBlank", legacy.isBlank(), actual.isBlank());
    }

    private static void assertSameRows(String label, Rows rows) {
        assertSameRow(label + " A", rows.actualA, rows.legacyA);
        assertSameRow(label + " B", rows.actualB, rows.legacyB);
    }

    /** Runs one operation on both rows and returns a description of it. */
    private static String step(Random random, Rows rows, int[] exceptionCounter) {
        boolean useA = random.nextBoolean();
        TerminalRow actual = useA ? rows.actualA : rows.actualB;
        int columns = columnsOf(actual);
        LegacyTerminalRow legacy = useA ? rows.legacyA : rows.legacyB;
        int roll = random.nextInt(100);
        int column = random.nextInt(columns);
        long style = STYLES[random.nextInt(STYLES.length)];
        Throwable actualThrown = null;
        Throwable legacyThrown = null;
        String what;
        Object actualResult = null;
        Object legacyResult = null;
        // The generator draws every random value before the two calls so both see the same ones.
        if (roll < 52) {
            int cp = pickCodePoint(random);
            what = "setChar(" + column + ", U+" + Integer.toHexString(cp) + ")";
            try {
                actual.setChar(column, cp, style);
            } catch (RuntimeException e) {
                actualThrown = e;
            }
            try {
                legacy.setChar(column, cp, style);
            } catch (RuntimeException e) {
                legacyThrown = e;
            }
        } else if (roll < 60) {
            int cp = pickCodePoint(random);
            int decoration = random.nextInt(3) == 0 ? 0xFF00FF : TextStyle.DECORATION_COLOR_DEFAULT;
            int link = random.nextInt(3);
            what = "setChar5(" + column + ", U+" + Integer.toHexString(cp) + ")";
            try {
                actual.setChar(column, cp, style, decoration, link);
            } catch (RuntimeException e) {
                actualThrown = e;
            }
            try {
                legacy.setChar(column, cp, style, decoration, link);
            } catch (RuntimeException e) {
                legacyThrown = e;
            }
        } else if (roll < 66) {
            int cp = pickCodePoint(random);
            what = "appendCodePointToCell(" + column + ", U+" + Integer.toHexString(cp) + ")";
            try {
                actual.appendCodePointToCell(column, cp);
            } catch (RuntimeException e) {
                actualThrown = e;
            }
            try {
                legacy.appendCodePointToCell(column, cp);
            } catch (RuntimeException e) {
                legacyThrown = e;
            }
        } else if (roll < 70) {
            what = "widenCell(" + column + ")";
            try {
                actualResult = actual.widenCell(column);
            } catch (RuntimeException e) {
                actualThrown = e;
            }
            try {
                legacyResult = legacy.widenCell(column);
            } catch (RuntimeException e) {
                legacyThrown = e;
            }
        } else if (roll < 75) {
            int cp = pickCodePoint(random);
            what = "setBlockAnchorChar(" + column + ", U+" + Integer.toHexString(cp) + ")";
            try {
                actual.setBlockAnchorChar(column, cp, style, TextStyle.DECORATION_COLOR_DEFAULT, 0);
            } catch (RuntimeException e) {
                actualThrown = e;
            }
            try {
                legacy.setBlockAnchorChar(column, cp, style, TextStyle.DECORATION_COLOR_DEFAULT, 0);
            } catch (RuntimeException e) {
                legacyThrown = e;
            }
        } else if (roll < 88) {
            boolean intoOther = random.nextBoolean();
            TerminalRow actualSource = intoOther == useA ? rows.actualB : rows.actualA;
            LegacyTerminalRow legacySource = intoOther == useA ? rows.legacyB : rows.legacyA;
            int sourceColumns = columnsOf(actualSource);
            int x1 = random.nextInt(sourceColumns + 1);
            int x2 = x1 + random.nextInt(sourceColumns - x1 + 1);
            int room = columns - (x2 - x1);
            int destination = room <= 0 ? 0 : random.nextInt(room + 1);
            what = "copyInterval(" + (intoOther ? "other" : "self") + ", " + x1 + ", " + x2 + ", " + destination + ")";
            if (x2 - x1 > columns) {
                what = "skipped " + what;
            } else {
                try {
                    actual.copyInterval(intoOther ? actualSource : actual, x1, x2, destination);
                } catch (RuntimeException e) {
                    actualThrown = e;
                }
                try {
                    legacy.copyInterval(intoOther ? legacySource : legacy, x1, x2, destination);
                } catch (RuntimeException e) {
                    legacyThrown = e;
                }
            }
        } else if (roll < 91) {
            int record = random.nextInt(3) == 0 ? 0 : 1 + random.nextInt(1000);
            what = "setTextSizeRecord(" + column + ", " + record + ")";
            actual.setTextSizeRecord(column, record);
            legacy.setTextSizeRecord(column, record);
        } else if (roll < 94) {
            what = "clear";
            actual.clear(style);
            legacy.clear(style);
        } else if (roll < 97) {
            int newColumns = COLUMN_COUNTS[random.nextInt(COLUMN_COUNTS.length)];
            what = "resizedTo(" + newColumns + ")";
            TerminalRow actualResized = null;
            LegacyTerminalRow legacyResized = null;
            try {
                actualResized = actual.resizedTo(newColumns, style);
            } catch (RuntimeException e) {
                actualThrown = e;
            }
            try {
                legacyResized = legacy.resizedTo(newColumns, style);
            } catch (RuntimeException e) {
                legacyThrown = e;
            }
            if (actualThrown == null && legacyThrown == null) {
                if (useA) {
                    rows.actualA = actualResized;
                    rows.legacyA = legacyResized;
                } else {
                    rows.actualB = actualResized;
                    rows.legacyB = legacyResized;
                }
            }
        } else {
            what = "clearTextSizes";
            actual.clearTextSizes();
            legacy.clearTextSizes();
        }
        assertEquals(what + " threw", describe(legacyThrown), describe(actualThrown));
        assertEquals(what + " result", legacyResult, actualResult);
        if (legacyThrown != null) exceptionCounter[0]++;
        return (useA ? "A:" : "B:") + what + (legacyThrown != null ? " (threw)" : "");
    }

    private void runCases(long seed, int cases, int minSteps, int maxSteps) {
        Random random = new Random(seed);
        int[] exceptions = {0};
        long operations = 0;
        for (int c = 0; c < cases; c++) {
            int columns = COLUMN_COUNTS[random.nextInt(COLUMN_COUNTS.length)];
            Rows rows = new Rows(columns, STYLES[0]);
            int steps = minSteps + random.nextInt(maxSteps - minSteps + 1);
            StringBuilder trace = new StringBuilder();
            for (int s = 0; s < steps; s++) {
                int before = exceptions[0];
                trace.append("\n  ").append(step(random, rows, exceptions));
                operations++;
                if (trace.length() > 3000) trace.delete(0, trace.length() - 3000);
                assertSameRows("case " + c + " step " + s + " cols " + columnsOf(rows.actualA)
                    + "/" + columnsOf(rows.actualB) + " trace:" + trace, rows);
                // A write that threw may have left the row half changed. Both rows are identical
                // at that point, but the case ends so no later operation runs on that state.
                if (exceptions[0] != before) break;
            }
        }
        assertTrue("generated " + operations + " operations", operations > cases);
    }

    public void testRandomOperationsMatchLegacy() {
        runCases(0x7E57C0DEL, 6000, 20, 140);
    }

    public void testNarrowRowsWithManyWideCharacters() {
        // A second seed biased by short rows: wide characters meet the edges all the time.
        Random random = new Random(0xCAFEL);
        int[] exceptions = {0};
        for (int c = 0; c < 4000; c++) {
            Rows rows = new Rows(2 + random.nextInt(8), STYLES[0]);
            for (int s = 0; s < 60; s++) {
                int before = exceptions[0];
                String what = step(random, rows, exceptions);
                assertSameRows("narrow case " + c + " step " + s + " " + what, rows);
                if (exceptions[0] != before) break;
            }
        }
    }

    public void testFullWidthTextLineMatchesLegacy() {
        // A screen's worth of mixed text written left to right with occasional overwrites, as a
        // program painting a line does.
        Random random = new Random(99);
        for (int c = 0; c < 1500; c++) {
            int columns = 40 + random.nextInt(60);
            TerminalRow actual = new TerminalRow(columns, TextStyle.NORMAL);
            LegacyTerminalRow legacy = new LegacyTerminalRow(columns, TextStyle.NORMAL);
            for (int column = 0; column < columns; ) {
                int cp = pickCodePoint(random);
                int width = WcWidth.width(cp);
                if (width == 2 && column == columns - 1) break;
                actual.setChar(column, cp, TextStyle.NORMAL);
                legacy.setChar(column, cp, TextStyle.NORMAL);
                assertSameRow("line " + c + " col " + column, actual, legacy);
                column += Math.max(width, random.nextInt(8) == 0 ? 0 : 1);
                if (random.nextInt(12) == 0) column = Math.max(0, column - 1 - random.nextInt(4));
            }
        }
    }

    /**
     * Writes cluster near the previous one, as text painted left to right does, with widening and
     * clears between them. That is where a remembered position in the row could go stale.
     */
    public void testWritesNearThePreviousWriteMatchLegacy() {
        Random random = new Random(0xB1A5L);
        for (int c = 0; c < 4000; c++) {
            int columns = 3 + random.nextInt(37);
            TerminalRow actual = new TerminalRow(columns, TextStyle.NORMAL);
            LegacyTerminalRow legacy = new LegacyTerminalRow(columns, TextStyle.NORMAL);
            int column = random.nextInt(columns);
            for (int s = 0; s < 80; s++) {
                column = Math.max(0, Math.min(columns - 1, column + random.nextInt(5) - 1));
                int cp = pickCodePoint(random);
                String what = "case " + c + " step " + s + " col " + column + " U+" + Integer.toHexString(cp);
                int kind = random.nextInt(10);
                Throwable actualThrown = null;
                Throwable legacyThrown = null;
                try {
                    if (kind < 7) actual.setChar(column, cp, TextStyle.NORMAL);
                    else if (kind < 8) actual.widenCell(column);
                    else if (kind < 9) actual.appendCodePointToCell(column, cp);
                    else actual.copyInterval(actual, 0, column + 1, 0);
                } catch (RuntimeException e) {
                    actualThrown = e;
                }
                try {
                    if (kind < 7) legacy.setChar(column, cp, TextStyle.NORMAL);
                    else if (kind < 8) legacy.widenCell(column);
                    else if (kind < 9) legacy.appendCodePointToCell(column, cp);
                    else legacy.copyInterval(legacy, 0, column + 1, 0);
                } catch (RuntimeException e) {
                    legacyThrown = e;
                }
                assertEquals(what + " threw", describe(legacyThrown), describe(actualThrown));
                assertSameRow(what, actual, legacy);
                if (legacyThrown != null) break;
                if (random.nextInt(40) == 0) {
                    actual.clear(TextStyle.NORMAL);
                    legacy.clear(TextStyle.NORMAL);
                }
            }
        }
    }

    /** The walk must find the same text start the old scans did, for every column of every shape. */
    public void testFindStartOfColumnStaysTheSame() {
        TerminalRow actual = new TerminalRow(10, TextStyle.NORMAL);
        LegacyTerminalRow legacy = new LegacyTerminalRow(10, TextStyle.NORMAL);
        int[] text = {0x4E2D, 'a', 0x0301, 0x1F600, 'b', 0x2070E, 'c'};
        int column = 0;
        for (int cp : text) {
            actual.setChar(column, cp, TextStyle.NORMAL);
            legacy.setChar(column, cp, TextStyle.NORMAL);
            column += Math.max(1, WcWidth.width(cp));
        }
        assertSameRow("fixed line", actual, legacy);
    }
}
