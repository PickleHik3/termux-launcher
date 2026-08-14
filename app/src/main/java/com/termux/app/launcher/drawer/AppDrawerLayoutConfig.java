package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Objects;

/** Sanitized immutable input for one in-place drawer layout application. */
public final class AppDrawerLayoutConfig {
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
        this.horizontalRows = horizontalRows >= 2 && horizontalRows <= 6 ? horizontalRows : 0;
        this.categoryColumns = categoryColumns >= 1 && categoryColumns <= 3 ? categoryColumns : 0;
    }

    @NonNull public static AppDrawerLayoutConfig defaults() {
        return new AppDrawerLayoutConfig(AppDrawerViewType.VERTICAL, 0, 0, 0, 0, 0);
    }

    /**
     * The view type is the only drawer layout preference left. Icon size and the column/row counts
     * are read as auto (0) on purpose: the settings entries are gone, every view resolves its own
     * geometry from the plane's width, and the category cards size their previews to fill — a stored
     * value from an older install would only pin an icon smaller than the card wants.
     *
     * <p>The explicit constructor still takes all six, so a caller (and the tests) can pin geometry;
     * nothing in the app does.
     */
    @NonNull public static AppDrawerLayoutConfig from(@NonNull TermuxAppSharedPreferences prefs) {
        return new AppDrawerLayoutConfig(AppDrawerViewType.fromPreference(
            prefs.getAppLauncherDrawerViewType()), 0, 0, 0, 0, 0);
    }

    private static boolean validIcon(int value) {
        return value == 0 || value == 36 || value == 40 || value == 44 || value == 48;
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
