package com.termux.app.statusbar;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * Pure one-way status gesture arbitration over one immutable DOWN snapshot.
 *
 * <p>One gesture is the bar's own: the drag across it, which folds and unfolds it. Which screen
 * axis that is follows the edge the bar stands on — a bar along the top or the bottom folds
 * vertically, a bar down the left or the right folds sideways — so everything below is written
 * in the bar's two axes, along and across, and the arbitration is one rule rather than four. A
 * drag along the bar is nobody's here: the wall is paged by the pane's own border drag
 * ({@code wall/BorderDrag}), never from the bar.
 */
public final class StatusBarGesturePolicy {
    public enum Claim {
        PENDING, EXPAND_SWIPE, COLLAPSE_SWIPE, CHILD_OWNED, CANCELLED
    }

    public static final class Down {
        public final int pointerId;
        public final float rawX;
        public final float rawY;
        public final float localX;
        public final float localY;
        public final long uptimeMillis;
        @NonNull public final TopStatusBarState state;
        public final boolean insideWindowBar;
        public final boolean insideInteractiveChild;
        public final boolean nestedChildOwned;
        public final boolean anotherSurfaceEngaged;
        /**
         * A drag across the bar from this point may change its form. Unlike {@link #eligible()}
         * it survives an interactive child under the finger: the drag works from anywhere on the
         * bar, folded or open, window chips and the clock included, and only a child that answers
         * drags on the fold's own axis takes it away. The layout computes it from the DOWN point.
         */
        public final boolean formEligible;
        public final int touchSlop;
        /** The edge the bar stands on, which is what decides the two axes. */
        @NonNull public final Edge edge;

        /** The shape every stream starts from, with the fold not armed. */
        public Down(int pointerId, float rawX, float rawY, float localX, float localY,
                    long uptimeMillis, @NonNull TopStatusBarState state,
                    boolean insideWindowBar,
                    boolean insideInteractiveChild, boolean nestedChildOwned,
                    boolean anotherSurfaceEngaged, int touchSlop) {
            this(pointerId, rawX, rawY, localX, localY, uptimeMillis, state,
                insideWindowBar, insideInteractiveChild, nestedChildOwned, anotherSurfaceEngaged,
                false, touchSlop);
        }

        public Down(int pointerId, float rawX, float rawY, float localX, float localY,
                    long uptimeMillis, @NonNull TopStatusBarState state,
                    boolean insideWindowBar,
                    boolean insideInteractiveChild, boolean nestedChildOwned,
                    boolean anotherSurfaceEngaged, boolean formEligible, int touchSlop) {
            this(pointerId, rawX, rawY, localX, localY, uptimeMillis, state, insideWindowBar,
                insideInteractiveChild, nestedChildOwned, anotherSurfaceEngaged, formEligible,
                touchSlop, Edge.TOP);
        }

        public Down(int pointerId, float rawX, float rawY, float localX, float localY,
                    long uptimeMillis, @NonNull TopStatusBarState state,
                    boolean insideWindowBar,
                    boolean insideInteractiveChild, boolean nestedChildOwned,
                    boolean anotherSurfaceEngaged, boolean formEligible, int touchSlop,
                    @NonNull Edge edge) {
            this.pointerId = pointerId;
            this.rawX = rawX;
            this.rawY = rawY;
            this.localX = localX;
            this.localY = localY;
            this.uptimeMillis = uptimeMillis;
            this.state = state;
            this.insideWindowBar = insideWindowBar;
            this.insideInteractiveChild = insideInteractiveChild;
            this.nestedChildOwned = nestedChildOwned;
            this.anotherSurfaceEngaged = anotherSurfaceEngaged;
            this.formEligible = formEligible;
            this.touchSlop = Math.max(0, touchSlop);
            this.edge = edge;
        }

        public boolean eligible() {
            return !insideInteractiveChild && !nestedChildOwned && !anotherSurfaceEngaged;
        }
    }

    @NonNull private final Down down;
    @NonNull private Claim claim;

    public StatusBarGesturePolicy(@NonNull Down down) {
        this.down = down;
        claim = down.eligible() || down.formEligible ? Claim.PENDING : Claim.CHILD_OWNED;
    }

    @NonNull public Down down() { return down; }
    @NonNull public Claim claim() { return claim; }

    /** Whether the bar stands in a column rather than a row. */
    public static boolean isVertical(@NonNull Edge edge) {
        return edge == Edge.LEFT || edge == Edge.RIGHT;
    }

    /**
     * Whether the bar standing on this edge may ever rest expanded. A bar down a side is too
     * narrow to grow into a panel the way one along the top or the bottom does, so it stays
     * compact regardless of what the place remembers — the memory itself is untouched, it just
     * has nothing to apply while the bar stands here.
     */
    public static boolean expansionAllowed(@NonNull Edge edge) {
        return !isVertical(edge);
    }

    /** Finger travel across the bar, which is the axis its form changes on. */
    public static float acrossAxis(@NonNull Edge edge, float dx, float dy) {
        return isVertical(edge) ? dx : dy;
    }

    /**
     * Which way across the bar unfolds it: away from the edge it stands on. A bar along the top
     * grows downward, one along the bottom upward, one down the left rightward, one down the
     * right leftward.
     */
    public static float expandSign(@NonNull Edge edge) {
        return edge == Edge.BOTTOM || edge == Edge.RIGHT ? -1f : 1f;
    }

    @NonNull
    public Claim move(float localX, float localY) {
        if (claim != Claim.PENDING) return claim;
        float dx = localX - down.localX;
        float dy = localY - down.localY;
        float along = isVertical(down.edge) ? dy : dx;
        float across = acrossAxis(down.edge, dx, dy);
        float aAlong = Math.abs(along);
        float aAcross = Math.abs(across);
        if (aAcross > down.touchSlop && aAcross > aAlong) {
            // One gesture across the bar with two directions: away from the bar's edge opens it,
            // back towards the edge folds it. Both share the same vetoes, so both work along the
            // bar's entire length.
            if (!down.formEligible) claim = Claim.CHILD_OWNED;
            else if (across * expandSign(down.edge) > 0f) {
                claim = down.state == TopStatusBarState.COMPACT
                    ? Claim.EXPAND_SWIPE : Claim.CHILD_OWNED;
            } else {
                claim = down.state == TopStatusBarState.EXPANDED
                    ? Claim.COLLAPSE_SWIPE : Claim.CHILD_OWNED;
            }
        } else if (aAlong > down.touchSlop && aAlong > aAcross) {
            // A drag along the bar is the chip strip's to scroll, or nobody's: the bar does not
            // page the wall.
            claim = Claim.CHILD_OWNED;
        }
        return claim;
    }

    @NonNull public Claim secondPointer() { return latch(Claim.CHILD_OWNED); }
    @NonNull public Claim nestedScrollStarted() { return latch(Claim.CHILD_OWNED); }
    @NonNull public Claim cancel() { return latch(Claim.CANCELLED); }

    private Claim latch(Claim value) {
        if (claim == Claim.PENDING) claim = value;
        return claim;
    }
}
