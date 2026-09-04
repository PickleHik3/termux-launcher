package com.termux.app.statusbar;

import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

/**
 * Where the clock and the wall's two navigation tiles (Widgets, Display) sit inside the 68dp top
 * slot, applied by {@link TopPaneWidgetSlot#onLayout}.
 *
 * <p>The slot is split into N equal cells (N = 1 plus however many tiles are shown), so a tile is
 * the same dimensions as the clock rather than some incidental leftover strip. Cell order follows
 * the clock's alignment, and Widgets always sits to the left of Display, mirroring the swipe
 * direction: the Widgets page is a swipe left of the terminal, Display a swipe right.
 */
public final class TopPaneTileLayoutPolicy {

    /** Where the clock and the wall's navigation tiles sit in the top slot. */
    public static final class Result {
        @NonNull public final Rect clock;
        @NonNull public final Rect widgets;
        @NonNull public final Rect display;
        public final boolean clockCompact;

        private Result(Rect clock, Rect widgets, Rect display, boolean clockCompact) {
            this.clock = clock;
            this.widgets = widgets;
            this.display = display;
            this.clockCompact = clockCompact;
        }
    }

    private TopPaneTileLayoutPolicy() {}

    @NonNull
    public static Result calculate(int widthPx, int heightPx, int gutterPx, int gapPx,
                                    @Nullable String clockAlignment, boolean widgetsTile,
                                    boolean displayTile, int clockFullDesiredWidthPx,
                                    boolean rtl) {
        int cellCount = 1 + (widgetsTile ? 1 : 0) + (displayTile ? 1 : 0);
        int available = widthPx - gutterPx * 2;
        int height = Math.max(0, heightPx);

        int[] cellLefts = new int[cellCount];
        int[] cellWidths = new int[cellCount];
        boolean fits = available > 0 && height > 0
            && computeCells(available, gapPx, cellCount, cellLefts, cellWidths);

        Rect clock = new Rect();
        Rect widgets = new Rect();
        Rect display = new Rect();
        int clockCellWidth = 0;

        if (fits) {
            int clockIndex = clockCellIndex(clockAlignment, cellCount);
            int nextTileIndex = 0;
            for (int i = 0; i < cellCount; i++) {
                int left = gutterPx + cellLefts[i];
                Rect rect = new Rect(left, 0, left + cellWidths[i], height);
                if (i == clockIndex) {
                    clock = rect;
                    clockCellWidth = cellWidths[i];
                } else if (nextTileIndex == 0 && widgetsTile) {
                    widgets = rect;
                    nextTileIndex++;
                } else {
                    display = rect;
                    nextTileIndex++;
                }
            }
        } else if (available > 0 && height > 0) {
            // Not enough room for every cell: give whatever usable width is left to the clock
            // alone rather than inventing tile geometry that does not fit.
            clock = new Rect(gutterPx, 0, gutterPx + available, height);
            clockCellWidth = available;
        }

        if (rtl) {
            clock = mirror(clock, widthPx);
            widgets = mirror(widgets, widthPx);
            display = mirror(display, widthPx);
        }

        boolean clockCompact = clockFullDesiredWidthPx > clockCellWidth;
        return new Result(clock, widgets, display, clockCompact);
    }

    /**
     * Fills {@code lefts}/{@code widths} (offsets from the start of the usable span) for
     * {@code cellCount} equal cells separated by {@code gapPx}, giving any integer-division
     * remainder to the last cell. Returns false (leaving the arrays untouched) when the usable
     * width cannot hold {@code cellCount} cells of at least 1px.
     */
    private static boolean computeCells(int available, int gapPx, int cellCount,
                                         int[] lefts, int[] widths) {
        int gap = Math.max(0, gapPx);
        int totalGap = gap * (cellCount - 1);
        int usable = available - totalGap;
        if (usable < cellCount) return false;
        int cellWidth = usable / cellCount;
        int remainder = usable - cellWidth * cellCount;
        int cursor = 0;
        for (int i = 0; i < cellCount; i++) {
            int width = cellWidth + (i == cellCount - 1 ? remainder : 0);
            lefts[i] = cursor;
            widths[i] = width;
            cursor += width + gap;
        }
        return true;
    }

    /** left -> 0, center -> N/2, right -> N-1; anything unrecognised falls back to left. */
    private static int clockCellIndex(@Nullable String alignment, int cellCount) {
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER.equals(alignment))
            return cellCount / 2;
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT.equals(alignment))
            return cellCount - 1;
        return 0;
    }

    private static Rect mirror(Rect value, int width) {
        if (value.isEmpty()) return new Rect();
        return new Rect(width - value.right, value.top, width - value.left, value.bottom);
    }
}
