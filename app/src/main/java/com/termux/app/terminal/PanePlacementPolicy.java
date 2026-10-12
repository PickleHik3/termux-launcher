package com.termux.app.terminal;

import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Where a pane asked for through the local API goes, and how big it is: the arithmetic behind
 * {@code launcherctl pane open --direction right --size 0.4}, {@code --columns 40} and
 * {@code launcherctl pane resize}, and the cell geometry the pane records report back.
 *
 * <p>Pure functions on numbers, so the controller only applies the answer. A share is the part of
 * a split one side holds along the split's axis; every share this hands out stays within
 * {@link #MIN_SHARE}..{@link #MAX_SHARE}, so neither pane of a split can be squeezed to nothing.
 * Sides reuse {@link DwindleTilingPolicy}'s constants, so a placement and a drop speak the same
 * language.
 */
public final class PanePlacementPolicy {

    /** The smallest share either side of a split may be given, and the largest. */
    public static final float MIN_SHARE = 0.1f;
    public static final float MAX_SHARE = 0.9f;
    /** The share a new pane gets when nobody said: an even split, as every split has always been. */
    public static final float DEFAULT_SHARE = 0.5f;

    /** No direction was asked for: the layout's own rule decides. */
    public static final int SIDE_UNSET = -1;
    /** A direction was given but is not one of right, left, down, up. */
    public static final int SIDE_INVALID = -2;

    private PanePlacementPolicy() {}

    /** The side a direction name stands for, {@link #SIDE_UNSET} for none, {@link #SIDE_INVALID} for junk. */
    public static int sideFor(@Nullable String direction) {
        if (direction == null) return SIDE_UNSET;
        switch (direction.trim().toLowerCase(Locale.ROOT)) {
            case "": return SIDE_UNSET;
            case "right": return DwindleTilingPolicy.SIDE_RIGHT;
            case "left": return DwindleTilingPolicy.SIDE_LEFT;
            case "down": return DwindleTilingPolicy.SIDE_BOTTOM;
            case "up": return DwindleTilingPolicy.SIDE_TOP;
            default: return SIDE_INVALID;
        }
    }

    /** The direction name for a side, the inverse of {@link #sideFor}. */
    @NonNull
    public static String directionName(int side) {
        switch (side) {
            case DwindleTilingPolicy.SIDE_LEFT: return "left";
            case DwindleTilingPolicy.SIDE_TOP: return "up";
            case DwindleTilingPolicy.SIDE_BOTTOM: return "down";
            default: return "right";
        }
    }

    /** Whether {@code size} is a share a caller may ask for: finite and within the bounds. */
    public static boolean isValidShare(double size) {
        return !Double.isNaN(size) && size >= MIN_SHARE - 1e-6 && size <= MAX_SHARE + 1e-6;
    }

    /** {@code share} pulled into {@link #MIN_SHARE}..{@link #MAX_SHARE}. */
    public static float clampShare(float share) {
        if (Float.isNaN(share)) return DEFAULT_SHARE;
        return Math.max(MIN_SHARE, Math.min(MAX_SHARE, share));
    }

    /**
     * The share {@code cells} columns (or rows) make of an extent {@code extentCells} long,
     * clamped so both sides keep at least {@link #MIN_SHARE}. An unknown extent (a pane that was
     * never measured) answers the even split rather than guessing.
     */
    public static float shareForCells(int cells, int extentCells) {
        if (extentCells <= 0 || cells <= 0) return DEFAULT_SHARE;
        return clampShare(cells / (float) extentCells);
    }

    /**
     * The side a new pane takes when the caller gave {@code requested} (maybe {@link #SIDE_UNSET})
     * and a cell count: columns alone mean beside it (right), rows alone below it (down), and
     * with neither, or both, the layout's {@code fallback} decides.
     */
    public static int resolveSide(int requested, boolean hasColumns, boolean hasRows, int fallback) {
        if (requested >= 0) return requested;
        if (hasColumns && !hasRows) return DwindleTilingPolicy.SIDE_RIGHT;
        if (hasRows && !hasColumns) return DwindleTilingPolicy.SIDE_BOTTOM;
        return fallback;
    }

    /** How a split of the anchor pane is made: its axis, which child the new pane is, and its share. */
    public static final class Placement {
        /** {@link LinearLayout#HORIZONTAL} (side by side) or {@link LinearLayout#VERTICAL} (stacked). */
        public final int orientation;
        /** True when the new pane is the split's first child: left of or above the anchor. */
        public final boolean newFirst;
        /** The new pane's share of the anchor's old extent along {@link #orientation}. */
        public final float newShare;

        Placement(int orientation, boolean newFirst, float newShare) {
            this.orientation = orientation;
            this.newFirst = newFirst;
            this.newShare = newShare;
        }

        /** The first child's weight when the split's two weights add up to {@code total}. */
        public float weightA(float total) {
            return total * (newFirst ? newShare : 1f - newShare);
        }

        /** The second child's weight when the split's two weights add up to {@code total}. */
        public float weightB(float total) {
            return total - weightA(total);
        }
    }

    /**
     * The split that puts a new pane on {@code side} of an anchor {@code anchorColumns} by
     * {@code anchorRows} cells. {@code size} (NaN when not given) wins; otherwise a cell count along
     * the split's axis — {@code columns} for left/right, {@code rows} for up/down, 0 when not
     * given — is turned into a share of the anchor; otherwise the split is even. A cell count on
     * the other axis says nothing about this split and is ignored.
     */
    @NonNull
    public static Placement place(int side, float size, int columns, int rows,
                                  int anchorColumns, int anchorRows) {
        int orientation = DwindleTilingPolicy.orientationForSide(side);
        boolean newFirst = DwindleTilingPolicy.droppedFirst(side);
        float share;
        if (!Float.isNaN(size)) {
            share = clampShare(size);
        } else if (orientation == LinearLayout.HORIZONTAL && columns > 0) {
            share = shareForCells(columns, anchorColumns);
        } else if (orientation == LinearLayout.VERTICAL && rows > 0) {
            share = shareForCells(rows, anchorRows);
        } else {
            share = DEFAULT_SHARE;
        }
        return new Placement(orientation, newFirst, share);
    }

    /**
     * The share a pane resized through the API is given of the split it sits in: {@code size}
     * when given (NaN otherwise), else {@code cells} of the split's {@code extentCells}.
     */
    public static float resizeShare(float size, int cells, int extentCells) {
        if (!Float.isNaN(size)) return clampShare(size);
        return shareForCells(cells, extentCells);
    }

    /**
     * The first child's weight that gives the side holding the pane {@code share} of a split whose
     * weights add up to {@code total}.
     */
    public static float weightAForShare(float total, float share, boolean paneIsFirst) {
        float clamped = clampShare(share);
        return total * (paneIsFirst ? clamped : 1f - clamped);
    }

    /**
     * One child's part of {@code parent} (left, top, right, bottom, as fractions of the window's
     * pane area) under a split of {@code orientation} weighted {@code weightA}:{@code weightB}.
     */
    @NonNull
    public static float[] childFraction(@NonNull float[] parent, int orientation,
                                        float weightA, float weightB, boolean first) {
        float total = weightA + weightB;
        float shareA = total <= 0f ? 0.5f : weightA / total;
        float[] out = parent.clone();
        if (orientation == LinearLayout.HORIZONTAL) {
            float cut = parent[0] + (parent[2] - parent[0]) * shareA;
            if (first) out[2] = cut; else out[0] = cut;
        } else {
            float cut = parent[1] + (parent[3] - parent[1]) * shareA;
            if (first) out[3] = cut; else out[1] = cut;
        }
        return out;
    }

    /**
     * A pane's place in cells — {left, top, columns, rows} — from its fractional bounds in a pane
     * area {@code areaColumns} by {@code areaRows} cells. Each edge is rounded on its own, so two
     * panes sharing a divider report the same cell for it and the columns of a row of panes add
     * up to the area's. Null when the area has not been measured.
     */
    @Nullable
    public static int[] cellGeometry(@NonNull float[] fraction, int areaColumns, int areaRows) {
        if (areaColumns <= 0 || areaRows <= 0) return null;
        int left = Math.round(clamp01(fraction[0]) * areaColumns);
        int top = Math.round(clamp01(fraction[1]) * areaRows);
        int right = Math.round(clamp01(fraction[2]) * areaColumns);
        int bottom = Math.round(clamp01(fraction[3]) * areaRows);
        return new int[]{left, top, Math.max(0, right - left), Math.max(0, bottom - top)};
    }

    /** How many whole cells of {@code cellPixels} fit in {@code pixels}; 0 when either is unknown. */
    public static int cellsIn(int pixels, int cellPixels) {
        return pixels <= 0 || cellPixels <= 0 ? 0 : pixels / cellPixels;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
