package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Settings › Home › Folders › Folder density: how tightly an open folder on Home lays its apps
 * out. Default is the popup as it was (four across, 76 × 84 dp cells); Compact fits five across
 * in smaller cells, Comfortable three across in larger ones. On a narrow screen the popup takes
 * as many columns as fit, never more than the density's own. Dock folders keep their own popup.
 */
public enum HomeFolderDensity {
    COMPACT("compact", 5, 64, 76),
    DEFAULT("default", 4, 76, 84),
    COMFORTABLE("comfortable", 3, 92, 104);

    /** The narrowest the popup ever is, in dp, however few apps it holds. */
    static final int MIN_POPUP_WIDTH_DP = 176;
    /** The popup's own edge around its cells, in dp. */
    static final int POPUP_EDGE_DP = 6;

    @NonNull public final String value;
    /** The most apps side by side. */
    public final int columns;
    public final int cellWidthDp;
    public final int cellHeightDp;

    HomeFolderDensity(@NonNull String value, int columns, int cellWidthDp, int cellHeightDp) {
        this.value = value;
        this.columns = columns;
        this.cellWidthDp = cellWidthDp;
        this.cellHeightDp = cellHeightDp;
    }

    @NonNull
    public static HomeFolderDensity fromValue(@Nullable String value) {
        for (HomeFolderDensity density : values()) if (density.value.equals(value)) return density;
        return DEFAULT;
    }

    /**
     * Columns for {@code apps} apps in a popup that may be {@code availableWidthDp} wide: no more
     * than the density's, than the apps, or than fit; at least one. A width of 0 or less is not
     * known yet and does not limit.
     */
    public int columnsFor(int apps, float availableWidthDp) {
        int wanted = Math.max(1, Math.min(columns, apps));
        if (availableWidthDp <= 0f) return wanted;
        int fit = (int) Math.floor((availableWidthDp - POPUP_EDGE_DP) / cellWidthDp);
        return Math.max(1, Math.min(wanted, fit));
    }

    /** The popup's width in dp for {@code columns} columns. */
    public int popupWidthDp(int columns) {
        return Math.max(MIN_POPUP_WIDTH_DP, columns * cellWidthDp + POPUP_EDGE_DP);
    }
}
