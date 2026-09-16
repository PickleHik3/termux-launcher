package com.termux.app.statusbar;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * Where the status bar stands, per edge: how thick it is, the frame it takes inside the container
 * it lives in, and how much of the screen's width the content beside it has to give up.
 *
 * <p>A bar along the top or the bottom is a row and costs the terminal height, which the content
 * column above and below it already accounts for; a bar down the left or the right is a column and
 * costs width, which is the same band on the same edge stack the apps rail and the extra keys
 * column stand in. The stack starts past the display cutout and every band on an edge stands
 * beside the ones outside it, so several things share one edge without any of them being drawn
 * over — {@link com.termux.app.place.EdgeStackPolicy#contentInsets} adds them up.
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
}
