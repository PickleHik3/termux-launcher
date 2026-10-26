package com.termux.app.launcher.widget;

/**
 * How an app icon sits in its Home cell: the icon at the dock's 48 dp when the cell has room,
 * smaller when it does not, with the label under it while the label still leaves a usable icon.
 * Pure arithmetic in pixels, so the view only applies it.
 */
public final class HomeAppIconMetrics {
    /** The icon's size when the cell has room: the dock's. */
    public static final float ICON_DP = 48f;
    /** Below this the label gives its line to the icon. */
    public static final float MIN_LABELLED_ICON_DP = 24f;
    /** The inset round the icon and label inside the cell's content. */
    public static final float INSET_DP = 4f;
    /** The gap between the icon and its label. */
    public static final float LABEL_GAP_DP = 4f;

    /** The icon's side in px. */
    public final int iconPx;
    /** Whether the label is shown under it. */
    public final boolean showLabel;

    private HomeAppIconMetrics(int iconPx, boolean showLabel) {
        this.iconPx = iconPx;
        this.showLabel = showLabel;
    }

    /**
     * The layout for a cell content of {@code widthPx}×{@code heightPx} (already inside the
     * cell's gutter) with a label line {@code labelPx} tall.
     */
    public static HomeAppIconMetrics of(int widthPx, int heightPx, int labelPx, float density) {
        return of(widthPx, heightPx, labelPx, density, 1f);
    }

    /**
     * The same, with the icon wanted at {@code iconScale} times 48 dp (Settings › Home › Icon
     * size). The label is still measured, never assumed, so a large font scale shrinks the icon
     * or gives up the label rather than clipping it.
     */
    public static HomeAppIconMetrics of(int widthPx, int heightPx, int labelPx, float density,
                                        float iconScale) {
        int inset = Math.round(INSET_DP * density);
        int wanted = Math.round(ICON_DP * iconScale * density);
        int across = Math.max(0, widthPx - 2 * inset);
        int labelled = heightPx - 2 * inset - labelPx - Math.round(LABEL_GAP_DP * density);
        int withLabel = Math.min(wanted, Math.min(across, labelled));
        if (withLabel >= Math.round(MIN_LABELLED_ICON_DP * density)) {
            return new HomeAppIconMetrics(withLabel, true);
        }
        int bare = Math.min(wanted, Math.min(across, Math.max(0, heightPx - 2 * inset)));
        return new HomeAppIconMetrics(Math.max(1, bare), false);
    }

    /**
     * How many lines the label may take: two when {@code twoLinesWanted} and a two-line label
     * ({@code lineHeightPx} each) still leaves the icon a labelled size, else one. The label is
     * then measured at that many lines and {@link #of} decides with its real height, so a short
     * name keeps one line's height either way.
     */
    public static int labelLines(int widthPx, int heightPx, int lineHeightPx, float density,
                                 float iconScale, boolean twoLinesWanted) {
        if (!twoLinesWanted) return 1;
        return of(widthPx, heightPx, 2 * lineHeightPx, density, iconScale).showLabel ? 2 : 1;
    }
}
