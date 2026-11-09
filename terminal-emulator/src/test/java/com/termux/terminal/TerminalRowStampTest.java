package com.termux.terminal;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;

/**
 * A row whose stamp has not moved holds exactly what it held when the stamp was read. The renderer
 * skips comparing such a row, so a change that slips past the stamp is a cell that stops updating.
 *
 * <p>Each case keeps a deep copy of every row it has seen, keyed by the row object, and after every
 * step asserts for every row it can reach: same stamp, same content. The steps are random calls on
 * {@link TerminalRow} itself and random escape streams through both cores, which between them reach
 * every way a row is changed (writes, wide and combining characters, widening, interval copies,
 * erases, inserts and deletes, rectangle operations, attributes, links, marks, text sizing, scrolls,
 * the alternate screen, resets and resizes).
 */
public class TerminalRowStampTest extends TerminalTestCase {

    private static final int[] SPECIAL = {
        0x4E2D, 0xFF21, 0xAC00, 0x1F600, 0x1F468, 0x1F9D1, 0x1D11E, // wide and supplementary
        0x0301, 0x0308, 0x20D0, 0x094D, 0x200D, 0xFE0F, 0x1F3FB, // combining, ZWJ, modifiers
        0x1F1E6, 0x1F1FA, 0x2500, 0xE9, 0xFFFD, 0x20, 0x41, 0x7E,
    };

    /** Everything a reader can get out of a row, copied. */
    private static final class Copy {
        final long stamp;
        final int spaceUsed;
        final char[] text;
        final byte[] widths;
        final long[] style;
        final int[] decoration, links, sizes;
        final boolean bitmap, wrap;
        final byte mark;
        final int placements;

        Copy(TerminalRow row) {
            stamp = row.getStamp();
            spaceUsed = row.getSpaceUsed();
            text = Arrays.copyOf(row.mText, spaceUsed);
            widths = new byte[spaceUsed];
            for (int i = 0; i < spaceUsed; i++) widths[i] = (byte) row.getDisplayWidthAt(i);
            int columns = row.mStyle.length;
            style = new long[columns];
            decoration = new int[columns];
            links = new int[columns];
            sizes = new int[columns];
            for (int c = 0; c < columns; c++) {
                style[c] = row.getStyle(c);
                decoration[c] = row.getDecorationColor(c);
                links[c] = row.getHyperlinkId(c);
                sizes[c] = row.getTextSizeRecord(c);
            }
            bitmap = row.mHasBitmap;
            wrap = row.getLineWrap();
            mark = row.mShellIntegrationMark;
            placements = row.mKittyPlacements == null ? 0 : row.mKittyPlacements.size();
        }

        /** Null when equal, else what differs. */
        String differenceFrom(Copy other) {
            if (spaceUsed != other.spaceUsed) return "spaceUsed";
            if (!Arrays.equals(text, other.text)) return "text '" + new String(text) + "' -> '" + new String(other.text) + "'";
            if (!Arrays.equals(widths, other.widths)) return "widths";
            if (!Arrays.equals(style, other.style)) return "style";
            if (!Arrays.equals(decoration, other.decoration)) return "decoration colours";
            if (!Arrays.equals(links, other.links)) return "hyperlinks";
            if (!Arrays.equals(sizes, other.sizes)) return "text sizes";
            if (bitmap != other.bitmap) return "bitmap";
            if (wrap != other.wrap) return "line wrap";
            if (mark != other.mark) return "mark";
            if (placements != other.placements) return "placements";
            return null;
        }
    }

    /** Last copy of every row seen, by row object. */
    private final Map<TerminalRow, Copy> mSeen = new IdentityHashMap<>();
    private int mUnmovedChecks;

    private void check(String step, TerminalRow row) {
        if (row == null) return;
        Copy now = new Copy(row);
        Copy before = mSeen.put(row, now);
        if (before == null || before.stamp != now.stamp) return;
        mUnmovedChecks++;
        String difference = before.differenceFrom(now);
        if (difference != null)
            fail("after " + step + ": a row changed (" + difference + ") but its stamp stayed " + now.stamp);
    }

    private void checkBuffer(String step, TerminalBuffer buffer) {
        for (TerminalRow row : buffer.mLines) check(step, row);
    }

    private static int pickCodePoint(Random random) {
        return random.nextInt(3) == 0 ? SPECIAL[random.nextInt(SPECIAL.length)] : 0x20 + random.nextInt(0x5F);
    }

    public void testRowCallsNeverChangeARowBehindAnUnmovedStamp() {
        Random random = new Random(0x57A3B);
        for (int round = 0; round < 200; round++) {
            mSeen.clear();
            int columns = 2 + random.nextInt(12);
            TerminalRow[] rows = {new TerminalRow(columns, 0), new TerminalRow(columns, 0)};
            for (TerminalRow row : rows) check("construction", row);
            for (int i = 0; i < 300; i++) {
                TerminalRow row = rows[random.nextInt(2)];
                int column = random.nextInt(columns);
                long style = TextStyle.encode(random.nextInt(8), random.nextInt(8), random.nextInt(4));
                String step;
                try {
                    switch (random.nextInt(15)) {
                        case 0: case 1: case 2: case 3:
                            step = "setChar";
                            row.setChar(column, pickCodePoint(random), style);
                            break;
                        case 4:
                            step = "setChar with decoration and link";
                            row.setChar(column, pickCodePoint(random), style,
                                random.nextBoolean() ? 0xFF112233 : TextStyle.DECORATION_COLOR_DEFAULT, random.nextInt(3));
                            break;
                        case 5:
                            step = "appendCodePointToCell";
                            row.appendCodePointToCell(column, SPECIAL[7 + random.nextInt(7)]);
                            break;
                        case 6:
                            step = "widenCell";
                            row.widenCell(column);
                            break;
                        case 7:
                            step = "setBlockAnchorChar";
                            row.setBlockAnchorChar(column, pickCodePoint(random), style, TextStyle.DECORATION_COLOR_DEFAULT, 0);
                            break;
                        case 8:
                            step = "copyInterval";
                            int from = random.nextInt(columns);
                            int to = from + random.nextInt(columns - from + 1);
                            TerminalRow source = rows[random.nextInt(2)];
                            row.copyInterval(source, from, to, random.nextInt(columns - (to - from) + 1));
                            break;
                        case 9:
                            step = "clear";
                            row.clear(style);
                            break;
                        case 10:
                            step = "setTextSizeRecord";
                            row.setTextSizeRecord(column, random.nextBoolean() ? 0 : 1 + random.nextInt(1000));
                            break;
                        case 11:
                            step = "clearTextSizes";
                            row.clearTextSizes();
                            break;
                        case 12:
                            step = "setLineWrap";
                            row.setLineWrap(random.nextBoolean());
                            break;
                        case 13:
                            step = "setShellIntegrationMark";
                            row.setShellIntegrationMark((byte) random.nextInt(4));
                            break;
                        default:
                            step = "setStyleAt";
                            row.setStyleAt(column, style);
                            break;
                    }
                } catch (RuntimeException refused) {
                    // A wide character in the last column, or an interval that ends inside a wide
                    // cell: the row may have changed before the call refused, so it is checked too.
                    step = "refused call";
                }
                for (TerminalRow r : rows) check(step, r);
            }
        }
        assertTrue("few rows were checked with an unmoved stamp: " + mUnmovedChecks, mUnmovedChecks > 10_000);
    }

    /** One random piece of terminal output: text or a sequence that changes rows. */
    private static String randomOutput(Random random, int columns, int rows) {
        int row = 1 + random.nextInt(rows), column = 1 + random.nextInt(columns);
        int n = 1 + random.nextInt(4);
        switch (random.nextInt(30)) {
            case 0: case 1: case 2: case 3: case 4: case 5: {
                StringBuilder text = new StringBuilder();
                for (int i = random.nextInt(columns * 2); i >= 0; i--) text.appendCodePoint(pickCodePoint(random));
                return text.toString();
            }
            case 6: return "\033[" + row + ";" + column + "H";
            case 7: return "\r\n";
            case 8: return "\033[" + n + "@";            // ICH
            case 9: return "\033[" + n + "P";            // DCH
            case 10: return "\033[" + n + "X";           // ECH
            case 11: return "\033[" + random.nextInt(3) + "K"; // EL
            case 12: return "\033[" + random.nextInt(4) + "J"; // ED, 3 erases history
            case 13: return "\033[" + n + "L";           // IL
            case 14: return "\033[" + n + "M";           // DL
            case 15: return "\033[" + n + (random.nextBoolean() ? "S" : "T"); // SU, SD
            case 16: return random.nextBoolean() ? "\033[" + row + ";" + rows + "r" : "\033[r"; // DECSTBM
            case 17: return random.nextBoolean() ? "\033[?69h\033[" + column + ";" + columns + "s" : "\033[?69l";
            case 18: // DECCARA and DECRARA: attributes over a rectangle, text untouched
                return "\033[1;1;" + row + ";" + column + ";" + (1 + random.nextInt(7)) + (random.nextBoolean() ? "$r" : "$t");
            case 19: return "\033[1;1;" + row + ";" + column + ";1;" + (1 + random.nextInt(rows)) + ";1;1$v"; // DECCRA
            case 20: return "\033[" + (0x21 + random.nextInt(90)) + ";1;1;" + row + ";" + column + "$x"; // DECFRA
            case 21: return "\033[1;1;" + row + ";" + column + "$z"; // DECERA
            case 22: return "\033[" + (random.nextBoolean() ? "1;4:3;58:2::200:10:10;38;5;" + random.nextInt(256) : "0") + "m";
            case 23: return random.nextBoolean() ? "\033]8;;https://example.com/" + n + "\033\\" : "\033]8;;\033\\";
            case 24: return "\033]133;" + "ABCD".charAt(random.nextInt(4)) + "\007";
            case 25: return "\033]66;s=" + (1 + random.nextInt(3)) + ";" + (char) ('a' + random.nextInt(26)) + "\007";
            case 26: return random.nextBoolean() ? "\033[?1049h" : "\033[?1049l";
            case 27: return "x\033[" + n + "b";          // REP
            case 28: return random.nextInt(8) == 0 ? "\033c" : "\033M"; // RIS, RI
            default: return "\033[" + n + "C" + (char) pickCodePoint(random) + "\b́"; // overwrite, combine
        }
    }

    private void feed(String output) {
        byte[] bytes = output.getBytes(StandardCharsets.UTF_8);
        mTerminal.append(bytes, bytes.length);
    }

    public void testJavaCoreOutputNeverChangesARowBehindAnUnmovedStamp() {
        Random random = new Random(0xC0FFEE);
        for (int round = 0; round < 40; round++) {
            mSeen.clear();
            int columns = 6 + random.nextInt(20), rows = 3 + random.nextInt(8);
            withTerminalSized(columns, rows);
            for (int i = 0; i < 400; i++) {
                String output = randomOutput(random, columns, rows);
                if (random.nextInt(60) == 0) {
                    columns = 6 + random.nextInt(20);
                    rows = 3 + random.nextInt(8);
                    mTerminal.resize(columns, rows, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS);
                    output = "resize";
                } else {
                    feed(output);
                }
                String step = "round " + round + " step " + i + " " + output.replace("\033", "ESC");
                checkBuffer(step, mTerminal.getScreen());
                checkBuffer(step, mTerminal.mAltBuffer);
            }
        }
        assertTrue("few rows were checked with an unmoved stamp: " + mUnmovedChecks, mUnmovedChecks > 10_000);
    }

    public void testGhosttyCoreOutputNeverChangesARowBehindAnUnmovedStamp() {
        if (System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) == null) return;
        Random random = new Random(0xBEEF);
        for (int round = 0; round < 20; round++) {
            mSeen.clear();
            int columns = 6 + random.nextInt(20), rows = 3 + random.nextInt(8);
            withTerminalSized(columns, rows);
            assertTrue(mTerminal.useGhosttyCore());
            for (int i = 0; i < 300; i++) {
                String output = randomOutput(random, columns, rows);
                feed(output);
                String step = "round " + round + " step " + i + " " + output.replace("\033", "ESC");
                TerminalContent content = mTerminal.getContent();
                for (int y = -content.historyRows(); y < content.screenRows(); y++) check(step, content.row(y));
            }
        }
        assertTrue("few rows were checked with an unmoved stamp: " + mUnmovedChecks, mUnmovedChecks > 5_000);
    }
}
