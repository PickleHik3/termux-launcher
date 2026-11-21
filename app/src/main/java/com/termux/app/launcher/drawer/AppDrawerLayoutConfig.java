package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Objects;

/** Sanitized immutable input for one in-place drawer layout application. */
public final class AppDrawerLayoutConfig {
    /** Rows Settings offers; the metrics' own clamp is wider and only guards a stray caller. */
    public static final int MIN_ROWS = 3;
    public static final int MAX_ROWS = 6;

    @NonNull public final AppDrawerViewType viewType;
    public final int iconSizeDp;
    public final int verticalColumns;
    public final int horizontalColumns;
    public final int horizontalRows;
    public final int categoryColumns;

    public AppDrawerLayoutConfig(@NonNull AppDrawerViewType viewType, int iconSizeDp,
                                 int verticalColumns, int horizontalColumns, int horizontalRows,
                                 int categoryColumns) {
        this.viewType = viewType;
        this.iconSizeDp = validIcon(iconSizeDp) ? iconSizeDp : 0;
        this.verticalColumns = validAppColumns(verticalColumns) ? verticalColumns : 0;
        this.horizontalColumns = validAppColumns(horizontalColumns) ? horizontalColumns : 0;
        this.horizontalRows = horizontalRows >= MIN_ROWS && horizontalRows <= MAX_ROWS ? horizontalRows : 0;
        this.categoryColumns = categoryColumns >= 1 && categoryColumns <= 3 ? categoryColumns : 0;
    }

    @NonNull public static AppDrawerLayoutConfig defaults() {
        return new AppDrawerLayoutConfig(AppDrawerViewType.VERTICAL, 0, 0, 0, 0, 0);
    }

    /**
     * The view type plus the drawer's icon size, column count and row count, each read as Auto (0)
     * until the user picks a step. Columns feed the three grids that have columns (Vertical,
     * Horizontal, Default's Apps page) and rows the Horizontal one; Categories sizes its cards
     * itself, so {@code categoryColumns} stays Auto.
     */
    @NonNull public static AppDrawerLayoutConfig from(@NonNull TermuxAppSharedPreferences prefs) {
        int columns = parseStep(prefs.getAppLauncherDrawerColumns());
        return new AppDrawerLayoutConfig(AppDrawerViewType.fromPreference(
            prefs.getAppLauncherDrawerViewType()),
            parseStep(prefs.getAppLauncherDrawerIconSize()), columns, columns,
            parseStep(prefs.getAppLauncherDrawerRows()), 0);
    }

    /** "auto", garbage and null read as 0; a number reads as itself (the constructor validates). */
    static int parseStep(@Nullable String value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean validIcon(int value) {
        return value == 0 || value == 48 || value == 54 || value == 60;
    }

    private static boolean validAppColumns(int value) {
        return value == 0 || value >= 4 && value <= 6;
    }

    @Override public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof AppDrawerLayoutConfig)) return false;
        AppDrawerLayoutConfig other = (AppDrawerLayoutConfig) object;
        return iconSizeDp == other.iconSizeDp && verticalColumns == other.verticalColumns
            && horizontalColumns == other.horizontalColumns && horizontalRows == other.horizontalRows
            && categoryColumns == other.categoryColumns && viewType == other.viewType;
    }

    @Override public int hashCode() {
        return Objects.hash(viewType, iconSizeDp, verticalColumns, horizontalColumns,
            horizontalRows, categoryColumns);
    }
}
