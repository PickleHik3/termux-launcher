package com.termux.terminal;

/**
 * Copies rows from libghostty-vt into Java {@link TerminalRow}s: the screen rows a sync changed,
 * into the Java buffer that holds the screen, and history rows one at a time when a reader asks
 * ({@link GhosttyContent}). Pure: it takes the cell arrays the shim fills (see
 * {@link GhosttyNative#CELL_INTS}), so tests drive it with plain arrays.
 */
final class GhosttyMirror {

    /** Rows that have gone into the library's history, read one at a time when a reader asks. */
    interface HistorySource {
        /**
         * Fill {@code cells} and {@code extra} with history row {@code y} (0 is the oldest kept).
         *
         * @return the row result (extra count | {@link GhosttyNative#ROW_WRAPPED}), or -1 if it is gone
         */
        int readHistoryRow(int y, int[] cells, int[] extra);
    }

    private GhosttyMirror() {}

    /**
     * Where the mirror's cost goes, split by part. Only the parse bench sets {@link #stats}; with
     * it null the app's path pays one static read per call and no clock.
     */
    static final class Stats {
        long syncNanos, syncMaxNanos;
        long screenNanos, screenMaxNanos, screenRows;
        long historyNanos, historyMaxNanos, historyRows;

        void sync(long nanos) {
            syncNanos += nanos;
            syncMaxNanos = Math.max(syncMaxNanos, nanos);
        }

        /** History rows read on demand; nothing copies history per turn any more. */
        void history(long nanos, int rows) {
            historyNanos += nanos;
            historyMaxNanos = Math.max(historyMaxNanos, nanos);
            historyRows += rows;
        }
    }

    /** Set by the bench around a run; null otherwise. */
    static Stats stats;

    /**
     * Write one row's cells into {@code row}. Wide characters take their two cells from the first;
     * a spacer cell adds nothing. Extra code points (combining marks and the like) join the cell
     * they belong to, read in order from {@code extra} starting at {@code extraOffset}.
     *
     * @return the offset just past this row's extra code points
     */
    static int applyRow(TerminalRow row, int columns, int[] cells, int cellOffset, int[] extra,
                        int extraOffset, int rowResult) {
        row.clear(TextStyle.NORMAL);
        row.mLineWrap = (rowResult & GhosttyNative.ROW_WRAPPED) != 0;
        int extraIndex = extraOffset;
        for (int x = 0; x < columns; x++) {
            int base = cellOffset + x * GhosttyNative.CELL_INTS;
            int attrs = cells[base + GhosttyNative.CELL_ATTRS];
            int wide = GhosttyStyle.wide(attrs);
            int extras = GhosttyStyle.extraCodePoints(attrs);
            if (wide == GhosttyNative.WIDE_SPACER_TAIL) {
                extraIndex += extras;
                continue;
            }
            long style = GhosttyStyle.toTextStyle(attrs, cells[base + GhosttyNative.CELL_FG],
                cells[base + GhosttyNative.CELL_BG]);
            int codePoint = cells[base + GhosttyNative.CELL_CODEPOINT];
            if (codePoint <= 0 || codePoint > Character.MAX_CODE_POINT || wide == GhosttyNative.WIDE_SPACER_HEAD) {
                // Empty, but it may still carry a background colour.
                if (style != TextStyle.NORMAL) row.setChar(x, ' ', style);
                extraIndex += extras;
                continue;
            }
            // A wide character in the last column cannot fit; leave the cell blank rather than throw.
            if (WcWidth.width(codePoint) == 2 && x == columns - 1) {
                row.setChar(x, ' ', style);
                extraIndex += extras;
                continue;
            }
            row.setChar(x, codePoint, style,
                GhosttyStyle.decorationColor(cells[base + GhosttyNative.CELL_UNDERLINE]), 0);
            for (int i = 0; i < extras; i++, extraIndex++) {
                if (extraIndex >= extra.length) break;
                int extraCodePoint = extra[extraIndex];
                if (extraCodePoint > 0 && extraCodePoint <= Character.MAX_CODE_POINT)
                    row.appendCodePointToCell(x, extraCodePoint);
            }
        }
        return extraIndex;
    }

    /**
     * Copy the rows the last sync reported as changed onto the screen of {@code buffer}.
     *
     * @param dirty per screen row, the row result or -1 when the row did not change
     */
    static void applyScreen(TerminalBuffer buffer, int columns, int rows, int[] screen, int[] dirty, int[] extra) {
        Stats stats = GhosttyMirror.stats;
        long start = stats == null ? 0 : System.nanoTime();
        int copied = 0;
        int extraOffset = 0;
        for (int y = 0; y < rows; y++) {
            int result = dirty[y];
            if (result < 0) continue;
            copied++;
            TerminalRow row = buffer.allocateFullLineIfNecessary(buffer.externalToInternalRow(y));
            applyRow(row, columns, screen, y * columns * GhosttyNative.CELL_INTS, extra, extraOffset, result);
            extraOffset += result & GhosttyNative.ROW_EXTRA_MASK;
        }
        if (stats != null) {
            long nanos = System.nanoTime() - start;
            stats.screenNanos += nanos;
            stats.screenMaxNanos = Math.max(stats.screenMaxNanos, nanos);
            stats.screenRows += copied;
        }
    }
}
