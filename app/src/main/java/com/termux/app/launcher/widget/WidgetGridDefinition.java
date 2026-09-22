package com.termux.app.launcher.widget;

/** Persisted widget-grid dimensions. A-3 exposes these read-only. */
public final class WidgetGridDefinition {
    /** 5x4 mirrors the stock home-screen grids widget providers design their cells against. */
    public static final int DEFAULT_ROWS = 5;
    public static final int DEFAULT_COLUMNS = 4;
    public static final int MIN_ROWS = 1;
    public static final int MAX_ROWS = 24;
    public static final int MIN_COLUMNS = 1;
    public static final int MAX_COLUMNS = 12;
    public static final WidgetGridDefinition DEFAULT =
        new WidgetGridDefinition(DEFAULT_ROWS, DEFAULT_COLUMNS);

    public final int rows;
    public final int columns;

    public WidgetGridDefinition(int rows, int columns) {
        if (rows < MIN_ROWS || rows > MAX_ROWS || columns < MIN_COLUMNS
            || columns > MAX_COLUMNS) {
            throw new IllegalArgumentException("Grid dimensions outside safety bounds");
        }
        this.rows = rows;
        this.columns = columns;
    }

    @Override public boolean equals(Object other) {
        return other instanceof WidgetGridDefinition
            && rows == ((WidgetGridDefinition) other).rows
            && columns == ((WidgetGridDefinition) other).columns;
    }

    @Override public int hashCode() { return 31 * rows + columns; }
}
