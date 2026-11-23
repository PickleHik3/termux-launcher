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
 * shape on the top edge, see {@link com.termux.app.chrome.CameraHoleGeometry}) plus the margin each
 * side, or the user's own nudges when Android's is wrong or missing. It applies only while the row stands in the hole's band: a row
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

    /** The margin's default: the air kept from the camera on each side. */
    public static final float GAP_MARGIN_DP = 4f;

    /** Whose box the camera gap follows. */
    public enum Mode {
        /** Android's display cutout box (the default). */
        AUTO,
        /** Android's shape nudged by the user (sideways, down, size), or their own box if missing. */
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
        /** The user's nudges to the shape, Custom only: sideways, down, and size in percent. */
        public final int offsetXPx;
        public final int offsetYPx;
        public final int scalePct;
        /** The air kept from the camera on each side, in pixels (Auto and Custom). */
        public final int marginPx;
        /** A Custom box's width when Android reports no shape. */
        public final int customBasePx;

        public Screen(boolean hasHole, int holeLeft, int holeTop, int holeRight, int holeBottom,
                      int topBandPx, float radiusTopLeft, float radiusTopRight,
                      float radiusBottomLeft, float radiusBottomRight, @NonNull Mode mode,
                      int offsetXPx, int offsetYPx, int scalePct, int marginPx,
                      int customBasePx) {
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
            this.offsetXPx = offsetXPx;
            this.offsetYPx = offsetYPx;
            this.scalePct = Math.max(0, scalePct);
            this.marginPx = Math.max(0, marginPx);
            this.customBasePx = Math.max(0, customBasePx);
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
                && mode == s.mode && offsetXPx == s.offsetXPx && offsetYPx == s.offsetYPx
                && scalePct == s.scalePct && marginPx == s.marginPx
                && customBasePx == s.customBasePx;
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
            h = 31 * h + offsetXPx;
            h = 31 * h + offsetYPx;
            h = 31 * h + scalePct;
            h = 31 * h + marginPx;
            h = 31 * h + customBasePx;
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
    public static int[] keepOutBox(@NonNull Screen screen, int windowWidth) {
        return com.termux.app.chrome.CameraHoleGeometry.keepOutBox(screen.mode, screen.hasHole,
            screen.holeLeft, screen.holeTop, screen.holeRight, screen.holeBottom,
            screen.topBandPx, screen.offsetXPx, screen.offsetYPx, screen.scalePct,
            screen.marginPx, screen.customBasePx, windowWidth);
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
        flow(marginLeft, width, marginRight, weight, new int[width.length], start, end, gapStart,
            gapEnd, outLeft, outWidth);
    }

    /**
     * {@link #flow(int[], int[], int[], float[], int, int, int, int, int[], int[])} with a floor
     * for each flexible child: {@code minWidth[i]} is the least child {@code i} may be given. A
     * flexible child below its floor first takes from the other flexible children, then the fixed
     * children in its group are dropped whole (width and margins both 0, see {@link #isDropped}),
     * nearest the flexible child first, until the floor holds or nothing is left to drop.
     */
    public static void flow(@NonNull int[] marginLeft, @NonNull int[] width,
                            @NonNull int[] marginRight, @NonNull float[] weight,
                            @NonNull int[] minWidth, int start, int end, int gapStart,
                            int gapEnd, @NonNull int[] outLeft, @NonNull int[] outWidth) {
        int n = width.length;
        int gs = Math.max(start, gapStart);
        int ge = Math.min(end, gapEnd);
        if (ge <= gs) {
            placeGroup(marginLeft, width, marginRight, weight, minWidth, 0, n, start, end, false,
                outLeft, outWidth);
            return;
        }
        placeGroup(marginLeft, width, marginRight, weight, minWidth, 0, n, start, end, false,
            outLeft, outWidth);
        float gapCentre = (gs + ge) / 2f;
        int natural = 0;
        while (natural < n && outLeft[natural] + outWidth[natural] / 2f < gapCentre) natural++;

        int split = natural;
        if (!fits(marginLeft, width, marginRight, weight, minWidth, natural, start, gs, ge, end)) {
            boolean leftOverflows = !hasFloor(weight, minWidth, 0, natural)
                && fixedSum(marginLeft, width, marginRight, weight, 0, natural) > gs - start;
            for (int s = natural; s >= 0 && s <= n; s += leftOverflows ? -1 : 1) {
                if (fits(marginLeft, width, marginRight, weight, minWidth, s, start, gs, ge, end)) {
                    split = s;
                    break;
                }
            }
        }
        placeGroup(marginLeft, width, marginRight, weight, minWidth, 0, split, start, gs, false,
            outLeft, outWidth);
        placeGroup(marginLeft, width, marginRight, weight, minWidth, split, n, ge, end, true,
            outLeft, outWidth);
    }

    /**
     * Whether both sides of the gap can hold their children: a side with a flexible child that has
     * a floor always can, since that child drops fixed ones until its floor holds.
     */
    private static boolean fits(int[] marginLeft, int[] width, int[] marginRight, float[] weight,
                                int[] minWidth, int split, int start, int gs, int ge, int end) {
        int n = width.length;
        return (hasFloor(weight, minWidth, 0, split)
                || fixedSum(marginLeft, width, marginRight, weight, 0, split) <= gs - start)
            && (hasFloor(weight, minWidth, split, n)
                || fixedSum(marginLeft, width, marginRight, weight, split, n) <= end - ge);
    }

    private static boolean hasFloor(float[] weight, int[] minWidth, int from, int to) {
        for (int i = from; i < to; i++) if (weight[i] > 0f && minWidth[i] > 0) return true;
        return false;
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

    /** {@link #partAtGap}'s answer, indexes into its {@code out}: row pixels from the row's left. */
    public static final int PART_CHIPS_LEFT = 0;
    public static final int PART_CHIPS_RIGHT = 1;
    public static final int PART_READINGS_LEFT = 2;
    public static final int PART_READINGS_RIGHT = 3;
    /** How many of the readings' items stand, the first ones in their order. */
    public static final int PART_READINGS_KEPT = 4;
    public static final int PART_SIZE = 5;

    /**
     * How many of the readings' items fit {@code roomPx}: the first ones in the user's order,
     * whole, so the readings at the far end go first. {@code chromePx} (the group's own margins
     * and padding) counts once when anything stands; a dot ({@code mark}) never stands last,
     * since it shows only between two readings. 0 when not even the first reading fits.
     */
    public static int readingsThatFit(@NonNull int[] itemWidth, @NonNull boolean[] mark,
                                      int count, int chromePx, int roomPx) {
        int used = Math.max(0, chromePx);
        int kept = 0;
        while (kept < count && used + itemWidth[kept] <= roomPx) {
            used += itemWidth[kept];
            kept++;
        }
        while (kept > 0 && mark[kept - 1]) kept--;
        return kept;
    }

    /** What the first {@code kept} items take with the group's chrome; 0 when none stand. */
    public static int readingsWidth(@NonNull int[] itemWidth, int kept, int chromePx) {
        if (kept <= 0) return 0;
        int width = Math.max(0, chromePx);
        for (int i = 0; i < kept; i++) width += itemWidth[i];
        return width;
    }

    /**
     * The row parted at the camera gap: the chips stand before the gap and the readings after it,
     * each within its own stretch, nothing under the camera. Before and after are in reading
     * order: in a right-to-left row the chips stand right of the camera and the readings left of
     * it. The chips fill their stretch; the readings hug the row's far end and keep as many whole
     * items as fit ({@link #readingsThatFit}). Two cameras near an end are handled too: when the
     * chips' stretch cannot hold {@code chipFloorPx} (one whole chip), chips and readings both
     * stand after the camera, the readings in what the floor leaves; when not one reading fits
     * after the camera, they stand before it, just ahead of the gap, in what the floor leaves.
     *
     * @param start   where the row's content starts (its left corner inset), row pixels
     * @param end     where it ends (the row's width less its right corner inset)
     * @param itemWidth each item's width with its margins, in the readings' own order (the order
     *                the user set; in a right-to-left row the first stands rightmost)
     * @param out     {@link #PART_SIZE} values, see the {@code PART_} indexes
     */
    public static void partAtGap(int start, int end, int gapStart, int gapEnd, boolean rtl,
                                 int chipFloorPx, @NonNull int[] itemWidth,
                                 @NonNull boolean[] mark, int count, int chromePx,
                                 @NonNull int[] out) {
        // Worked in reading order (x mirrored in a right-to-left row), turned back at the end.
        int gs = Math.max(start, Math.min(end, gapStart));
        int ge = Math.max(gs, Math.min(end, gapEnd));
        int s = rtl ? -end : start;
        int e = rtl ? -start : end;
        int before = rtl ? -ge : gs;
        int after = rtl ? -gs : ge;
        int floor = Math.max(0, chipFloorPx);
        int chipsLeft;
        int chipsRight;
        int readingsLeft;
        int readingsRight;
        int kept;
        if (before - s >= floor) {
            kept = readingsThatFit(itemWidth, mark, count, chromePx, e - after);
            if (kept > 0 || count == 0) {
                chipsLeft = s;
                chipsRight = before;
                readingsRight = e;
                readingsLeft = e - readingsWidth(itemWidth, kept, chromePx);
            } else {
                kept = readingsThatFit(itemWidth, mark, count, chromePx, before - s - floor);
                int width = readingsWidth(itemWidth, kept, chromePx);
                chipsLeft = s;
                chipsRight = before - width;
                readingsLeft = before - width;
                readingsRight = before;
            }
        } else {
            kept = readingsThatFit(itemWidth, mark, count, chromePx, e - after - floor);
            int width = readingsWidth(itemWidth, kept, chromePx);
            chipsLeft = after;
            chipsRight = Math.max(after, e - width);
            readingsLeft = e - width;
            readingsRight = e;
        }
        out[PART_CHIPS_LEFT] = rtl ? -chipsRight : chipsLeft;
        out[PART_CHIPS_RIGHT] = rtl ? -chipsLeft : chipsRight;
        out[PART_READINGS_LEFT] = rtl ? -readingsRight : readingsLeft;
        out[PART_READINGS_RIGHT] = rtl ? -readingsLeft : readingsRight;
        out[PART_READINGS_KEPT] = kept;
    }

    /** Whether {@code flow} dropped child {@code i}: a fixed child that had width and got none. */
    public static boolean isDropped(int width, float weight, int outWidth) {
        return weight <= 0f && width > 0 && outWidth == 0;
    }

    private static void placeGroup(int[] marginLeft, int[] width, int[] marginRight,
                                   float[] weight, int[] minWidth, int from, int to,
                                   int groupStart, int groupEnd, boolean hugEnd,
                                   int[] outLeft, int[] outWidth) {
        int fixed = fixedSum(marginLeft, width, marginRight, weight, from, to);
        float weights = 0f;
        int needed = 0;
        int firstFlexible = -1;
        for (int i = from; i < to; i++) {
            if (weight[i] <= 0f) continue;
            weights += weight[i];
            needed += Math.max(0, minWidth[i]);
            if (firstFlexible < 0) firstFlexible = i;
        }
        boolean[] dropped = null;
        if (needed > 0) {
            // The floors come before the fixed children: drop them whole, nearest the flexible
            // child first (the readings next to the chips before the mark at the row's end).
            while (groupEnd - groupStart - fixed < needed) {
                int pick = -1;
                for (int i = from; i < to; i++) {
                    if (weight[i] > 0f || width[i] <= 0 || (dropped != null && dropped[i])) continue;
                    if (pick < 0 || Math.abs(i - firstFlexible) <= Math.abs(pick - firstFlexible)) {
                        pick = i;
                    }
                }
                if (pick < 0) break;
                if (dropped == null) dropped = new boolean[width.length];
                dropped[pick] = true;
                fixed -= marginLeft[pick] + marginRight[pick] + width[pick];
            }
        }
        int free = Math.max(0, groupEnd - groupStart - fixed);
        float[] share = null;
        if (needed > 0 && weights > 0f) share = flexibleShares(weight, minWidth, from, to, free);
        int x = groupStart;
        if (weights <= 0f && hugEnd) x = Math.max(groupStart, groupEnd - fixed);
        int handedOut = 0;
        float weightLeft = weights;
        for (int i = from; i < to; i++) {
            boolean drop = dropped != null && dropped[i];
            if (!drop) x += marginLeft[i];
            int w;
            if (weight[i] > 0f) {
                // The last flexible child takes what rounding left, so the side fills exactly.
                w = weightLeft - weight[i] <= 0f ? free - handedOut
                    : share != null ? Math.round(share[i])
                    : Math.round(free * weight[i] / weights);
                handedOut += w;
                weightLeft -= weight[i];
            } else {
                w = drop ? 0 : width[i];
            }
            outLeft[i] = x;
            outWidth[i] = w;
            x += w + (drop ? 0 : marginRight[i]);
        }
    }

    /**
     * {@code free} shared by weight between the flexible children {@code [from, to)}, except that
     * a child whose share is under its floor stands at the floor and the rest share what is left.
     * When the floors alone exceed {@code free} they share it in proportion.
     */
    private static float[] flexibleShares(float[] weight, int[] minWidth, int from, int to,
                                          int free) {
        float[] share = new float[weight.length];
        boolean[] pinned = new boolean[weight.length];
        int floors = 0;
        for (int i = from; i < to; i++) if (weight[i] > 0f) floors += Math.max(0, minWidth[i]);
        if (floors >= free) {
            for (int i = from; i < to; i++) {
                if (weight[i] > 0f) share[i] = floors == 0 ? 0f : free * (float) Math.max(0, minWidth[i]) / floors;
            }
            return share;
        }
        boolean again = true;
        while (again) {
            again = false;
            float left = free;
            float weights = 0f;
            for (int i = from; i < to; i++) {
                if (weight[i] <= 0f) continue;
                if (pinned[i]) left -= Math.max(0, minWidth[i]); else weights += weight[i];
            }
            for (int i = from; i < to; i++) {
                if (weight[i] <= 0f || pinned[i]) continue;
                share[i] = weights <= 0f ? 0f : left * weight[i] / weights;
                if (share[i] < minWidth[i]) {
                    pinned[i] = true;
                    again = true;
                }
            }
        }
        for (int i = from; i < to; i++) if (weight[i] > 0f && pinned[i]) share[i] = minWidth[i];
        return share;
    }
}
