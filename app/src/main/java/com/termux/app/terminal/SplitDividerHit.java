package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Which split's divider a finger is down on. The touch strip is the gap between two panes' rects,
 * the whole shared edge, and never any other part of a pane's content: a gap thinner than
 * {@link #MIN_TOUCH_DP} (Inner gap runs down to 0, two panes touching) still takes a strip that
 * thick, centred on the seam, out of the panes' edges either side of it.
 *
 * <p>Pure, in host pixels, so the rule can be tested without a view tree.
 */
final class SplitDividerHit {

    private SplitDividerHit() {}

    /** The least thickness of a divider's touch strip, in dp, however thin the gap it stands in. */
    static final float MIN_TOUCH_DP = 10f;

    /** One split's gap: {@code token} is whatever the caller wants back, normally the Split. */
    static final class Gap {
        @NonNull final Object token;
        /** Whether the strip runs top to bottom, so dragging it moves the seam along x. */
        final boolean dragsX;
        final float left, top, right, bottom;

        Gap(@NonNull Object token, boolean dragsX,
            float left, float top, float right, float bottom) {
            this.token = token;
            this.dragsX = dragsX;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        boolean contains(float x, float y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        /** Whether the point is within {@code thickness} centred on the seam, along its length. */
        boolean nearSeam(float x, float y, float thickness) {
            float half = thickness / 2f;
            if (dragsX) {
                float seam = (left + right) / 2f;
                return x >= seam - half && x < seam + half && y >= top && y < bottom;
            }
            float seam = (top + bottom) / 2f;
            return y >= seam - half && y < seam + half && x >= left && x < right;
        }
    }

    /** A pane's content rect. */
    static final class Pane {
        final float left, top, right, bottom;

        Pane(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        boolean contains(float x, float y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    /**
     * Every gap whose strip holds the point. A point on a pane's content is a gap's only within
     * {@code minTouchPx} centred on its seam; elsewhere the pane wins outright. More than one only
     * where two strips cross; {@link #pick} settles that.
     */
    @NonNull
    static List<Gap> gapsAt(@NonNull List<Gap> gaps, @NonNull List<Pane> panes,
                            float x, float y, float minTouchPx) {
        List<Gap> hit = new ArrayList<>(2);
        boolean onPane = false;
        for (Pane pane : panes) {
            if (pane.contains(x, y)) {
                onPane = true;
                break;
            }
        }
        for (Gap gap : gaps) {
            if ((!onPane && gap.contains(x, y)) || gap.nearSeam(x, y, minTouchPx)) hit.add(gap);
        }
        return hit;
    }

    /**
     * The gap a drag belongs to. A lone candidate is the answer. Where strips cross, the one the
     * finger's first movement runs across: a drag along x moves a seam that runs top to bottom,
     * a drag along y one that runs left to right, and an exact diagonal goes to the first listed.
     */
    @Nullable
    static Gap pick(@NonNull List<Gap> candidates, float dx, float dy) {
        if (candidates.isEmpty()) return null;
        boolean alongX = Math.abs(dx) >= Math.abs(dy);
        for (Gap gap : candidates) {
            if (gap.dragsX == alongX) return gap;
        }
        return candidates.get(0);
    }
}
