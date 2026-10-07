package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * How many columns and rows a wall can hold: as many as keep every cell at least
 * {@link #MIN_CELL_DP} either way, which is Android's own floor for a widget's smallest useful
 * size. The settings range stays the absolute floor and ceiling; this only ever tightens the
 * ceiling, and never below the floor, so the smallest wall still gets a grid.
 *
 * <p>The cell arithmetic is {@link WidgetGridMetrics}'s own (same edge, same gap), so a count this
 * allows really does produce cells of that size. Pure and tested: the wheels, the Layout editor
 * and the apply step all ask here.
 */
public final class WidgetGridCaps {

    /**
     * The least a cell is wide or tall, in dp. The platform's own floor for a one-cell widget is
     * 57 dp wide and 51 dp tall; 55 sits between them and lets a 386 dp phone wall hold six
     * columns (55.7 dp cells), which is where the range for phones should end.
     */
    public static final float MIN_CELL_DP = 55f;

    private static final WidgetGridCaps UNBOUNDED = new WidgetGridCaps(
        TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS, TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS);

    public final int maxColumns;
    public final int maxRows;

    private WidgetGridCaps(int maxColumns, int maxRows) {
        this.maxColumns = maxColumns;
        this.maxRows = maxRows;
    }

    /** No wall known yet: the settings range alone. */
    @NonNull public static WidgetGridCaps unbounded() { return UNBOUNDED; }

    /** The caps for a wall of this size in dp; an unmeasured wall (zero or less) is unbounded. */
    @NonNull public static WidgetGridCaps forWall(float wallWidthDp, float wallHeightDp) {
        if (!(wallWidthDp > 0f) || !(wallHeightDp > 0f)) return UNBOUNDED;
        return new WidgetGridCaps(
            maxCells(wallWidthDp, TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
                TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS),
            maxCells(wallHeightDp, TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
                TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS));
    }

    /** The caps for a wall measured in pixels. */
    @NonNull public static WidgetGridCaps forWallPx(int widthPx, int heightPx, float density) {
        if (!(density > 0f)) return UNBOUNDED;
        return forWall(widthPx / density, heightPx / density);
    }

    /** n cells fit when {@code 2 * edge + (n - 1) * gap + n * cell <= length}. */
    static int maxCells(float lengthDp, int floor, int ceiling) {
        float pitch = MIN_CELL_DP + WidgetGridMetrics.GAP_DP;
        float room = lengthDp - 2f * WidgetGridMetrics.EDGE_DP + WidgetGridMetrics.GAP_DP;
        int fitting = (int) Math.floor(room / pitch);
        return Math.max(floor, Math.min(ceiling, fitting));
    }

    public int clampColumns(int columns) {
        return Math.max(TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
            Math.min(maxColumns, columns));
    }

    public int clampRows(int rows) {
        return Math.max(TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS, Math.min(maxRows, rows));
    }

    @Override public boolean equals(Object other) {
        return other instanceof WidgetGridCaps
            && maxColumns == ((WidgetGridCaps) other).maxColumns
            && maxRows == ((WidgetGridCaps) other).maxRows;
    }

    @Override public int hashCode() { return 31 * maxColumns + maxRows; }
}
