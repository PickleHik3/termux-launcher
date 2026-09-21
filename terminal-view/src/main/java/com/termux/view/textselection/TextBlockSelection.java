package com.termux.view.textselection;

import com.termux.terminal.TerminalBuffer;

/**
 * D5 of the kitty text sizing spec: a block is one selection unit. Touching any part of a block
 * selects all of it, so an end of the selection that lands inside one is pushed out to that
 * block's own corner — the start to its top left, the end to its bottom right.
 *
 * <p>That is the whole rule. The highlight then covers the block on every row it spans, because
 * the rows between the two ends are selected across their full width anyway, and the handles sit
 * at the block's corners because they are drawn from these same four numbers. Copying a block's
 * text once is the buffer's own business and was settled by the model phase.
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
        boolean moved = false;
        final TerminalBuffer.TextBlock start = screen.getTextBlockAt(selection[1], selection[0]);
        if (start != null && (selection[0] != start.column || selection[1] != start.row)) {
            selection[0] = start.column;
            selection[1] = start.row;
            moved = true;
        }
        final TerminalBuffer.TextBlock end = screen.getTextBlockAt(selection[3], selection[2]);
        if (end != null) {
            final int lastColumn = end.column + end.columns - 1;
            final int lastRow = end.row + end.rows - 1;
            if (selection[2] != lastColumn || selection[3] != lastRow) {
                selection[2] = lastColumn;
                selection[3] = lastRow;
                moved = true;
            }
        }
        return moved;
    }
}
