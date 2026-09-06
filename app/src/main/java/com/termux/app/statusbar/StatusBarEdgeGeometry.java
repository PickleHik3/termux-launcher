package com.termux.app.statusbar;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * Where the status bar stands, per edge: how thick it is, the frame it takes inside the container
 * it lives in, and how much of the screen's width the content beside it has to give up.
 *
 * <p>A bar along the top or the bottom is a row and costs the terminal height, which the content
 * column above and below it already accounts for; a bar down the left or the right is a column and
 * costs width, which is the same content-inset seam the apps rail and the extra keys column use.
 * The column starts past whatever already holds that edge — the display cutout, or the rail — so
 * three things can share one edge without any of them being drawn over.
 *
 * <p>Pure: no views, no resources, only densities and pixels.
 */
public final class StatusBarEdgeGeometry {

    /** A row's thickness is its height; a column's is its width. Both in dp. */
    public static final float ROW_COMPACT_DOCKED_DP = 32f;
    public static final float ROW_COMPACT_CAPSULE_DP = 30f;
    public static final float ROW_EXPANDED_DOCKED_DP = 96f;
    public static final float ROW_EXPANDED_CAPSULE_DP = 100f;

    /**
     * The column is a little wider than the row is tall: it carries the same chips turned on
     * their side, and a chip needs room for its label's first glyph rather than only its height.
     */
    public static final float COLUMN_COMPACT_DOCKED_DP = 36f;
    public static final float COLUMN_COMPACT_CAPSULE_DP = 34f;
    /** Open, the column is wide enough for the stacked clock's two lines of digits. */
    public static final float COLUMN_EXPANDED_DOCKED_DP = 76f;
    public static final float COLUMN_EXPANDED_CAPSULE_DP = 80f;

    /** The bar's frame inside its container, in that container's own pixels. */
    public static final class Frame {
        public final int left;
        public final int top;
        public final int right;
        public final int bottom;

        public Frame(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int width() { return Math.max(0, right - left); }

        public int height() { return Math.max(0, bottom - top); }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Frame)) return false;
            Frame that = (Frame) other;
            return left == that.left && top == that.top && right == that.right
                && bottom == that.bottom;
        }

        @Override public int hashCode() {
            return ((left * 31 + top) * 31 + right) * 31 + bottom;
        }

        @NonNull @Override public String toString() {
            return "Frame{" + left + "," + top + "," + right + "," + bottom + "}";
        }
    }

    private StatusBarEdgeGeometry() {}

    /** The bar stands in a column rather than a row. */
    public static boolean isVertical(@NonNull Edge edge) {
        return StatusBarGesturePolicy.isVertical(edge);
    }

    /** Whether the bar holds the left or the right edge of the screen. */
    public static boolean holdsSide(@NonNull Edge edge, boolean right) {
        return edge == (right ? Edge.RIGHT : Edge.LEFT);
    }

    public static float thicknessDp(@NonNull Edge edge, boolean capsule, boolean compact) {
        if (isVertical(edge)) {
            if (compact) return capsule ? COLUMN_COMPACT_CAPSULE_DP : COLUMN_COMPACT_DOCKED_DP;
            return capsule ? COLUMN_EXPANDED_CAPSULE_DP : COLUMN_EXPANDED_DOCKED_DP;
        }
        if (compact) return capsule ? ROW_COMPACT_CAPSULE_DP : ROW_COMPACT_DOCKED_DP;
        return capsule ? ROW_EXPANDED_CAPSULE_DP : ROW_EXPANDED_DOCKED_DP;
    }

    public static int thicknessPx(@NonNull Edge edge, boolean capsule, boolean compact,
                                  float density) {
        return Math.round(thicknessDp(edge, capsule, compact) * density);
    }

    /**
     * The bar's frame. A row spans the container's width and takes {@code thicknessPx} off the
     * edge it stands on; a column spans the height and takes its width starting {@code edgeInsetPx}
     * in from its edge, which is what keeps it past the display cutout and past a rail already
     * holding the same side.
     */
    @NonNull
    public static Frame frame(@NonNull Edge edge, int containerWidthPx, int containerHeightPx,
                              int thicknessPx, int edgeInsetPx) {
        int width = Math.max(0, containerWidthPx);
        int height = Math.max(0, containerHeightPx);
        int thickness = Math.max(0, thicknessPx);
        int inset = Math.max(0, edgeInsetPx);
        switch (edge) {
            case BOTTOM:
                return new Frame(0, Math.max(0, height - thickness), width, height);
            case LEFT:
                return new Frame(inset, 0, Math.min(width, inset + thickness), height);
            case RIGHT:
                return new Frame(Math.max(0, width - inset - thickness), 0,
                    Math.max(0, width - inset), height);
            case TOP:
            default:
                return new Frame(0, 0, width, Math.min(height, thickness));
        }
    }

    /**
     * How far in from one side the bar reaches, which is what the content there is inset by. A row
     * costs no width at all, and a column costs nothing on the side it does not stand on.
     */
    public static int contentInsetPx(@NonNull Edge edge, boolean right, int thicknessPx,
                                     int edgeInsetPx) {
        if (!holdsSide(edge, right)) return 0;
        return Math.max(0, edgeInsetPx) + Math.max(0, thicknessPx);
    }

    /**
     * Whether a rail or extra-keys column on this side shares the bar's edge. They then stand in
     * one column — one blended surface — with the bar nearest the top.
     */
    public static boolean sharesColumn(@NonNull Edge edge, boolean otherOnRight) {
        return holdsSide(edge, otherOnRight);
    }

    /**
     * How far down its column a rail or extra-keys column starts. The bar has the top of a column
     * they share, so whatever else stands there begins under the bar's content rather than beside
     * it; on any other edge nothing moves.
     */
    public static int columnTopOffsetPx(@NonNull Edge edge, boolean otherOnRight,
                                        int barContentLengthPx) {
        return sharesColumn(edge, otherOnRight) ? Math.max(0, barContentLengthPx) : 0;
    }

    /**
     * The bar's own thickness laid across the screen the other way: a row's frame is measured
     * from the top, a bottom row's from the bottom, and a column's content is always anchored at
     * the top of its column. This is the offset of a child of length {@code childLengthPx} that
     * rides the bar's inner edge — the one facing the terminal — while the bar grows.
     */
    public static int innerEdgeOffsetPx(@NonNull Edge edge, int barLengthPx, int childLengthPx,
                                        int restingOffsetPx) {
        int span = Math.max(0, barLengthPx);
        int child = Math.max(0, childLengthPx);
        int resting = Math.max(0, restingOffsetPx);
        // A bar that opens away from its edge keeps its content on the moving inner edge; one that
        // opens back towards the origin mirrors that offset about the bar's own length.
        if (StatusBarGesturePolicy.expandSign(edge) > 0f) return resting;
        return Math.max(0, span - child - resting);
    }
}
