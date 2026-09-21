package com.termux.view.textselection;

import androidx.annotation.Nullable;

import com.termux.terminal.TerminalBuffer;

/**
 * D5 of the kitty text sizing spec: a block is one selection unit. Touching any part of a block
 * selects all of it, so an end of the selection that lands inside one is pulled out to that
 * block's own run of cells.
 *
 * <p>A block's run is on its <em>anchor row only</em>. The selection the rest of the view works in
 * is a stream — the first row from its column to the end, then whole rows, then the last row up to
 * its column — and a rectangle two rows tall cannot be said in it: reaching down to the block's
 * bottom right would select everything after the block on its own row as well. So both ends are
 * pulled onto the anchor row, between the block's first and last column, and the rows the block
 * covers underneath are filled in by the renderer instead, which can paint a rectangle.
 *
 * <p>Either end can end up before the other once it has been pulled onto its anchor row, so the
 * pair is put back in stream order over the corners of both blocks. Copying is then correct
 * through {@code getSelectedText} as it stands, since the anchor contributes the block's text once.
 *
 * <p>Kept apart from {@link TextSelectionCursorController} so the rule can be checked without a
 * View, a MotionEvent or a looper.
 */
final class TextBlockSelection {

    private TextBlockSelection() {
    }

    /**
     * Grow a selection outwards over any block its ends land in, in place.
     *
     * @param selection the selection's corners as {@code x1, y1, x2, y2}, with the rows in the
     *     buffer's external coordinates.
     * @return whether anything moved.
     */
    static boolean snap(TerminalBuffer screen, int[] selection) {
        final TerminalBuffer.TextBlock start = screen.getTextBlockAt(selection[1], selection[0]);
        final TerminalBuffer.TextBlock end = screen.getTextBlockAt(selection[3], selection[2]);
        if (start == null && end == null) return false;
        // Four corners: where each end begins and where it ends, its block's run when it has one.
        final int startFirstRow = firstRow(start, selection[1]);
        final int startFirstColumn = firstColumn(start, selection[0]);
        final int startLastColumn = lastColumn(start, selection[0]);
        final int endFirstRow = firstRow(end, selection[3]);
        final int endFirstColumn = firstColumn(end, selection[2]);
        final int endLastColumn = lastColumn(end, selection[2]);

        final boolean startIsFirst = before(startFirstRow, startFirstColumn, endFirstRow,
            endFirstColumn);
        final int x1 = startIsFirst ? startFirstColumn : endFirstColumn;
        final int y1 = startIsFirst ? startFirstRow : endFirstRow;
        final boolean startIsLast = before(endFirstRow, endLastColumn, startFirstRow,
            startLastColumn);
        final int x2 = startIsLast ? startLastColumn : endLastColumn;
        final int y2 = startIsLast ? startFirstRow : endFirstRow;

        if (x1 == selection[0] && y1 == selection[1] && x2 == selection[2] && y2 == selection[3])
            return false;
        selection[0] = x1;
        selection[1] = y1;
        selection[2] = x2;
        selection[3] = y2;
        return true;
    }

    /** The anchor row of the block an end landed in, or the row the end is already on. */
    private static int firstRow(@Nullable TerminalBuffer.TextBlock block, int row) {
        return block == null ? row : block.row;
    }

    /** The first column of that block's run on its anchor row, or the end's own column. */
    private static int firstColumn(@Nullable TerminalBuffer.TextBlock block, int column) {
        return block == null ? column : block.column;
    }

    /** The last column of that block's run on its anchor row, or the end's own column. */
    private static int lastColumn(@Nullable TerminalBuffer.TextBlock block, int column) {
        return block == null ? column : block.column + block.columns - 1;
    }

    /** Whether one cell comes before another in the order the selection stream reads them. */
    private static boolean before(int row, int column, int otherRow, int otherColumn) {
        return row != otherRow ? row < otherRow : column <= otherColumn;
    }
}
