package com.termux.app.wayland;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.DwindleTilingPolicy;

/**
 * Where a carried Linux window lands (spec #384, "Carry"): the one drop decision. A drop in the
 * strip along a window's left or right edge is the gap between that window and its neighbour: the
 * carried window becomes its own window there and the row reorders. Any other drop returns it.
 * The split drops (#387) and the Float drop (#388) extend {@link Kind} and {@link #decide}; the
 * rest of the carry only asks this. Pure.
 *
 * <p>A gap is numbered by the window after it: gap {@code g} stands between row places
 * {@code g - 1} and {@code g}, so a row of {@code n} windows has gaps {@code 0..n}.
 */
public final class LinuxCarryPolicy {

    /** How wide the strip along each side of the front window is, in dp: a fingertip and a half. */
    public static final float EDGE_STRIP_DP = 56f;

    /** How far a second finger swipes sideways to page the row during a carry, in dp. */
    public static final float SECOND_SWIPE_DP = 72f;

    private LinuxCarryPolicy() {}

    /** What a drop does. */
    public enum Kind {
        /** The window goes back to where it was lifted from. */
        RETURN,
        /** The window becomes its own window at {@link Drop#to}, between two windows. */
        BETWEEN,
        /** The window becomes a pane beside the pane under the finger, on {@link Drop#side}. */
        SPLIT
    }

    /** The carried window is a pane of a split, not a row window: there is no row place to leave. */
    public static final int FROM_PANE = -2;

    /** A drop's decision. */
    public static final class Drop {
        @NonNull public final Kind kind;
        /** For {@link Kind#BETWEEN}: the row place the window takes once lifted out; else -1. */
        public final int to;
        /** For {@link Kind#SPLIT}: the half of the pane the window takes, a {@code DwindleTilingPolicy.SIDE_*}; else -1. */
        public final int side;

        Drop(@NonNull Kind kind, int to, int side) {
            this.kind = kind;
            this.to = to;
            this.side = side;
        }
    }

    private static final Drop RETURN = new Drop(Kind.RETURN, -1, -1);

    /**
     * The side strip the finger at {@code x} is in over the window spanning {@code left..right}:
     * -1 the left one, +1 the right one, 0 none. A window narrower than two strips has no middle:
     * the nearer edge wins.
     */
    public static int edgeAt(float x, float left, float right, float stripPx) {
        if (!(right > left) || x < left || x > right) return 0;
        boolean inLeft = x - left <= stripPx;
        boolean inRight = right - x <= stripPx;
        if (inLeft && inRight) return x - left <= right - x ? -1 : 1;
        return inLeft ? -1 : inRight ? 1 : 0;
    }

    /** The gap an {@code edge} strip of the window at {@code front} stands on. */
    public static int gapOf(int edge, int front) {
        return edge > 0 ? front + 1 : front;
    }

    /**
     * The row place the carried window takes when lifted from {@code carried} and set down in
     * {@code gap}; -1 where that is where it already stands or the gap is not in the row.
     */
    public static int placeForGap(int gap, int carried, int count) {
        if (carried == FROM_PANE) return gap < 0 || gap > count ? -1 : gap;
        if (gap < 0 || gap > count || carried < 0 || carried >= count) return -1;
        int to = gap > carried ? gap - 1 : gap;
        return to == carried ? -1 : to;
    }

    /**
     * Decides a drop of the window lifted from row place {@code carried} when the finger is at
     * {@code x} over the front page spanning {@code left..right}. {@code front} is the row place of
     * the window in front, or -1 when the front page is not a row window (Home, the display) or
     * the wall is still moving: then there is nothing to drop between.
     */
    @NonNull
    public static Drop decide(float x, float left, float right, float stripPx, int front,
                              int count, int carried) {
        return decide(x, 0f, left, right, stripPx, front, count, carried, null);
    }

    /**
     * {@link #decide(float, float, float, float, int, int, int)} with split drops: past the edge
     * strips a finger over a pane ({@code pane}: left, top, right, bottom of the pane under it,
     * null when none) splits it on the half the finger is over. {@code carried} may be
     * {@link #FROM_PANE}. A pane splits only while a window is in front ({@code front >= 0}).
     */
    @NonNull
    public static Drop decide(float x, float y, float left, float right, float stripPx, int front,
                              int count, int carried, @Nullable float[] pane) {
        if (front < 0) return RETURN;
        int edge = front >= count ? 0 : edgeAt(x, left, right, stripPx);
        if (edge != 0) {
            int to = placeForGap(gapOf(edge, front), carried, count);
            return to < 0 ? RETURN : new Drop(Kind.BETWEEN, to, -1);
        }
        if (pane == null || !contains(pane, x, y)) return RETURN;
        return new Drop(Kind.SPLIT, -1, sideAt(x, y, pane));
    }

    private static boolean contains(@NonNull float[] pane, float x, float y) {
        return x >= pane[0] && x <= pane[2] && y >= pane[1] && y <= pane[3];
    }

    /**
     * The half of {@code pane} (left, top, right, bottom) the finger at ({@code x}, {@code y}) is
     * over: the axis follows the pane's longer side as every pane drop does
     * ({@link DwindleTilingPolicy#splitOrientationFor}), the half the side of its midline.
     */
    public static int sideAt(float x, float y, @NonNull float[] pane) {
        float w = pane[2] - pane[0];
        float h = pane[3] - pane[1];
        if (DwindleTilingPolicy.splitOrientationFor(w, h) == android.widget.LinearLayout.HORIZONTAL) {
            return x < pane[0] + w / 2f ? DwindleTilingPolicy.SIDE_LEFT : DwindleTilingPolicy.SIDE_RIGHT;
        }
        return y < pane[1] + h / 2f ? DwindleTilingPolicy.SIDE_TOP : DwindleTilingPolicy.SIDE_BOTTOM;
    }

    /** The half of {@code pane} that a drop on {@code side} takes, into {@code out} (l, t, r, b). */
    @NonNull
    public static float[] halfOf(@NonNull float[] pane, int side, @NonNull float[] out) {
        System.arraycopy(pane, 0, out, 0, 4);
        switch (side) {
            case DwindleTilingPolicy.SIDE_LEFT: out[2] = (pane[0] + pane[2]) / 2f; break;
            case DwindleTilingPolicy.SIDE_RIGHT: out[0] = (pane[0] + pane[2]) / 2f; break;
            case DwindleTilingPolicy.SIDE_TOP: out[3] = (pane[1] + pane[3]) / 2f; break;
            default: out[1] = (pane[1] + pane[3]) / 2f; break;
        }
        return out;
    }

    /**
     * The rows the preview shows while the finger is over a strip: the gap its drop would use, or
     * -1 when a drop there would not move the window (the strip beside its own place, or none).
     */
    public static int previewGap(float x, float left, float right, float stripPx, int front,
                                 int count, int carried) {
        if (front < 0 || front >= count) return -1;
        int edge = edgeAt(x, left, right, stripPx);
        if (edge == 0) return -1;
        int gap = gapOf(edge, front);
        return placeForGap(gap, carried, count) < 0 ? -1 : gap;
    }

    /**
     * The steps a second finger's sideways travel {@code dxPx} pages the row: a swipe to the left
     * brings the next window (+1), to the right the previous one (-1), 0 short of {@code thresholdPx}.
     */
    public static int pageSteps(float dxPx, float thresholdPx) {
        if (dxPx <= -thresholdPx) return 1;
        if (dxPx >= thresholdPx) return -1;
        return 0;
    }
}
