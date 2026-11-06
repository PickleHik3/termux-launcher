package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.statusbar.StatusRowKeepOut;

/**
 * The one owner of the camera's keep-out box, and of how a surface at the screen's top gives way
 * to it. Pure arithmetic over window pixels, no Android types: the host reads Android's cutout
 * shape (the cutout path's bounds on API 31+, the bounding rects below) and the user's nudges,
 * and every reader (the status row's gap, the status bar's split, the pane's rim, the pane's
 * text inset) asks here.
 *
 * <p>The box runs from the screen's top edge down to the shape's bottom (a punch hole stands a
 * little in from the edge, but a card cannot pass over it from above), and sideways from the
 * shape's left to its right, grown by the margin sideways.
 */
public final class CameraHoleGeometry {

    private CameraHoleGeometry() {}

    /** A Custom box's width when Android reports no shape, in dp. */
    public static final float CUSTOM_BASE_DP = 24f;
    /** The narrowest card a split may leave beside the hole, in dp (or twice the radius). */
    public static final float SIDE_CARD_MIN_DP = 40f;

    /**
     * The box {@code {left, top, right, bottom}} in window pixels, or null for none: Off, Auto
     * with no shape, or a box that scales away. Custom reads the same shape and nudges it: scaled
     * about its centre by {@code scalePct}, moved by {@code (dxPx, dyPx)}, then grown by
     * {@code marginPx} left and right. Not below: a surface that starts at the shape's bottom (the
     * system bar showing) must stay clear of the box. With no shape Custom stands on a
     * {@code customBasePx} wide base the height of the top band, centred in the window.
     */
    @Nullable
    public static int[] keepOutBox(@NonNull StatusRowKeepOut.Mode mode, boolean hasShape,
                                   int shapeLeft, int shapeTop, int shapeRight, int shapeBottom,
                                   int topBandPx, int dxPx, int dyPx, int scalePct, int marginPx,
                                   int customBasePx, int windowWidthPx) {
        if (mode == StatusRowKeepOut.Mode.OFF) return null;
        int margin = Math.max(0, marginPx);
        if (mode == StatusRowKeepOut.Mode.AUTO) {
            if (!hasShape) return null;
            return new int[] {shapeLeft - margin, 0, shapeRight + margin, shapeBottom};
        }
        float left = shapeLeft;
        float top = shapeTop;
        float right = shapeRight;
        float bottom = shapeBottom;
        if (!hasShape) {
            if (windowWidthPx <= 0 || topBandPx <= 0 || customBasePx <= 0) return null;
            left = (windowWidthPx - customBasePx) / 2f;
            right = left + customBasePx;
            top = 0f;
            bottom = topBandPx;
        }
        float scale = Math.max(0, scalePct) / 100f;
        float cx = (left + right) / 2f;
        float cy = (top + bottom) / 2f;
        int outLeft = Math.round(cx - (right - left) * scale / 2f + dxPx) - margin;
        int outRight = Math.round(cx + (right - left) * scale / 2f + dxPx) + margin;
        int outBottom = Math.round(cy + (bottom - top) * scale / 2f + dyPx);
        if (scale <= 0f || outBottom <= 0) return null;
        return new int[] {outLeft, 0, outRight, outBottom};
    }

    /** How a surface at the top gives way to the box. */
    public enum Kind {
        /** The surface is untouched. */
        NONE,
        /** Two cards, the box in the gap between them. */
        TWO,
        /** One card whose left edge moves to the box's right edge. */
        TRIM_LEFT,
        /** One card whose right edge moves to the box's left edge. */
        TRIM_RIGHT,
        /** The surface is narrower than the hole: it stays whole and is not trimmed. */
        COVERED
    }

    /** The split's answer, in window pixels; immutable. */
    public static final class Split {
        public static final Split NONE = new Split(Kind.NONE, 0, 0);
        public static final Split COVERED = new Split(Kind.COVERED, 0, 0);

        @NonNull public final Kind kind;
        /** TWO: the left card's right edge; TRIM_RIGHT: the card's new right edge. */
        public final int leftCardRightPx;
        /** TWO: the right card's left edge; TRIM_LEFT: the card's new left edge. */
        public final int rightCardLeftPx;

        Split(@NonNull Kind kind, int leftCardRightPx, int rightCardLeftPx) {
            this.kind = kind;
            this.leftCardRightPx = leftCardRightPx;
            this.rightCardLeftPx = rightCardLeftPx;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Split)) return false;
            Split s = (Split) other;
            return kind == s.kind && leftCardRightPx == s.leftCardRightPx
                && rightCardLeftPx == s.rightCardLeftPx;
        }

        @Override
        public int hashCode() {
            return (kind.hashCode() * 31 + leftCardRightPx) * 31 + rightCardLeftPx;
        }

        @NonNull
        @Override
        public String toString() {
            return "Split{" + kind + ", " + leftCardRightPx + "|" + rightCardLeftPx + "}";
        }
    }

    /** The narrowest card a split leaves beside the hole: twice the radius, or 40dp if more. */
    public static int minSidePx(float radiusPx, float density) {
        return Math.round(Math.max(2f * Math.max(0f, radiusPx), SIDE_CARD_MIN_DP * density));
    }

    /**
     * How the surface {@code [left, right) × [top, bottom)} gives way to {@code box}. Horizontal
     * only, for the surface's full height: the height decides only whether the surface stands in
     * the box's band at all.
     */
    @NonNull
    public static Split split(@Nullable int[] box, int left, int top, int right, int bottom,
                              int minSidePx) {
        if (box == null || right <= left || bottom <= top) return Split.NONE;
        if (top >= box[3] || bottom <= box[1]) return Split.NONE;
        if (box[2] <= left || box[0] >= right) return Split.NONE;
        boolean leftOk = box[0] - left >= minSidePx;
        boolean rightOk = right - box[2] >= minSidePx;
        if (leftOk && rightOk) return new Split(Kind.TWO, box[0], box[2]);
        if (rightOk) return new Split(Kind.TRIM_LEFT, 0, box[2]);
        if (leftOk) return new Split(Kind.TRIM_RIGHT, box[0], 0);
        return Split.COVERED;
    }

    /**
     * Whether the cutout path's traced bounds {@code [traceLeft, traceRight)} stand for the
     * camera on the top edge whose bounding rect is {@code [rectLeft, rectRight)}: whenever the
     * two meet across, so the trace is that cutout's. The trace is then the shape, whole, not
     * cut to the rect: on pong the rect (454..540) stands left of the hole, which runs on past
     * 551, and cutting the trace to it put the right card over the camera.
     */
    public static boolean traceStandsForRect(int rectLeft, int rectRight, int traceLeft,
                                             int traceRight) {
        if (rectRight <= rectLeft || traceRight <= traceLeft) return false;
        return traceLeft < rectRight && traceRight > rectLeft;
    }

    /** {@link #barCardMargins}'s slots in its {@code out} array. */
    public static final int MAIN_LEFT = 0, MAIN_RIGHT = 1, SIDE_LEFT = 2, SIDE_RIGHT = 3;

    /**
     * Where the status bar's glass stands for {@code split} of a bar laid out at
     * {@code [left, right)} in window px: each card's margins from the bar's own edges, written to
     * {@code out} at {@link #MAIN_LEFT}, {@link #MAIN_RIGHT}, {@link #SIDE_LEFT} and
     * {@link #SIDE_RIGHT}. Every layer that draws a card (its tint and rim, its wallpaper frost)
     * takes these same margins. The main card takes the wider side (the right one on a tie);
     * {@code TRIM_*} trims the main card to the box's far edge; NONE and COVERED leave it whole.
     * A card's inner edge is the box's edge: the margin is already in the box. Returns whether
     * the side card shows.
     */
    public static boolean barCardMargins(@NonNull Split split, int left, int right,
                                         @NonNull int[] out) {
        out[MAIN_LEFT] = 0;
        out[MAIN_RIGHT] = 0;
        out[SIDE_LEFT] = 0;
        out[SIDE_RIGHT] = 0;
        switch (split.kind) {
            case TRIM_LEFT:
                out[MAIN_LEFT] = split.rightCardLeftPx - left;
                return false;
            case TRIM_RIGHT:
                out[MAIN_RIGHT] = right - split.leftCardRightPx;
                return false;
            case TWO:
                if (right - split.rightCardLeftPx >= split.leftCardRightPx - left) {
                    out[MAIN_LEFT] = split.rightCardLeftPx - left;
                    out[SIDE_RIGHT] = right - split.leftCardRightPx;
                } else {
                    out[MAIN_RIGHT] = right - split.leftCardRightPx;
                    out[SIDE_LEFT] = split.rightCardLeftPx - left;
                }
                return true;
            default:
                return false;
        }
    }

    /**
     * The span of a pane's top line to leave unstroked, in the pane's own x from its left edge,
     * written to {@code out[0..1]} as {@code [start, end)}. False (and {@code out} zeroed) when the
     * box does not reach the pane's top: the pane's top is at or below the box's bottom, or the
     * box is beside it.
     */
    public static boolean paneTopGap(@Nullable int[] box, int paneLeft, int paneTop, int paneRight,
                                     @NonNull int[] out) {
        out[0] = 0;
        out[1] = 0;
        if (box == null || paneRight <= paneLeft || paneTop >= box[3]) return false;
        int start = Math.max(0, box[0] - paneLeft);
        int end = Math.min(paneRight - paneLeft, box[2] - paneLeft);
        if (end <= start) return false;
        out[0] = start;
        out[1] = end;
        return true;
    }

    /**
     * The first Custom's width as a size in percent of the shape it now stands on (the shape's
     * width, or the base's with none), clamped to the slider's 50..200.
     */
    public static int migrateScalePct(int oldWidthPx, int baseWidthPx) {
        if (baseWidthPx <= 0) return 100;
        return Math.max(50, Math.min(200, Math.round(100f * oldWidthPx / baseWidthPx)));
    }

    /**
     * The first Custom's centre as a sideways move in dp from the shape's centre (the window's
     * with none), clamped to the slider's -200..200.
     */
    public static int migrateOffsetXDp(int oldCentreXPx, int baseCentreXPx, float density) {
        float dp = (oldCentreXPx - baseCentreXPx) / Math.max(0.01f, density);
        return Math.max(-200, Math.min(200, Math.round(dp)));
    }
}
