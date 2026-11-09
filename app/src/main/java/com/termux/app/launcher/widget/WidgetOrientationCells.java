package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

/**
 * The px a widget's cells give it upright and turned, worked out before the screen ever turns
 * (ADR 0020 §12): one grid serves both orientations, so a provider is told both sizes at once,
 * as Launcher3's {@code WidgetSizes} does.
 *
 * <p>Portrait wall: the screen's short side across, its long side less the status bar and the
 * dock at the icon step ({@link WidgetGridCaps#portraitDockChromeDp}). Landscape wall on a phone
 * (smallest width under {@link #SIDE_BAR_MAX_SMALLEST_WIDTH_DP}): the long side less the side-bar
 * dock ({@link #sideBarDp}), by the short side less the status bar. A tablet keeps its dock along
 * the bottom in landscape too. Each cell is {@code (wall - 2 * edge - (n - 1) * gap) / n}, and a
 * span is {@code span * cell + (span - 1) * gap}.
 */
public final class WidgetOrientationCells {
    /** Phones (smallest width under this) put the dock on the long edge in landscape. */
    public static final int SIDE_BAR_MAX_SMALLEST_WIDTH_DP = 600;

    /** What the walls are worked out from; all in dp but the density. */
    public static final class Geometry {
        public final float screenWidthDp, screenHeightDp, statusBarDp, iconDp, edgeDp;
        public final int columns, rows;
        public final float density;

        public Geometry(float screenWidthDp, float screenHeightDp, float statusBarDp,
                        float iconDp, float edgeDp, int columns, int rows, float density) {
            this.screenWidthDp = screenWidthDp;
            this.screenHeightDp = screenHeightDp;
            this.statusBarDp = statusBarDp;
            this.iconDp = iconDp;
            this.edgeDp = edgeDp;
            this.columns = columns;
            this.rows = rows;
            this.density = density;
        }
    }

    private WidgetOrientationCells() {}

    /** Whether a screen this size lays its dock as a side bar in landscape: a phone. */
    public static boolean usesSideBar(float screenWidthDp, float screenHeightDp) {
        return Math.min(screenWidthDp, screenHeightDp) < SIDE_BAR_MAX_SMALLEST_WIDTH_DP;
    }

    /**
     * The side-bar dock's width in landscape, dp: the icon at the one step with its air either
     * side, and the page indicator's band beside it.
     */
    public static float sideBarDp(float iconDp) {
        return WidgetGridCaps.portraitDockChromeDp(iconDp);
    }

    /** {portrait width, portrait height, landscape width, landscape height} of the wall, dp. */
    @NonNull public static float[] wallsDp(@NonNull Geometry g) {
        float shortSide = Math.min(g.screenWidthDp, g.screenHeightDp);
        float longSide = Math.max(g.screenWidthDp, g.screenHeightDp);
        float status = Math.max(0f, g.statusBarDp);
        float dock = WidgetGridCaps.portraitDockChromeDp(g.iconDp);
        float portraitH = longSide - status - dock;
        float landscapeW, landscapeH;
        if (usesSideBar(g.screenWidthDp, g.screenHeightDp)) {
            landscapeW = longSide - sideBarDp(g.iconDp);
            landscapeH = shortSide - status;
        } else {
            landscapeW = longSide;
            landscapeH = shortSide - status - dock;
        }
        return new float[] {shortSide, portraitH, landscapeW, landscapeH};
    }

    /** A span of {@code span} cells out of {@code count} across a wall {@code wallDp} long, px. */
    public static int spanPx(float wallDp, float edgeDp, int count, int span, float density) {
        int n = Math.max(1, count);
        int s = Math.max(1, Math.min(span, n));
        float gap = WidgetGridMetrics.GAP_DP;
        float cell = (wallDp - 2f * Math.max(0f, edgeDp) - (n - 1) * gap) / n;
        float dp = s * cell + (s - 1) * gap;
        return Math.max(1, Math.round(dp * density));
    }

    /**
     * {portrait width, portrait height, landscape width, landscape height} in px for a widget
     * spanning {@code columnSpan} × {@code rowSpan} cells.
     */
    @NonNull public static int[] spanPxBoth(@NonNull Geometry g, int columnSpan, int rowSpan) {
        float[] walls = wallsDp(g);
        return new int[] {
            spanPx(walls[0], g.edgeDp, g.columns, columnSpan, g.density),
            spanPx(walls[1], g.edgeDp, g.rows, rowSpan, g.density),
            spanPx(walls[2], g.edgeDp, g.columns, columnSpan, g.density),
            spanPx(walls[3], g.edgeDp, g.rows, rowSpan, g.density),
        };
    }

    /**
     * Both orientations' px for the size options, with the one on screen as measured: the
     * provider's own content ({@code measuredWidthPx} x {@code measuredHeightPx}) is exact for the
     * orientation the screen stands in, and the other is the worked-out span less the same inset
     * (the cell's gutter and the host view's padding) the measured one shows.
     */
    @NonNull public static int[] bothFromMeasured(@NonNull Geometry g, int columnSpan, int rowSpan,
                                                  int measuredWidthPx, int measuredHeightPx,
                                                  boolean landscape) {
        int[] both = spanPxBoth(g, columnSpan, rowSpan);
        int here = landscape ? 2 : 0, there = landscape ? 0 : 2;
        int insetW = Math.max(0, both[here] - measuredWidthPx);
        int insetH = Math.max(0, both[here + 1] - measuredHeightPx);
        int[] out = new int[4];
        out[here] = measuredWidthPx;
        out[here + 1] = measuredHeightPx;
        out[there] = Math.max(1, both[there] - insetW);
        out[there + 1] = Math.max(1, both[there + 1] - insetH);
        return out;
    }
}
