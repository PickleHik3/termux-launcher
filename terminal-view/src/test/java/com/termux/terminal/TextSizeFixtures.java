package com.termux.terminal;

/**
 * Test-only access to the package-private text sizing record, so renderer and selection tests in
 * {@code com.termux.view} can stamp the same blocks OSC 66 writes. The same trick, and the same
 * reason, as {@link StyleFixtures}.
 */
public final class TextSizeFixtures {

    private TextSizeFixtures() {
    }

    /** The packed record of a block, as {@code KittyTextSizing} builds it from an OSC 66. */
    public static int record(int scale, int width, int numerator, int denominator,
                             int verticalAlign, int horizontalAlign, boolean demoted) {
        return KittyTextSizing.packRecord(scale, width, numerator, denominator, verticalAlign,
            horizontalAlign, demoted);
    }

    /** The plainest record there is: a scale, a width, no fraction, default alignment. */
    public static int record(int scale, int width) {
        return record(scale, width, 0, 0, 0, 0, false);
    }

    /** Stamp one cell of a block onto a row, at its offset from the block's anchor. */
    public static void mark(TerminalRow row, int column, int record, int offsetX, int offsetY) {
        row.setTextSizeRecord(column, KittyTextSizing.withOffsets(record, offsetX, offsetY));
    }

    /**
     * Stamp a whole block over {@code rows}, whose first entry is the block's anchor row. Only the
     * records are written; the text is beside the point for anything that compares rows.
     */
    public static void markBlock(TerminalRow[] rows, int firstRow, int column, int record) {
        final int blockRows = KittyTextSizing.rowsOf(record);
        final int blockColumns = KittyTextSizing.columnsOf(record);
        for (int y = 0; y < blockRows; y++)
            for (int x = 0; x < blockColumns; x++)
                mark(rows[firstRow + y], column + x, record, x, y);
    }

    /** Make one cell a plain one again. */
    public static void clear(TerminalRow row, int column) {
        row.setTextSizeRecord(column, 0);
    }
}
