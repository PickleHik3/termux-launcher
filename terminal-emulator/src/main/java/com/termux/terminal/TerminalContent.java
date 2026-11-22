package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What a pane shows and holds, row by row: the one way readers (the renderer, selection, find,
 * links, transcripts) reach the screen and its history, whichever core parses the output. Cursor
 * and modes stay on {@link TerminalEmulator}. Main thread only.
 *
 * <p>Rows are external: {@code -historyRows()} is the oldest readable row, {@code 0} the top of the
 * screen and {@code screenRows() - 1} its bottom. A row's absolute index,
 * {@code firstRowIndex() + historyRows() + externalRow}, names it for as long as it is readable.
 */
public interface TerminalContent {

    int columns();

    int screenRows();

    /** Rows above the screen that can be read now. */
    int historyRows();

    /** The absolute index of the oldest readable row; it only grows as rows scroll off. */
    long firstRowIndex();

    /** Rows that left the top of the screen since the last call. */
    int takeScrolledRows();

    /**
     * The row at {@code externalRow}, from {@code -historyRows()} to {@code screenRows() - 1}.
     * Read it, do not keep it: the object may hold another row after the next call or the next
     * output turn.
     */
    @NonNull
    TerminalRow row(int externalRow);

    /**
     * The text from ({@code x1}, {@code y1}) to ({@code x2}, {@code y2}), both inclusive, clamped
     * to the readable rows. Trailing blanks of a row are dropped unless it wraps; rows end in a
     * newline unless {@code joinWrapped} and the row wraps, or {@code joinFullLines} and its text
     * fills the width.
     */
    @NonNull
    String text(int x1, int y1, int x2, int y2, boolean joinWrapped, boolean joinFullLines);

    /** The text sizing block a cell belongs to, or null; always null where blocks are not kept. */
    @Nullable
    TerminalBuffer.TextBlock textBlockAt(int column, int externalRow);

    /** The text a block draws; empty when its anchor has gone. */
    @NonNull
    String textBlockText(@NonNull TerminalBuffer.TextBlock block);

    /** Every readable row and the screen. */
    default int activeRows() {
        return historyRows() + screenRows();
    }

    /** {@link #text(int, int, int, int, boolean, boolean)} joining wrapped rows. */
    @NonNull
    default String text(int x1, int y1, int x2, int y2) {
        return text(x1, y1, x2, y2, true, false);
    }

    /**
     * All of history and the screen as text, trimmed. Reads every row: on the Ghostty core that is
     * one native read per history row, so callers ask for it once per action, never per frame.
     */
    @NonNull
    default String transcriptText(boolean joinWrapped, boolean joinFullLines) {
        return text(0, -historyRows(), columns(), screenRows(), joinWrapped, joinFullLines).trim();
    }
}
