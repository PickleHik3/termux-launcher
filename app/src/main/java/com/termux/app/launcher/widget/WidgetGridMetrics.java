package com.termux.app.launcher.widget;

import android.graphics.Rect;

import androidx.annotation.NonNull;

/** Exact integer cell geometry for the measured middle body. */
public final class WidgetGridMetrics {
    @NonNull private final Rect body;
    @NonNull private final WidgetGridDefinition grid;
    private final int actionStripHeight;
    private final int edgePadding;
    private final int gap;
    private final boolean rtl;

    public WidgetGridMetrics(@NonNull Rect body, int actionStripHeight, int edgePadding,
                             int gap, @NonNull WidgetGridDefinition grid, boolean rtl) {
        this.body = new Rect(body);
        this.actionStripHeight = Math.max(0, actionStripHeight);
        this.edgePadding = Math.max(0, edgePadding);
        this.gap = Math.max(0, gap);
        this.grid = grid;
        this.rtl = rtl;
    }

    @NonNull public WidgetGridDefinition definition() { return grid; }

    @NonNull public Rect contentBounds() {
        return new Rect(body.left + edgePadding, body.top + actionStripHeight + edgePadding,
            Math.max(body.left + edgePadding, body.right - edgePadding),
            Math.max(body.top + actionStripHeight + edgePadding, body.bottom - edgePadding));
    }

    @NonNull public Rect boundsFor(@NonNull WidgetCellRect cell) {
        Rect content = contentBounds();
        int usableWidth = Math.max(0, content.width() - gap * (grid.columns - 1));
        int usableHeight = Math.max(0, content.height() - gap * (grid.rows - 1));
        int logicalLeft = edge(usableWidth, grid.columns, cell.left) + gap * cell.left;
        int logicalRight = edge(usableWidth, grid.columns, cell.right) + gap * (cell.right - 1);
        int top = content.top + edge(usableHeight, grid.rows, cell.top) + gap * cell.top;
        int bottom = content.top + edge(usableHeight, grid.rows, cell.bottom)
            + gap * (cell.bottom - 1);
        int left = rtl ? content.right - logicalRight : content.left + logicalLeft;
        int right = rtl ? content.right - logicalLeft : content.left + logicalRight;
        return new Rect(left, top, right, bottom);
    }

    /** Smallest span whose exact first-position rectangle contains the requested outer pixels. */
    @NonNull public Span spanForPixels(int desiredWidth, int desiredHeight) {
        int columns = smallestColumnSpan(Math.max(1, desiredWidth));
        int rows = smallestRowSpan(Math.max(1, desiredHeight));
        return new Span(columns, rows, columns > 0 && rows > 0);
    }

    private int smallestColumnSpan(int pixels) {
        for (int span = 1; span <= grid.columns; span++) {
            if (boundsFor(new WidgetCellRect(0, 0, span, 1)).width() >= pixels) return span;
        }
        return -1;
    }

    private int smallestRowSpan(int pixels) {
        for (int span = 1; span <= grid.rows; span++) {
            if (boundsFor(new WidgetCellRect(0, 0, 1, span)).height() >= pixels) return span;
        }
        return -1;
    }

    // Value equality keys the catalog cache: any geometry change must miss it.
    @Override public boolean equals(Object other) {
        if (!(other instanceof WidgetGridMetrics)) return false;
        WidgetGridMetrics that = (WidgetGridMetrics) other;
        return body.equals(that.body) && grid.equals(that.grid)
            && actionStripHeight == that.actionStripHeight && edgePadding == that.edgePadding
            && gap == that.gap && rtl == that.rtl;
    }

    @Override public int hashCode() {
        int result = body.hashCode();
        result = 31 * result + grid.hashCode();
        result = 31 * result + actionStripHeight;
        result = 31 * result + edgePadding;
        result = 31 * result + gap;
        return 31 * result + (rtl ? 1 : 0);
    }

    private static int edge(int pixels, int cells, int edge) {
        int base = pixels / cells;
        int remainder = pixels % cells;
        return edge * base + Math.min(edge, remainder);
    }

    public static final class Span {
        public final int columns;
        public final int rows;
        public final boolean fits;
        Span(int columns, int rows, boolean fits) {
            this.columns = columns;
            this.rows = rows;
            this.fits = fits;
        }
    }
}
