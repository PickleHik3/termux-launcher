package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/** Immutable page and cell geometry for the horizontal drawer. */
public final class AppDrawerHorizontalGridMetrics {

    public static final int MIN_ROWS = 2;
    public static final int MAX_ROWS = 6;

    public final int columns;
    public final int rows;
    public final int itemsPerPage;
    public final float usablePageWidthPx;
    public final float usablePageHeightPx;
    public final float cellWidthPx;
    public final float iconPx;
    public final float rowHeightPx;

    private AppDrawerHorizontalGridMetrics(int columns, int rows, float usablePageWidthPx,
                                           float usablePageHeightPx, float cellWidthPx,
                                           float iconPx, float rowHeightPx) {
        this.columns = columns;
        this.rows = rows;
        this.itemsPerPage = Math.max(1, columns * rows);
        this.usablePageWidthPx = usablePageWidthPx;
        this.usablePageHeightPx = usablePageHeightPx;
        this.cellWidthPx = cellWidthPx;
        this.iconPx = iconPx;
        this.rowHeightPx = rowHeightPx;
    }

    /**
     * Resolves full-width page geometry from the pager's already-reserved usable bounds.
     * Requested columns/rows are independent preferences; zero means AUTO.
     */
    @NonNull
    public static AppDrawerHorizontalGridMetrics resolve(float usableWidthPx,
                                                         float usableHeightPx,
                                                         float density,
                                                         float labelHeightPx,
                                                         int requestedColumns,
                                                         int requestedRows) {
        float width = Math.max(0f, finiteOrZero(usableWidthPx));
        float height = Math.max(0f, finiteOrZero(usableHeightPx));
        AppDrawerGridMetrics cells = AppDrawerGridMetrics.resolve(width, density,
            labelHeightPx, requestedColumns);
        float naturalRowHeight = Math.max(1f, finiteOrOne(cells.rowHeightPx));
        int physicallyFit = Math.max(1, Math.min(MAX_ROWS,
            (int) Math.floor(height / naturalRowHeight)));
        int rows;
        if (requestedRows == 0) {
            rows = physicallyFit;
        } else {
            int explicit = Math.max(MIN_ROWS, Math.min(MAX_ROWS, requestedRows));
            rows = Math.max(1, Math.min(explicit, physicallyFit));
        }
        // Fill the usable height only when it is taller than the cells need. A short height keeps
        // the natural row finite and relies on clipping rather than producing a zero-sized cell.
        float rowHeight = height > 0f ? Math.min(naturalRowHeight, height / rows) : naturalRowHeight;
        return new AppDrawerHorizontalGridMetrics(cells.columns, rows, width, height,
            cells.cellWidthPx, Math.min(cells.iconPx,
                Math.max(0f, rowHeight - Math.max(0f, labelHeightPx)
                    - (AppDrawerGridMetrics.LABEL_GAP_DP + AppDrawerGridMetrics.ROW_BOTTOM_DP)
                    * (density > 0f ? density : 1f))), rowHeight);
    }

    private static float finiteOrZero(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 0f : value;
    }

    private static float finiteOrOne(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 1f : value;
    }
}
