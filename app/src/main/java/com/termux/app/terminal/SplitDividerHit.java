package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Which split's divider a finger is down on. The touch strip is the gap between two panes' rects:
 * the whole shared edge, as wide as the gutter, and never any part of a pane's content, so a touch
 * on a terminal is always the terminal's however close to the seam it lands.
 *
 * <p>Pure, in host pixels, so the rule can be tested without a view tree.
 */
final class SplitDividerHit {

    private SplitDividerHit() {}

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
     * Every gap whose strip holds the point. Empty when the point is on any pane's content, which
     * wins outright. More than one only where two strips cross; {@link #pick} settles that.
     */
    @NonNull
    static List<Gap> gapsAt(@NonNull List<Gap> gaps, @NonNull List<Pane> panes,
                            float x, float y) {
        List<Gap> hit = new ArrayList<>(2);
        for (Pane pane : panes) {
            if (pane.contains(x, y)) return hit;
        }
        for (Gap gap : gaps) {
            if (gap.contains(x, y)) hit.add(gap);
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
