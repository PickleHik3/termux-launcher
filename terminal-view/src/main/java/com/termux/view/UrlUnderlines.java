package com.termux.view;

import androidx.annotation.Nullable;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.UrlDetector;

import java.util.Arrays;
import java.util.List;

/**
 * Which cells of the visible rows hold a plain-text URL, for the renderer to underline.
 *
 * <p>Touch has no hover, so the addresses a tap would open are marked all the time — which means
 * the question is asked every frame. The answer only changes when the text does, so the rows the
 * detector would read are hashed and the last answer is kept while the hash holds; scrolling or a
 * keystroke costs one regex pass over the screen, an unchanged frame costs a walk over its chars.
 */
final class UrlUnderlines {

    /** Rows past the visible edge that can carry an address into or out of view. */
    private static final int CONTEXT_ROWS = 3;

    private static final int[] NONE = new int[0];

    private boolean mValid;
    private long mHash;
    private int mTopRow;
    private int mEndRow;
    private int mColumns;

    /** Per visible row: pairs of {@code firstColumn, endColumnExclusive}, or null when the row has none. */
    private int[][] mSegments = new int[0][];

    /** Per visible row: a summary of its segments, folded into the row cache's change detection. */
    private int[] mKeys = new int[0];

    /**
     * Compute — or confirm — the underlines for rows {@code [topRow, endRow)}. Must run once per
     * frame before any row is drawn or its cache consulted.
     */
    void prepare(TerminalBuffer screen, int topRow, int endRow, int columns, int screenRows,
                 boolean enabled) {
        final int visible = Math.max(0, endRow - topRow);
        if (mSegments.length < visible) {
            mSegments = new int[visible][];
            mKeys = new int[visible];
            mValid = false;
        }
        if (!enabled) {
            if (mValid) {
                Arrays.fill(mSegments, null);
                Arrays.fill(mKeys, 0);
            }
            mValid = false;
            return;
        }
        final long hash = hashRows(screen, screenRows, topRow - CONTEXT_ROWS, endRow + CONTEXT_ROWS);
        if (mValid && hash == mHash && topRow == mTopRow && endRow == mEndRow && columns == mColumns)
            return;
        Arrays.fill(mSegments, null);
        Arrays.fill(mKeys, 0);
        List<UrlDetector.UrlSpan> spans = UrlDetector.find(screen, topRow, endRow - 1);
        for (UrlDetector.UrlSpan span : spans) {
            for (int s = 0; s < span.segmentCount(); s++) {
                int index = span.segmentRow(s) - topRow;
                if (index < 0 || index >= visible) continue;
                int start = span.segmentStartColumn(s);
                int end = span.segmentEndColumn(s);
                int[] existing = mSegments[index];
                int n = existing == null ? 0 : existing.length;
                int[] grown = Arrays.copyOf(existing == null ? NONE : existing, n + 2);
                grown[n] = start;
                grown[n + 1] = end;
                mSegments[index] = grown;
                mKeys[index] = (mKeys[index] * 31 + start) * 31 + end + 1;
            }
        }
        mValid = true;
        mHash = hash;
        mTopRow = topRow;
        mEndRow = endRow;
        mColumns = columns;
    }

    /** The URL cells of the visible row at {@code index}, as column pairs, or null. */
    @Nullable
    int[] segmentsFor(int index) {
        return index >= 0 && index < mSegments.length ? mSegments[index] : null;
    }

    /** Zero for a row without URL cells. */
    int keyFor(int index) {
        return index >= 0 && index < mKeys.length ? mKeys[index] : 0;
    }

    private static long hashRows(TerminalBuffer screen, int screenRows, int firstRow, int lastRowExclusive) {
        final int minRow = -screen.getActiveTranscriptRows();
        final int maxRow = screenRows;
        firstRow = Math.max(minRow, firstRow);
        lastRowExclusive = Math.min(maxRow, lastRowExclusive);
        long hash = 1125899906842597L;
        for (int row = firstRow; row < lastRowExclusive; row++) {
            TerminalRow line = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final int used = line.getSpaceUsed();
            hash = hash * 31 + used;
            final char[] text = line.mText;
            for (int i = 0; i < used; i++) hash = hash * 31 + text[i];
            hash = hash * 31 + (screen.getLineWrap(row) ? 1 : 0);
        }
        return hash;
    }
}
