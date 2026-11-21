package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A Ghostty pane's rows. The screen is the Java buffer the last sync rewrote (dirty rows only); the
 * history stays in libghostty-vt and is read a row at a time when a reader asks, into a small
 * window of reused rows keyed by absolute index. No Java transcript is kept.
 *
 * <p>Readers see at most as much history as the Java transcript would hold
 * ({@link TerminalBuffer#mTotalRows} minus the screen), so both cores show the same rows; the
 * library keeps up to a page more, since it prunes whole pages. Main thread only, and only between
 * a sync and the next write: a row read in between would see the library's history moved under it.
 */
final class GhosttyContent implements TerminalContent {

    /**
     * Rows the window keeps beyond two screens: what link detection reads around the rows on screen
     * ({@link UrlDetector#ROWS_READ_AROUND}), so a frame that looks for links does not push the
     * rows it is about to draw out of the window.
     */
    private static final int WINDOW_SLACK = UrlDetector.ROWS_READ_AROUND;

    private final GhosttyCore mCore;
    private final TerminalBuffer mMainBuffer;
    private final ScrollCount mScrolls;
    /** Gives history rows read here the session's link ids. */
    private final GhosttyLinks mLinks;

    /** The Java buffer holding the screen the last sync wrote: the main buffer or the alternate one. */
    private TerminalBuffer mScreen;
    /** Rows of history the library held at the last sync; 0 while the alternate screen is up. */
    private int mLibraryHistory;

    /** History rows read so far, by slot; a slot holds the row whose absolute index is in {@link #mKeys}. */
    private TerminalRow[] mWindow = new TerminalRow[0];
    private long[] mKeys = new long[0];
    private int mWindowColumns;

    GhosttyContent(GhosttyCore core, TerminalBuffer mainBuffer, ScrollCount scrolls, GhosttyLinks links) {
        mCore = core;
        mMainBuffer = mainBuffer;
        mScreen = mainBuffer;
        mScrolls = scrolls;
        mLinks = links;
    }

    /**
     * Take the state of one sync: the buffer now on screen, how many history rows the library holds
     * and how many rows left the top of the primary screen since the last sync.
     */
    void onSync(TerminalBuffer screen, int libraryHistory, int scrolled) {
        mScreen = screen;
        mLibraryHistory = screen == mMainBuffer ? Math.max(0, libraryHistory) : 0;
        if (scrolled > 0) mScrolls.add(scrolled);
    }

    /**
     * How far a rows-only resize moves the rows on a Ghostty pane's primary screen (see
     * {@link TerminalEmulator#predictRowsOnlyResizeShift}). Losing rows gives up the blank rows at
     * the bottom of the screen first, as far up as the cursor's row, and only the rest go off the
     * top into history. Gaining rows pulls history back down only when the cursor sits on the last
     * row, as many rows as history holds; otherwise the new rows go below and nothing moves.
     * {@code GhosttyContentTest} checks this against the library.
     *
     * @param blankRowsBelowCursor the unbroken run of blank rows at the bottom of the screen, below
     *                             the cursor's row
     */
    static int rowsOnlyResizeShift(int rowChange, int historyRows, int cursorRow, int screenRows,
                                   int blankRowsBelowCursor) {
        if (rowChange < 0) return Math.min(0, rowChange + Math.max(0, blankRowsBelowCursor));
        if (cursorRow < screenRows - 1) return 0;
        return Math.min(rowChange, Math.max(0, historyRows));
    }

    /** The rows the window holds no longer sit where their keys say: a reflow, a reset. */
    void forgetHistory() {
        java.util.Arrays.fill(mKeys, Long.MIN_VALUE);
    }

    @Override
    public int columns() {
        return mScreen.mColumns;
    }

    @Override
    public int screenRows() {
        return mScreen.mScreenRows;
    }

    @Override
    public int historyRows() {
        if (mScreen != mMainBuffer) return 0;
        return Math.min(mLibraryHistory, mMainBuffer.mTotalRows - mMainBuffer.mScreenRows);
    }

    @Override
    public long firstRowIndex() {
        return mScrolls.total - historyRows();
    }

    @Override
    public int takeScrolledRows() {
        return mScrolls.take();
    }

    @NonNull
    @Override
    public TerminalRow row(int externalRow) {
        if (externalRow >= 0) return mScreen.row(externalRow);
        if (externalRow < -historyRows())
            throw new IllegalArgumentException("extRow=" + externalRow + ", historyRows=" + historyRows());
        long key = mScrolls.total + externalRow;
        int slot = slotFor(key);
        TerminalRow row = mWindow[slot];
        if (mKeys[slot] == key) return row;
        GhosttyMirror.Stats stats = GhosttyMirror.stats;
        long start = stats == null ? 0 : System.nanoTime();
        // The newest history row is external row -1 and the library's last.
        int result = mCore.readHistoryRow(mLibraryHistory + externalRow, mCore.historyCells(), mCore.historyExtra());
        if (result < 0) {
            row.clear(TextStyle.NORMAL);
            row.setLineWrap(false);
        } else {
            GhosttyMirror.applyRow(row, mWindowColumns, mCore.historyCells(), 0, mCore.historyExtra(), 0, result,
                historyLinkIds(mWindowColumns));
        }
        mKeys[slot] = key;
        if (stats != null) stats.history(System.nanoTime() - start, 1);
        return row;
    }

    /** The link ids of the history row just read, or null. */
    private int[] historyLinkIds(int columns) {
        mLinks.begin();
        return mLinks.cellIds(mCore.linkRuns(), mCore.linkUris(), 0, columns);
    }

    /** Mark the link ids the history rows read so far carry, so a full link pool keeps them. */
    void markUsedHyperlinkIds(boolean[] used) {
        for (int slot = 0; slot < mWindow.length; slot++)
            if (mWindow[slot] != null && mKeys[slot] != Long.MIN_VALUE) mWindow[slot].markUsedHyperlinkIds(used);
    }

    /**
     * The closest row above or below {@code fromRow} (external, exclusive) that starts a shell
     * prompt, or {@link Integer#MIN_VALUE}, as {@link TerminalBuffer#findRowWithMark} answers on the
     * Java core. Screen rows carry the mark from the sync; history rows are searched in the library
     * by their prompt flag alone, so a jump does not read every row's cells into the window.
     */
    int findPromptRow(int fromRow, boolean backwards) {
        int first = -historyRows();
        int last = mScreen.mScreenRows - 1;
        if (backwards) {
            for (int row = Math.min(fromRow - 1, last); row >= 0; row--)
                if (mScreen.row(row).mShellIntegrationMark == TerminalRow.MARK_PROMPT_START) return row;
            int start = Math.min(fromRow - 1, -1);
            if (start >= first) {
                int found = mCore.findPromptRow(mLibraryHistory + start, mLibraryHistory + first);
                if (found >= 0) return found - mLibraryHistory;
            }
        } else {
            int start = Math.max(fromRow + 1, first);
            if (start <= -1) {
                int found = mCore.findPromptRow(mLibraryHistory + start, mLibraryHistory - 1);
                if (found >= 0) return found - mLibraryHistory;
            }
            for (int row = Math.max(fromRow + 1, 0); row <= last; row++)
                if (mScreen.row(row).mShellIntegrationMark == TerminalRow.MARK_PROMPT_START) return row;
        }
        return Integer.MIN_VALUE;
    }

    /** The slot for an absolute index, sizing the window to the screen first. */
    private int slotFor(long key) {
        int columns = mMainBuffer.mColumns;
        int capacity = 2 * mMainBuffer.mScreenRows + WINDOW_SLACK;
        if (mWindow.length != capacity || mWindowColumns != columns) {
            mWindow = new TerminalRow[capacity];
            mKeys = new long[capacity];
            java.util.Arrays.fill(mKeys, Long.MIN_VALUE);
            mWindowColumns = columns;
        }
        int slot = (int) Math.floorMod(key, (long) capacity);
        if (mWindow[slot] == null) mWindow[slot] = new TerminalRow(columns, TextStyle.NORMAL);
        return slot;
    }

    @NonNull
    @Override
    public String text(int x1, int y1, int x2, int y2, boolean joinWrapped, boolean joinFullLines) {
        return TerminalBuffer.selectedText(this, x1, y1, x2, y2, joinWrapped, joinFullLines);
    }

    /** The Ghostty core keeps no text sizing blocks. */
    @Nullable
    @Override
    public TerminalBuffer.TextBlock textBlockAt(int column, int externalRow) {
        return null;
    }

    @NonNull
    @Override
    public String textBlockText(@NonNull TerminalBuffer.TextBlock block) {
        return "";
    }

    /**
     * Hand the readable history to the Java main buffer, whose transcript is empty in Ghostty mode,
     * so a pane whose core is released (its shell exited) can still be scrolled back. Once, at release:
     * one native read per row.
     */
    void handOverHistory() {
        if (mScreen != mMainBuffer) return;
        int rows = historyRows();
        if (rows <= 0) return;
        mMainBuffer.openEmptyTranscript(rows);
        int[] cells = mCore.historyCells();
        int[] extra = mCore.historyExtra();
        for (int y = -rows; y < 0; y++) {
            int result = mCore.readHistoryRow(mLibraryHistory + y, cells, extra);
            if (result >= 0) GhosttyMirror.applyRow(mMainBuffer.row(y), mMainBuffer.mColumns, cells, 0, extra, 0, result,
                historyLinkIds(mMainBuffer.mColumns));
        }
    }
}
