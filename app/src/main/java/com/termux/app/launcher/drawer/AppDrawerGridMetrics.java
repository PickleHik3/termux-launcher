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
 * <p>Instances are immutable results of {@link #resolve}, which also takes the user's icon size and
 * column count (0 is Automatic for both).
 */
public final class AppDrawerGridMetrics {

    /** Cell width the column count aims for. */
    public static final float TARGET_CELL_DP = 84f;
    public static final int MIN_COLUMNS = 4;
    public static final int MAX_COLUMNS = 6;
    /** Recycled app cells kept for a full screen of the widest grid plus a row of slack. */
    public static final int GRID_POOL_CELLS = MAX_COLUMNS * 9;
    /** Ceiling on the rendered icon, which is also the largest key the drawer puts in the icon cache. */
    public static final float MAX_ICON_DP = 48f;
    /** Share of the cell an icon may take before the label has no room left to be read. */
    public static final float ICON_CELL_FRACTION = 0.58f;
    /** Gap between the icon and its label. */
    public static final float LABEL_GAP_DP = 6f;
    /**
     * Air above the icon inside a row: the highlight bead's padding plus its spare, so the bead
     * has the same room above the icon as beside it and under the label.
     */
    public static final float ROW_TOP_DP =
        AppDrawerHighlightShape.PADDING_DP + AppDrawerHighlightShape.SPARE_DP;
    /**
     * Padding under the label, so two rows do not read as one block; the same figure as
     * {@link #ROW_TOP_DP}, which takes it from what used to be a 10dp tail.
     */
    public static final float ROW_BOTTOM_DP = ROW_TOP_DP;

    /**
     * {@link #ROW_TOP_DP} in whole pixels: the bead's padding and its spare, each rounded the way
     * the cell rounds them, so the air above the icon never comes up short of what the bead asks
     * for (at density 2.75 the dp figure is 19.25 while the bead needs 17 + 3).
     */
    public static float rowTopPx(float density) {
        float d = density > 0f ? density : 1f;
        return Math.round(AppDrawerHighlightShape.PADDING_DP * d)
            + Math.max(1, Math.round(AppDrawerHighlightShape.SPARE_DP * d));
    }

    /** {@link #ROW_BOTTOM_DP} in whole pixels; the same figure as {@link #rowTopPx}. */
    public static float rowBottomPx(float density) {
        return rowTopPx(density);
    }
    /**
     * Air a label keeps inside its own cell on each side, so two long names never touch and the
     * outer columns stand off the card's edge. Every layout's cell label reads it.
     */
    public static final float LABEL_SIDE_INSET_DP = 4f;

    /** {@link #LABEL_SIDE_INSET_DP} in pixels. */
    public static float labelSideInsetPx(float density) {
        return LABEL_SIDE_INSET_DP * (density > 0f ? density : 1f);
    }

    /** Columns across the plane, always within {@link #MIN_COLUMNS}..{@link #MAX_COLUMNS}. */
    public final int columns;
    /** Content width divided by {@link #columns}; never negative. */
    public final float cellWidthPx;
    /** Rendered icon size; capped at {@link #MAX_ICON_DP} on Automatic, at the cell on a chosen size. */
    public final float iconPx;
    /** Icon, gap, label and bottom padding. */
    public final float rowHeightPx;

    private AppDrawerGridMetrics(int columns, float cellWidthPx, float iconPx, float rowHeightPx) {
        this.columns = columns;
        this.cellWidthPx = cellWidthPx;
        this.iconPx = iconPx;
        this.rowHeightPx = rowHeightPx;
    }

    /** Value equality, so a re-resolve of unchanged inputs can be told from a real change. */
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppDrawerGridMetrics)) return false;
        AppDrawerGridMetrics m = (AppDrawerGridMetrics) o;
        return columns == m.columns && Float.compare(cellWidthPx, m.cellWidthPx) == 0
            && Float.compare(iconPx, m.iconPx) == 0
            && Float.compare(rowHeightPx, m.rowHeightPx) == 0;
    }

    @Override public int hashCode() {
        int h = columns;
        h = 31 * h + Float.floatToIntBits(cellWidthPx);
        h = 31 * h + Float.floatToIntBits(iconPx);
        h = 31 * h + Float.floatToIntBits(rowHeightPx);
        return h;
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

    /**
     * The icon sizes Settings offers, in dp: Home's three presets ({@code HomeIconSize}). Anything
     * else asked of the metrics is Automatic.
     */
    public static final int[] ICON_STEPS_DP = {48, 54, 60};

    /** Whether {@code dp} is one of {@link #ICON_STEPS_DP}; 0 (Automatic) and strays are not. */
    public static boolean isIconStep(int dp) {
        for (int step : ICON_STEPS_DP) if (step == dp) return true;
        return false;
    }

    /**
     * A chosen icon size is honoured and the {@link #MAX_ICON_DP} ceiling is Automatic's alone.
     * With Automatic columns the count drops (never below {@link #MIN_COLUMNS}) until the cell
     * holds the icon; with chosen columns, or at the minimum, the icon is capped to what the cell
     * holds. Automatic icon and columns resolve exactly as they always did.
     *
     * @param requestedIconDp {@code 0} for Automatic, else one of {@link #ICON_STEPS_DP}
     */
    @NonNull
    public static AppDrawerGridMetrics resolve(float contentWidthPx, float density,
                                               float labelHeightPx, int requestedColumns,
                                               int requestedIconDp) {
        // A zero density would take the column count to NaN and the row height with it; the drawer
        // is rebuilt on every configuration change, so a degenerate frame must degrade, not poison.
        float d = density > 0f ? density : 1f;
        float width = Math.max(0f, contentWidthPx);
        boolean chosenIcon = isIconStep(requestedIconDp);
        float iconTargetPx = (chosenIcon ? requestedIconDp : MAX_ICON_DP) * d;
        int columns;
        if (requestedColumns == 0) {
            columns = resolveColumns(width / d);
            if (chosenIcon) {
                int holding = (int) Math.floor(width / (iconTargetPx / ICON_CELL_FRACTION));
                columns = Math.max(MIN_COLUMNS, Math.min(columns, holding));
            }
        } else {
            columns = Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, requestedColumns));
        }
        float cellWidthPx = width / columns;
        float iconPx = Math.min(iconTargetPx, cellWidthPx * ICON_CELL_FRACTION);
        float rowHeightPx = rowTopPx(d) + iconPx + (LABEL_GAP_DP * d)
            + Math.max(0f, labelHeightPx) + rowBottomPx(d);
        return new AppDrawerGridMetrics(columns, cellWidthPx, iconPx, rowHeightPx);
    }

    @NonNull
    @Override
    public String toString() {
        return "AppDrawerGridMetrics(columns=" + columns + ", cell=" + cellWidthPx
            + ", icon=" + iconPx + ", row=" + rowHeightPx + ")";
    }
}
