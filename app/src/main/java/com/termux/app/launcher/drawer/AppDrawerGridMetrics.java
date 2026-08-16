package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * Pure grid sizing for the app drawer: how many columns fit the plane, how large an icon may be in
 * one of them and how tall a row is.
 *
 * <p>Density arrives as a number and every dp constant is multiplied by it here, so the class never
 * sees a {@code Context} and its test runs under bare JUnit. It is also the single place the icon
 * ceiling lives: at 4x density a 48dp icon is 192px and one rendered entry is a ~294KB pair of
 * bitmaps, so the cap is what keeps a 400-app scroll inside the shared icon cache's byte budget
 * rather than a size the grid happened to ask for.
 *
 * <p>The column count is a rounded division rather than a floor: rounding puts the cell within half
 * a target of {@link #TARGET_CELL_DP} on either side, which is what keeps a 360dp phone at four
 * columns instead of dropping the last one to a floor, while the clamp keeps a tablet-width plane
 * and a very narrow one both legible.
 *
 * <p>Instances are immutable results of {@link #resolve}. B-7 will feed the deferred grid
 * preferences in through the same entry point; none of them are read here.
 */
public final class AppDrawerGridMetrics {

    /** Cell width the column count aims for. */
    public static final float TARGET_CELL_DP = 84f;
    public static final int MIN_COLUMNS = 4;
    public static final int MAX_COLUMNS = 6;
    /** Ceiling on the rendered icon, which is also the largest key the drawer puts in the icon cache. */
    public static final float MAX_ICON_DP = 48f;
    /** Share of the cell an icon may take before the label has no room left to be read. */
    public static final float ICON_CELL_FRACTION = 0.58f;
    /** Gap between the icon and its label. */
    public static final float LABEL_GAP_DP = 6f;
    /** Padding under the label, so two rows do not read as one block. */
    public static final float ROW_BOTTOM_DP = 10f;

    /** Columns across the plane, always within {@link #MIN_COLUMNS}..{@link #MAX_COLUMNS}. */
    public final int columns;
    /** Content width divided by {@link #columns}; never negative. */
    public final float cellWidthPx;
    /** Rendered icon size, capped at {@link #MAX_ICON_DP}. */
    public final float iconPx;
    /** Icon, gap, label and bottom padding. */
    public final float rowHeightPx;

    private AppDrawerGridMetrics(int columns, float cellWidthPx, float iconPx, float rowHeightPx) {
        this.columns = columns;
        this.cellWidthPx = cellWidthPx;
        this.iconPx = iconPx;
        this.rowHeightPx = rowHeightPx;
    }

    /** The column count alone, for callers that only need to know whether a rebind changed it. */
    public static int resolveColumns(float planeWidthDp) {
        int columns = Math.round(planeWidthDp / TARGET_CELL_DP);
        return Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, columns));
    }

    /**
     * @param contentWidthPx the plane's width minus whatever horizontal padding the caller applies
     * @param density        {@code DisplayMetrics.density}
     * @param labelHeightPx  the measured height of a single-line label at the cell's text size
     */
    @NonNull
    public static AppDrawerGridMetrics resolve(float contentWidthPx, float density,
                                               float labelHeightPx) {
        return resolve(contentWidthPx, density, labelHeightPx, 0);
    }

    /**
     * Resolves the grid with an optional explicit column count.
     *
     * @param requestedColumns {@code 0} keeps the shipped width-based AUTO calculation; any other
     *     value is clamped to {@link #MIN_COLUMNS}..{@link #MAX_COLUMNS}
     */
    @NonNull
    public static AppDrawerGridMetrics resolve(float contentWidthPx, float density,
                                               float labelHeightPx, int requestedColumns) {
        return resolve(contentWidthPx, density, labelHeightPx, requestedColumns, 0);
    }

    /** Explicit icon target is applied before the established cell-width and 48dp clamps. */
    @NonNull
    public static AppDrawerGridMetrics resolve(float contentWidthPx, float density,
                                               float labelHeightPx, int requestedColumns,
                                               int requestedIconDp) {
        // A zero density would take the column count to NaN and the row height with it; the drawer
        // is rebuilt on every configuration change, so a degenerate frame must degrade, not poison.
        float d = density > 0f ? density : 1f;
        float width = Math.max(0f, contentWidthPx);
        int columns = requestedColumns == 0
            ? resolveColumns(width / d)
            : Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, requestedColumns));
        float cellWidthPx = width / columns;
        float iconTargetPx = requestedIconDp == 36 || requestedIconDp == 40
            || requestedIconDp == 44 || requestedIconDp == 48
            ? requestedIconDp * d : MAX_ICON_DP * d;
        float iconPx = Math.min(iconTargetPx, cellWidthPx * ICON_CELL_FRACTION);
        float rowHeightPx = iconPx + (LABEL_GAP_DP * d)
            + Math.max(0f, labelHeightPx) + (ROW_BOTTOM_DP * d);
        return new AppDrawerGridMetrics(columns, cellWidthPx, iconPx, rowHeightPx);
    }

    @NonNull
    @Override
    public String toString() {
        return "AppDrawerGridMetrics(columns=" + columns + ", cell=" + cellWidthPx
            + ", icon=" + iconPx + ", row=" + rowHeightPx + ")";
    }
}
