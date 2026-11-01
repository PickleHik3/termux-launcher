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
 * allows really does produce cells of that size. The edge is Home's Margin, passed in by every
 * caller: a wider Margin leaves less wall for cells, so the caps fall with it, and a grid chosen
 * larger is held to them by the same clamp a smaller wall gets. Pure and tested: the wheels, the
 * Layout editor and the apply step all ask here.
 */
public final class WidgetGridCaps {

    /**
     * The least a cell is wide or tall, in dp. The platform's own floor for a one-cell widget is
     * 57 dp wide and 51 dp tall; 55 sits between them and lets a 386 dp phone wall hold six
     * columns under a rim of up to 7 dp (55.7 dp cells at 6 dp), which is where the range for
     * phones should end. A wider Margin trades columns for air: five at the seeded 12 dp.
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

    /**
     * The caps for a wall of this size in dp with a rim of {@code edgeDp} (Home's Margin) round
     * the grid; an unmeasured wall (zero or less) is unbounded. A negative rim counts as none.
     */
    @NonNull public static WidgetGridCaps forWall(float wallWidthDp, float wallHeightDp,
                                                  float edgeDp) {
        if (!(wallWidthDp > 0f) || !(wallHeightDp > 0f)) return UNBOUNDED;
        return new WidgetGridCaps(
            maxCells(wallWidthDp, edgeDp, TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
                TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS),
            maxCells(wallHeightDp, edgeDp, TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
                TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS));
    }

    /**
     * The caps for the screen in its portrait form, {@code widthDp} by {@code heightDp} either
     * way round, with a rim of {@code edgeDp}: one grid for both orientations (ADR 0020 §12), so
     * a turn of the screen, a keyboard or a sheet never changes the caps and never reflows.
     */
    @NonNull public static WidgetGridCaps forPortraitScreen(float widthDp, float heightDp,
                                                            float edgeDp) {
        return forWall(Math.min(widthDp, heightDp), Math.max(widthDp, heightDp), edgeDp);
    }

    /** The status bar's height where the system names none, dp. */
    public static final float FALLBACK_STATUS_BAR_DP = 24f;

    /**
     * The chrome the portrait wall gives up under the grid, dp: the dock's pinned row at the one
     * icon step with its air either side, and the page indicator's band under it.
     */
    public static float portraitDockChromeDp(float iconDp) {
        return Math.max(0f, iconDp) + 2f * com.termux.app.dock.DockLayoutPolicy.LONE_ROW_AIR_DP
            + com.termux.app.launcher.paging.PageTickStrip.bandPx(1f);
    }

    /**
     * The caps of the one grid (ADR 0020 §12), worked out once from the portrait wall: the
     * screen's long side less the portrait status bar and the dock at the icon step
     * ({@link #portraitDockChromeDp}), its short side across. The same in both orientations and
     * stable: it never follows the wall last seen, so a keyboard, a sheet or a turn changes
     * nothing, and no grid the caps offer has portrait cells under {@link #MIN_CELL_DP}.
     */
    @NonNull public static WidgetGridCaps forPortraitWall(float screenWidthDp, float screenHeightDp,
                                                          float statusBarDp, float iconDp,
                                                          float edgeDp) {
        float across = Math.min(screenWidthDp, screenHeightDp);
        float tall = Math.max(screenWidthDp, screenHeightDp) - Math.max(0f, statusBarDp)
            - portraitDockChromeDp(iconDp);
        return forWall(across, tall, edgeDp);
    }

    /** The caps for a wall measured in pixels, with a rim of {@code edgeDp} round the grid. */
    @NonNull public static WidgetGridCaps forWallPx(int widthPx, int heightPx, float density,
                                                    float edgeDp) {
        if (!(density > 0f)) return UNBOUNDED;
        return forWall(widthPx / density, heightPx / density, edgeDp);
    }

    /** n cells fit when {@code 2 * edge + (n - 1) * gap + n * cell <= length}. */
    static int maxCells(float lengthDp, float edgeDp, int floor, int ceiling) {
        float pitch = MIN_CELL_DP + WidgetGridMetrics.GAP_DP;
        float edge = edgeDp > 0f ? edgeDp : 0f;
        float room = lengthDp - 2f * edge + WidgetGridMetrics.GAP_DP;
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
