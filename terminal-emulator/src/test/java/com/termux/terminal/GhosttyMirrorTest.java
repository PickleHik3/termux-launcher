package com.termux.terminal;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

/** The mirror's row copying, driven with plain arrays in place of the library. */
public class GhosttyMirrorTest extends TestCase {

    private static final int COLUMNS = 6;

    /** A row of cells built up one at a time, in the shim's layout. */
    private static final class RowBuilder {
        final int[] cells = new int[COLUMNS * GhosttyNative.CELL_INTS];
        final List<Integer> extra = new ArrayList<>();
        int result;

        RowBuilder text(int x, String s) {
            for (int i = 0; i < s.length(); i++) cell(x + i, s.charAt(i), 0, 0, 0);
            return this;
        }

        RowBuilder cell(int x, int codePoint, int attrs, int fg, int bg) {
            int base = x * GhosttyNative.CELL_INTS;
            cells[base + GhosttyNative.CELL_CODEPOINT] = codePoint;
            cells[base + GhosttyNative.CELL_ATTRS] = attrs;
            cells[base + GhosttyNative.CELL_FG] = fg;
            cells[base + GhosttyNative.CELL_BG] = bg;
            return this;
        }

        RowBuilder extras(int x, int... codePoints) {
            int base = x * GhosttyNative.CELL_INTS + GhosttyNative.CELL_ATTRS;
            cells[base] |= codePoints.length << 20;
            for (int c : codePoints) extra.add(c);
            result += codePoints.length;
            return this;
        }

        RowBuilder wrapped() {
            result |= GhosttyNative.ROW_WRAPPED;
            return this;
        }

        int[] extraArray() {
            int[] a = new int[Math.max(1, extra.size())];
            for (int i = 0; i < extra.size(); i++) a[i] = extra.get(i);
            return a;
        }
    }

    private static String text(TerminalBuffer buffer, int row) {
        return buffer.getSelectedText(0, row, COLUMNS - 1, row, false, false);
    }

    private static TerminalRow apply(RowBuilder b) {
        TerminalRow row = new TerminalRow(COLUMNS, TextStyle.NORMAL);
        GhosttyMirror.applyRow(row, COLUMNS, b.cells, 0, b.extraArray(), 0, b.result);
        return row;
    }

    public void testPlainTextAndStyle() {
        int red = (GhosttyStyle.COLOR_TAG_PALETTE << 24) | 1;
        RowBuilder b = new RowBuilder().text(0, "ab").cell(2, 'c', GhosttyStyle.FLAG_BOLD, red, 0);
        TerminalRow row = apply(b);
        assertEquals("abc   ", new String(row.mText, 0, row.getSpaceUsed()));
        assertEquals(TextStyle.NORMAL, row.getStyle(0));
        assertEquals(TextStyle.encode(1, TextStyle.COLOR_INDEX_BACKGROUND, TextStyle.CHARACTER_ATTRIBUTE_BOLD),
            row.getStyle(2));
        assertFalse(row.mLineWrap);
    }

    public void testWideCharacterTakesTwoCellsAndItsSpacerAddsNothing() {
        RowBuilder b = new RowBuilder().cell(0, 0x4E2D, GhosttyNative.WIDE_WIDE << 16, 0, 0)
            .cell(1, 0, GhosttyNative.WIDE_SPACER_TAIL << 16, 0, 0).text(2, "x");
        TerminalRow row = apply(b);
        assertEquals("中x   ", new String(row.mText, 0, row.getSpaceUsed()));
        assertEquals(1, row.findStartOfColumn(2));
    }

    public void testCombiningMarkJoinsItsCell() {
        RowBuilder b = new RowBuilder().text(0, "e").extras(0, 0x0301).text(1, "f");
        TerminalRow row = apply(b);
        assertEquals("éf    ", new String(row.mText, 0, row.getSpaceUsed()));
    }

    public void testEmptyCellKeepsItsBackground() {
        int blue = (GhosttyStyle.COLOR_TAG_PALETTE << 24) | 4;
        RowBuilder b = new RowBuilder().cell(3, 0, 0, 0, blue);
        TerminalRow row = apply(b);
        assertEquals(4, TextStyle.decodeBackColor(row.getStyle(3)));
        assertEquals(TextStyle.NORMAL, row.getStyle(2));
    }

    public void testWrapFlagIsCopied() {
        assertTrue(apply(new RowBuilder().text(0, "abcdef").wrapped()).mLineWrap);
    }

    public void testApplyScreenTouchesOnlyChangedRows() {
        TerminalBuffer buffer = new TerminalBuffer(COLUMNS, 10, 3);
        buffer.setChar(0, 1, 'k', TextStyle.NORMAL);
        int[] screen = new int[3 * COLUMNS * GhosttyNative.CELL_INTS];
        RowBuilder top = new RowBuilder().text(0, "top").extras(1, 0x0301);
        RowBuilder bottom = new RowBuilder().text(0, "bot");
        System.arraycopy(top.cells, 0, screen, 0, top.cells.length);
        System.arraycopy(bottom.cells, 0, screen, 2 * COLUMNS * GhosttyNative.CELL_INTS, bottom.cells.length);
        int[] dirty = {top.result, -1, bottom.result};
        GhosttyMirror.applyScreen(buffer, COLUMNS, 3, screen, dirty, top.extraArray());
        assertEquals("tóp", text(buffer, 0));
        assertEquals("k", text(buffer, 1));
        assertEquals("bot", text(buffer, 2));
    }
}
