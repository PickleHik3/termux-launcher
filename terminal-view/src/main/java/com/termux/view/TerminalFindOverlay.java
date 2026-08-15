package com.termux.view;

import java.util.ArrayList;
import java.util.List;

/**
 * What a find session wants painted over the transcript: every match, which one is current, a
 * copy-mode cursor and the selection being dragged out from it.
 *
 * <p>This is deliberately a dumb value holder in row/column space. It owns no search, no key
 * handling and no colours of its own beyond the ones handed to it, so the whole find feature can be
 * unit tested above it and the renderer below it stays a pure projection of these numbers onto
 * cells. Rows are transcript rows in the same coordinate system the renderer draws in — negative
 * above the screen, {@code 0..rows-1} on it.</p>
 */
public final class TerminalFindOverlay {

    /** Selection shapes, matching vim's v, V and Ctrl-V. */
    public static final int SELECTION_NONE = 0;
    public static final int SELECTION_CHAR = 1;
    public static final int SELECTION_LINE = 2;
    public static final int SELECTION_BLOCK = 3;

    /** One matched run of cells on a single row; {@code endColumn} is inclusive. */
    public static final class Span {
        public final int row;
        public final int startColumn;
        public final int endColumn;

        public Span(int row, int startColumn, int endColumn) {
            this.row = row;
            this.startColumn = startColumn;
            this.endColumn = endColumn;
        }
    }

    public final List<Span> spans = new ArrayList<>();
    /** Index into {@link #spans} drawn as the current match, or -1 for none. */
    public int currentSpan = -1;

    public boolean cursorVisible;
    public int cursorRow;
    public int cursorColumn;

    public int selectionMode = SELECTION_NONE;
    public int anchorRow;
    public int anchorColumn;

    public int matchColor = 0x33FFD54F;
    public int currentMatchColor = 0x66FFB300;
    public int selectionColor = 0x4D64B5F6;
    public int cursorColor = 0xCCFFFFFF;

    public boolean isEmpty() {
        return spans.isEmpty() && !cursorVisible && selectionMode == SELECTION_NONE;
    }

    public void clearSpans() {
        spans.clear();
        currentSpan = -1;
    }
}
