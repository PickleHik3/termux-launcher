package com.termux.app.tiling;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A gap drag worked out once, as the finger goes down on the gap (round 3; review of
 * terl-window-ops): which windows the dragged gap moves and how each one's rectangle follows the
 * gap's travel {@code d}, linearly, and how far {@code d} may go before a window would fall under
 * its minimum. Each move of the drag is then a few multiplications per window over these arrays:
 * no allocation and no layout of the Workspace, as touch and draw callbacks require. The release
 * makes the real change once ({@link #delta} is what the layout policies are given).
 *
 * <p>Rectangles are in the layout's coordinates (a Scrolling strip's, a Dwindle room's). Windows
 * the gap does not reach are not in the plan.
 */
public final class GapPreview {

    /** The windows the gap moves, and their rectangles as laid out when the drag began. */
    @NonNull public final long[] ids;
    @NonNull private final float[] left;
    @NonNull private final float[] top;
    @NonNull private final float[] width;
    @NonNull private final float[] height;
    // How much of the travel each edge takes: left and top move, width and height grow.
    @NonNull private final float[] kLeft;
    @NonNull private final float[] kTop;
    @NonNull private final float[] kWidth;
    @NonNull private final float[] kHeight;
    /** Whether the gap moves sideways (a column gap, a side-by-side split) or up and down. */
    public final boolean sideways;
    /** The gap's middle across it as the drag began, in the layout's coordinates. */
    public final float centre;
    /** How far the gap may travel back (negative or zero) and on (positive or zero). */
    public final float minDelta;
    public final float maxDelta;

    private GapPreview(@NonNull List<float[]> rows, @NonNull long[] ids, boolean sideways, float centre,
                       float minDelta, float maxDelta) {
        int n = ids.length;
        this.ids = ids;
        left = new float[n];
        top = new float[n];
        width = new float[n];
        height = new float[n];
        kLeft = new float[n];
        kTop = new float[n];
        kWidth = new float[n];
        kHeight = new float[n];
        for (int i = 0; i < n; i++) {
            float[] r = rows.get(i);
            left[i] = r[0];
            top[i] = r[1];
            width[i] = r[2];
            height[i] = r[3];
            kLeft[i] = r[4];
            kTop[i] = r[5];
            kWidth[i] = r[6];
            kHeight[i] = r[7];
        }
        this.sideways = sideways;
        this.centre = centre;
        // Never inverted: a gap already past a minimum (a smaller screen) neither grows nor shrinks it.
        this.minDelta = Math.min(0f, minDelta);
        this.maxDelta = Math.max(0f, maxDelta);
    }

    /** The finger's travel along the gap ({@code dx} or {@code dy}) as the gap may follow it. */
    public float delta(float dx, float dy) {
        float d = sideways ? dx : dy;
        return Math.max(minDelta, Math.min(maxDelta, d));
    }

    public int count() {
        return ids.length;
    }

    public float leftAt(int i, float d) {
        return left[i] + kLeft[i] * d;
    }

    public float topAt(int i, float d) {
        return top[i] + kTop[i] * d;
    }

    public float widthAt(int i, float d) {
        return width[i] + kWidth[i] * d;
    }

    public float heightAt(int i, float d) {
        return height[i] + kHeight[i] * d;
    }

    /** The gap's middle across it after a travel of {@code d}. */
    public float centreAt(float d) {
        return centre + d;
    }

    private static float[] row(@NonNull ScrollingLayoutPolicy.Placement p, float kl, float kt, float kw, float kh) {
        return new float[] {p.left, p.top, p.width(), p.height(), kl, kt, kw, kh};
    }

    private static long[] idsOf(@NonNull List<Long> ids) {
        long[] out = new long[ids.size()];
        for (int i = 0; i < out.length; i++) out[i] = ids.get(i);
        return out;
    }

    /**
     * The gap right of Scrolling column {@code leftColumn}: that column widens with the gap, every
     * column right of it moves with it, the strip's scroll stays (so the gap is under the finger even
     * when the strip stood at its end); the column stays between {@code minWidthPx} and
     * {@code maxWidthPx}.
     */
    @NonNull
    public static GapPreview columnGap(@NonNull ScrollingLayoutPolicy.Layout layout, int leftColumn,
                                       float minWidthPx, float maxWidthPx) {
        List<float[]> rows = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        float right = 0f;
        float nextLeft = Float.NaN;
        float columnWidth = 0f;
        for (ScrollingLayoutPolicy.Placement p : layout.placements) {
            if (p.column == leftColumn) {
                rows.add(row(p, 0f, 0f, 1f, 0f));
                right = p.right;
                columnWidth = p.width();
            } else if (p.column > leftColumn) {
                rows.add(row(p, 1f, 0f, 0f, 0f));
                if (p.column == leftColumn + 1) nextLeft = p.left;
            } else {
                continue;
            }
            ids.add(p.windowId);
        }
        float centre = Float.isNaN(nextLeft) ? right : (right + nextLeft) / 2f;
        return new GapPreview(rows, idsOf(ids), true, centre, minWidthPx - columnWidth, maxWidthPx - columnWidth);
    }

    /**
     * The gap between the two windows stacked in Scrolling column {@code column}: the top window
     * grows with it, the bottom one moves and shrinks; each keeps at least {@code minHeightPx}.
     */
    @NonNull
    public static GapPreview stackGap(@NonNull ScrollingLayoutPolicy.Layout layout, int column, float minHeightPx) {
        List<float[]> rows = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        ScrollingLayoutPolicy.Placement upper = null;
        ScrollingLayoutPolicy.Placement lower = null;
        for (ScrollingLayoutPolicy.Placement p : layout.placements) {
            if (p.column != column) continue;
            if (p.row == 0) upper = p;
            else lower = p;
        }
        if (upper == null || lower == null) {
            return new GapPreview(rows, new long[0], false, 0f, 0f, 0f);
        }
        rows.add(row(upper, 0f, 0f, 0f, 1f));
        ids.add(upper.windowId);
        rows.add(row(lower, 0f, 1f, 0f, -1f));
        ids.add(lower.windowId);
        float centre = (upper.bottom + lower.top) / 2f;
        return new GapPreview(rows, idsOf(ids), false, centre, minHeightPx - upper.height(),
            lower.height() - minHeightPx);
    }

    /**
     * Dwindle split {@code split}'s gap in {@code tiles}: each tile in the split's room moves and
     * resizes with its side, nested tiles in proportion; the first side keeps at least
     * {@code minFirst} along the gap's way and the second {@code minSecond}
     * ({@link DwindleTree#sideMinimums}).
     */
    @NonNull
    public static GapPreview split(@NonNull DwindleTree.Tiles tiles, int split, float minFirst, float minSecond) {
        List<float[]> rows = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        if (split < 0 || split >= tiles.gaps.size()) return new GapPreview(rows, new long[0], true, 0f, 0f, 0f);
        DwindleTree.Gap gap = tiles.gaps.get(split);
        boolean sideways = gap.sideBySide;
        float cut = sideways ? gap.left : gap.top;
        float after = sideways ? gap.right : gap.bottom;
        float first = Math.max(1f, cut - gap.start);
        float second = Math.max(1f, gap.end - after);
        for (ScrollingLayoutPolicy.Placement p : tiles.layout.placements) {
            // In the split's room: across the gap within its length, along it within the room.
            float acrossFrom = sideways ? p.top : p.left;
            float acrossTo = sideways ? p.bottom : p.right;
            float lengthFrom = sideways ? gap.top : gap.left;
            float lengthTo = sideways ? gap.bottom : gap.right;
            float from = sideways ? p.left : p.top;
            float to = sideways ? p.right : p.bottom;
            if (acrossFrom < lengthFrom - 0.5f || acrossTo > lengthTo + 0.5f
                || from < gap.start - 0.5f || to > gap.end + 0.5f) continue;
            float kMove;
            float kGrow;
            if (to <= cut + 0.5f) {
                float u0 = (from - gap.start) / first;
                float u1 = (to - gap.start) / first;
                kMove = u0;
                kGrow = u1 - u0;
            } else if (from >= after - 0.5f) {
                float v0 = (from - after) / second;
                float v1 = (to - after) / second;
                kMove = 1f - v0;
                kGrow = -(v1 - v0);
            } else {
                continue;
            }
            rows.add(sideways ? row(p, kMove, 0f, kGrow, 0f) : row(p, 0f, kMove, 0f, kGrow));
            ids.add(p.windowId);
        }
        return new GapPreview(rows, idsOf(ids), sideways, (cut + after) / 2f, minFirst - (cut - gap.start),
            (gap.end - after) - minSecond);
    }
}
