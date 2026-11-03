package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the screen itself takes from the status row: the camera hole the row may share a band with
 * (#163), and the rounded corners its ends may run into. Pure arithmetic over window pixels, so
 * {@link StatusRowLayout} only applies the answer.
 *
 * <p>The hole is a gap the row flows around: the place's chips and the readings fill the room on
 * either side of it, and nothing is drawn under it. Its box is Android's (the display cutout's
 * bounding rect on the top edge) plus {@link #GAP_MARGIN_DP} each side, or the user's own when
 * Android's is wrong or missing. It applies only while the row stands in the hole's band: a row
 * below the system status bar, in landscape, or on a phone without a top cutout gets none.
 *
 * <p>The corners: a screen corner of radius {@code r} is a circle centred {@code r} in from both
 * edges, so at a height {@code y} from the edge it meets (with {@code y < r}) the curve stands
 * {@code r - sqrt(r² - (r - y)²)} in from the side. The row is a band from its top to its bottom,
 * and the curve is deepest at the band's edge nearest the corner, so that is where it is measured.
 * The row's end steps in by what the curve reaches past the row's own edge, and no further.
 */
public final class StatusRowKeepOut {

    private StatusRowKeepOut() {}

    /** The air the row keeps from Android's camera box on each side. */
    public static final float GAP_MARGIN_DP = 4f;

    /** Whose box the camera gap follows. */
    public enum Mode {
        /** Android's display cutout box (the default). */
        AUTO,
        /** The user's own centre and width, for a box that is too big or missing. */
        CUSTOM,
        /** No gap at all. */
        OFF;

        /** The stored preference value, read leniently: anything unknown is {@link #AUTO}. */
        @NonNull
        public static Mode fromPreference(@Nullable String value) {
            if ("custom".equals(value)) return CUSTOM;
            if ("off".equals(value)) return OFF;
            return AUTO;
        }
    }

    /** What the window says about its own shape, in window pixels. Immutable, compared by value. */
    public static final class Screen {
        /** Android's camera box on the top edge; {@code hasHole} false when it reports none. */
        public final boolean hasHole;
        public final int holeLeft;
        public final int holeTop;
        public final int holeRight;
        public final int holeBottom;
        /** The system status bar's height whether or not it shows: a custom box's band. */
        public final int topBandPx;
        /** Each screen corner's radius; 0 where it is square or unknown. */
        public final float radiusTopLeft;
        public final float radiusTopRight;
        public final float radiusBottomLeft;
        public final float radiusBottomRight;
        @NonNull public final Mode mode;
        /** A custom box's centre, from the window's horizontal centre; and its full width. */
        public final int customCenterOffsetPx;
        public final int customWidthPx;
        /** {@link #GAP_MARGIN_DP} in pixels. */
        public final int gapMarginPx;

        public Screen(boolean hasHole, int holeLeft, int holeTop, int holeRight, int holeBottom,
                      int topBandPx, float radiusTopLeft, float radiusTopRight,
                      float radiusBottomLeft, float radiusBottomRight, @NonNull Mode mode,
                      int customCenterOffsetPx, int customWidthPx, int gapMarginPx) {
            this.hasHole = hasHole && holeRight > holeLeft && holeBottom > holeTop;
            this.holeLeft = this.hasHole ? holeLeft : 0;
            this.holeTop = this.hasHole ? holeTop : 0;
            this.holeRight = this.hasHole ? holeRight : 0;
            this.holeBottom = this.hasHole ? holeBottom : 0;
            this.topBandPx = Math.max(0, topBandPx);
            this.radiusTopLeft = Math.max(0f, radiusTopLeft);
            this.radiusTopRight = Math.max(0f, radiusTopRight);
            this.radiusBottomLeft = Math.max(0f, radiusBottomLeft);
            this.radiusBottomRight = Math.max(0f, radiusBottomRight);
            this.mode = mode;
            this.customCenterOffsetPx = customCenterOffsetPx;
            this.customWidthPx = Math.max(0, customWidthPx);
            this.gapMarginPx = Math.max(0, gapMarginPx);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Screen)) return false;
            Screen s = (Screen) other;
            return hasHole == s.hasHole && holeLeft == s.holeLeft && holeTop == s.holeTop
                && holeRight == s.holeRight && holeBottom == s.holeBottom
                && topBandPx == s.topBandPx
                && Float.compare(radiusTopLeft, s.radiusTopLeft) == 0
                && Float.compare(radiusTopRight, s.radiusTopRight) == 0
                && Float.compare(radiusBottomLeft, s.radiusBottomLeft) == 0
                && Float.compare(radiusBottomRight, s.radiusBottomRight) == 0
                && mode == s.mode && customCenterOffsetPx == s.customCenterOffsetPx
                && customWidthPx == s.customWidthPx && gapMarginPx == s.gapMarginPx;
        }

        @Override
        public int hashCode() {
            int h = hasHole ? 1 : 0;
            h = 31 * h + holeLeft;
            h = 31 * h + holeTop;
            h = 31 * h + holeRight;
            h = 31 * h + holeBottom;
            h = 31 * h + topBandPx;
            h = 31 * h + Float.floatToIntBits(radiusTopLeft);
            h = 31 * h + Float.floatToIntBits(radiusTopRight);
            h = 31 * h + Float.floatToIntBits(radiusBottomLeft);
            h = 31 * h + Float.floatToIntBits(radiusBottomRight);
            h = 31 * h + mode.hashCode();
            h = 31 * h + customCenterOffsetPx;
            h = 31 * h + customWidthPx;
            h = 31 * h + gapMarginPx;
            return h;
        }
    }

    /** The row's answer, in the row's own pixels from its left edge. */
    public static final class Answer {
        public static final Answer NONE = new Answer(0, 0, 0, 0);

        /** How far each end of the row steps in to clear the screen's corner. */
        public final int leftInsetPx;
        public final int rightInsetPx;
        /** The span the row leaves empty for the camera; empty when there is no gap. */
        public final int gapStartPx;
        public final int gapEndPx;

        public Answer(int leftInsetPx, int rightInsetPx, int gapStartPx, int gapEndPx) {
            this.leftInsetPx = Math.max(0, leftInsetPx);
            this.rightInsetPx = Math.max(0, rightInsetPx);
            boolean gap = gapEndPx > gapStartPx;
            this.gapStartPx = gap ? gapStartPx : 0;
            this.gapEndPx = gap ? gapEndPx : 0;
        }

        public boolean hasGap() {
            return gapEndPx > gapStartPx;
        }

        /** Nothing to keep clear: the row lays itself out as it always has. */
        public boolean isNone() {
            return leftInsetPx == 0 && rightInsetPx == 0 && !hasGap();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Answer)) return false;
            Answer a = (Answer) other;
            return leftInsetPx == a.leftInsetPx && rightInsetPx == a.rightInsetPx
                && gapStartPx == a.gapStartPx && gapEndPx == a.gapEndPx;
        }

        @Override
        public int hashCode() {
            return ((leftInsetPx * 31 + rightInsetPx) * 31 + gapStartPx) * 31 + gapEndPx;
        }

        @NonNull
        @Override
        public String toString() {
            return "Answer{left=" + leftInsetPx + ", right=" + rightInsetPx
                + ", gap=" + gapStartPx + ".." + gapEndPx + "}";
        }
    }

    /**
     * How far in from the side a screen corner of {@code radiusPx} stands at {@code distancePx}
     * from the edge it rounds: {@code r - sqrt(r² - (r - y)²)} while {@code y < r}, else nothing.
     */
    public static float cornerInsetPx(float radiusPx, float distancePx) {
        if (radiusPx <= 0f) return 0f;
        float y = Math.max(0f, distancePx);
        if (y >= radiusPx) return 0f;
        float d = radiusPx - y;
        return radiusPx - (float) Math.sqrt(radiusPx * radiusPx - d * d);
    }

    /**
     * The answer for a row standing at {@code [rowLeft, rowRight) × [rowTop, rowBottom)} in a window
     * {@code windowWidth × windowHeight}. A window size of 0 leaves the far corners out.
     */
    @NonNull
    public static Answer answer(@Nullable Screen screen, int windowWidth, int windowHeight,
                                int rowLeft, int rowTop, int rowRight, int rowBottom) {
        if (screen == null || rowRight <= rowLeft || rowBottom <= rowTop) return Answer.NONE;
        int rowWidth = rowRight - rowLeft;
        float fromBottom = windowHeight > 0 ? windowHeight - rowBottom : Float.MAX_VALUE;
        float leftEdge = Math.max(cornerInsetPx(screen.radiusTopLeft, rowTop),
            cornerInsetPx(screen.radiusBottomLeft, fromBottom));
        float rightEdge = Math.max(cornerInsetPx(screen.radiusTopRight, rowTop),
            cornerInsetPx(screen.radiusBottomRight, fromBottom));
        int left = Math.max(0, (int) Math.ceil(leftEdge - rowLeft));
        int right = windowWidth > 0
            ? Math.max(0, (int) Math.ceil(rightEdge - (windowWidth - rowRight))) : 0;

        int gapStart = 0;
        int gapEnd = 0;
        int[] box = keepOutBox(screen, windowWidth);
        if (box != null && rowTop < box[3] && rowBottom > box[1]) {
            gapStart = Math.max(0, box[0] - rowLeft);
            gapEnd = Math.min(rowWidth, box[2] - rowLeft);
        }
        return new Answer(left, right, gapStart, gapEnd);
    }

    /**
     * The camera's keep-out box in window pixels, {@code {left, top, right, bottom}}, or null when
     * there is none to keep clear of.
     */
    @Nullable
    static int[] keepOutBox(@NonNull Screen screen, int windowWidth) {
        switch (screen.mode) {
            case OFF:
                return null;
            case CUSTOM: {
                if (screen.customWidthPx <= 0 || windowWidth <= 0) return null;
                int bottom = screen.hasHole ? screen.holeBottom : screen.topBandPx;
                if (bottom <= 0) return null;
                int centre = windowWidth / 2 + screen.customCenterOffsetPx;
                int left = centre - screen.customWidthPx / 2;
                return new int[] {left, 0, left + screen.customWidthPx, bottom};
            }
            case AUTO:
            default:
                if (!screen.hasHole) return null;
                return new int[] {screen.holeLeft - screen.gapMarginPx, screen.holeTop,
                    screen.holeRight + screen.gapMarginPx, screen.holeBottom};
        }
    }

    /**
     * Lays a row's children out between {@code start} and {@code end}, around the gap
     * {@code [gapStart, gapEnd)} when it falls inside them. Children are given in visual order,
     * left to right, by their margins and widths; a child with a weight is flexible and takes its
     * share of what its side has left (its width in is ignored). Writes each child's left edge and
     * width.
     *
     * <p>The children keep their order. Those whose natural centre lies before the gap's centre
     * stand left of it and the rest right of it, unless one side cannot hold its share, in which
     * case children cross to the side with room. A side with a flexible child fills its stretch;
     * otherwise the left side packs to its start and the right side to its end, the way the
     * readings hug the row's end.
     */
    public static void flow(@NonNull int[] marginLeft, @NonNull int[] width,
                            @NonNull int[] marginRight, @NonNull float[] weight,
                            int start, int end, int gapStart, int gapEnd,
                            @NonNull int[] outLeft, @NonNull int[] outWidth) {
        int n = width.length;
        int gs = Math.max(start, gapStart);
        int ge = Math.min(end, gapEnd);
        if (ge <= gs) {
            placeGroup(marginLeft, width, marginRight, weight, 0, n, start, end, false,
                outLeft, outWidth);
            return;
        }
        placeGroup(marginLeft, width, marginRight, weight, 0, n, start, end, false,
            outLeft, outWidth);
        float gapCentre = (gs + ge) / 2f;
        int natural = 0;
        while (natural < n && outLeft[natural] + outWidth[natural] / 2f < gapCentre) natural++;

        int split = natural;
        if (!fits(marginLeft, width, marginRight, weight, natural, start, gs, ge, end)) {
            boolean leftOverflows = fixedSum(marginLeft, width, marginRight, weight, 0, natural)
                > gs - start;
            for (int s = natural; s >= 0 && s <= n; s += leftOverflows ? -1 : 1) {
                if (fits(marginLeft, width, marginRight, weight, s, start, gs, ge, end)) {
                    split = s;
                    break;
                }
            }
        }
        placeGroup(marginLeft, width, marginRight, weight, 0, split, start, gs, false,
            outLeft, outWidth);
        placeGroup(marginLeft, width, marginRight, weight, split, n, ge, end, true,
            outLeft, outWidth);
    }

    private static boolean fits(int[] marginLeft, int[] width, int[] marginRight, float[] weight,
                                int split, int start, int gs, int ge, int end) {
        return fixedSum(marginLeft, width, marginRight, weight, 0, split) <= gs - start
            && fixedSum(marginLeft, width, marginRight, weight, split, width.length) <= end - ge;
    }

    /** What children {@code [from, to)} take before any flexible one gets a share. */
    private static int fixedSum(int[] marginLeft, int[] width, int[] marginRight, float[] weight,
                                int from, int to) {
        int sum = 0;
        for (int i = from; i < to; i++) {
            sum += marginLeft[i] + marginRight[i] + (weight[i] > 0f ? 0 : width[i]);
        }
        return sum;
    }

    private static void placeGroup(int[] marginLeft, int[] width, int[] marginRight,
                                   float[] weight, int from, int to, int groupStart, int groupEnd,
                                   boolean hugEnd, int[] outLeft, int[] outWidth) {
        int fixed = fixedSum(marginLeft, width, marginRight, weight, from, to);
        float weights = 0f;
        for (int i = from; i < to; i++) if (weight[i] > 0f) weights += weight[i];
        int free = Math.max(0, groupEnd - groupStart - fixed);
        int x = groupStart;
        if (weights <= 0f && hugEnd) x = Math.max(groupStart, groupEnd - fixed);
        int handedOut = 0;
        float weightLeft = weights;
        for (int i = from; i < to; i++) {
            x += marginLeft[i];
            int w;
            if (weight[i] > 0f) {
                // The last flexible child takes what rounding left, so the side fills exactly.
                w = weightLeft - weight[i] <= 0f ? free - handedOut
                    : Math.round(free * weight[i] / weights);
                handedOut += w;
                weightLeft -= weight[i];
            } else {
                w = width[i];
            }
            outLeft[i] = x;
            outWidth[i] = w;
            x += w + marginRight[i];
        }
    }
}
