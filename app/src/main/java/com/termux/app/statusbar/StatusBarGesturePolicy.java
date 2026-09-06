package com.termux.app.statusbar;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * Pure one-way status gesture arbitration over one immutable DOWN snapshot.
 *
 * <p>Two gestures share the bar and they are always perpendicular: one drags the pane wall along
 * the bar's own length, the other folds and unfolds the bar across it. Which screen axis each of
 * those is follows the edge the bar stands on — a bar along the top or the bottom pages sideways
 * and folds vertically; a bar down the left or the right pages up and down and folds sideways.
 * Everything below is written in those two axes, so the arbitration is one rule rather than four.
 */
public final class StatusBarGesturePolicy {
    public enum Claim {
        PENDING, WALL_PAGING, EXPAND_SWIPE, COLLAPSE_SWIPE, CHILD_OWNED, CANCELLED
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
         * it survives an interactive child under the finger: the drag works along the bar's
         * entire length, window chips included. The layout computes it from the DOWN point and
         * the bar state.
         */
        public final boolean formEligible;
        /**
         * The pane wall has somewhere to go and this point may drag it there. A drag along the
         * bar means the wall and nothing else: the bar is the pager, and its own form changes
         * only by the drag across it.
         */
        public final boolean wallEligible;
        public final int touchSlop;
        /** The edge the bar stands on, which is what decides the two axes. */
        @NonNull public final Edge edge;

        /** The shape every stream starts from, with neither gesture armed. */
        public Down(int pointerId, float rawX, float rawY, float localX, float localY,
                    long uptimeMillis, @NonNull TopStatusBarState state,
                    boolean insideWindowBar,
                    boolean insideInteractiveChild, boolean nestedChildOwned,
                    boolean anotherSurfaceEngaged, int touchSlop) {
            this(pointerId, rawX, rawY, localX, localY, uptimeMillis, state,
                insideWindowBar, insideInteractiveChild, nestedChildOwned, anotherSurfaceEngaged,
                false, false, touchSlop);
        }

        public Down(int pointerId, float rawX, float rawY, float localX, float localY,
                    long uptimeMillis, @NonNull TopStatusBarState state,
                    boolean insideWindowBar,
                    boolean insideInteractiveChild, boolean nestedChildOwned,
                    boolean anotherSurfaceEngaged, boolean formEligible,
                    boolean wallEligible, int touchSlop) {
            this(pointerId, rawX, rawY, localX, localY, uptimeMillis, state, insideWindowBar,
                insideInteractiveChild, nestedChildOwned, anotherSurfaceEngaged, formEligible,
                wallEligible, touchSlop, Edge.TOP);
        }

        public Down(int pointerId, float rawX, float rawY, float localX, float localY,
                    long uptimeMillis, @NonNull TopStatusBarState state,
                    boolean insideWindowBar,
                    boolean insideInteractiveChild, boolean nestedChildOwned,
                    boolean anotherSurfaceEngaged, boolean formEligible,
                    boolean wallEligible, int touchSlop, @NonNull Edge edge) {
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
            this.wallEligible = wallEligible;
            this.touchSlop = Math.max(0, touchSlop);
            this.edge = edge;
        }

        public boolean eligible() {
            return !insideInteractiveChild && !nestedChildOwned && !anotherSurfaceEngaged;
        }
    }

    /**
     * With the wall in reach, the form toggle asks for a clearly perpendicular drag: twice the
     * slop of travel, at least twice as far across the bar as along it. A swipe along the bar
     * often starts with a small curl, and a claim made on that first slop of movement folded the
     * pane the user was only trying to slide.
     */
    static final float WALL_ACROSS_SLOP_FACTOR = 2f;
    static final float WALL_ACROSS_DOMINANCE = 2f;

    @NonNull private final Down down;
    @NonNull private Claim claim;
    private float pagingDelta;

    public StatusBarGesturePolicy(@NonNull Down down) {
        this.down = down;
        claim = down.eligible() || down.formEligible || down.wallEligible
            ? Claim.PENDING : Claim.CHILD_OWNED;
    }

    @NonNull public Down down() { return down; }
    @NonNull public Claim claim() { return claim; }

    /** Finger travel along the bar since the DOWN — the distance the wall is dragged by. */
    public float pagingDelta() { return pagingDelta; }

    /** Whether the bar stands in a column rather than a row, and pages along the screen's height. */
    public static boolean isVertical(@NonNull Edge edge) {
        return edge == Edge.LEFT || edge == Edge.RIGHT;
    }

    /** Finger travel along the bar: sideways for a row, up and down for a column. */
    public static float alongAxis(@NonNull Edge edge, float dx, float dy) {
        return isVertical(edge) ? dy : dx;
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
        float along = alongAxis(down.edge, dx, dy);
        float across = acrossAxis(down.edge, dx, dy);
        float aAlong = Math.abs(along);
        float aAcross = Math.abs(across);
        boolean formGesture = down.wallEligible
            ? aAcross > down.touchSlop * WALL_ACROSS_SLOP_FACTOR
                && aAcross > aAlong * WALL_ACROSS_DOMINANCE
            : aAcross > down.touchSlop && aAcross > aAlong;
        if (formGesture) {
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
            // The wall takes the drag along the bar wherever it has a place to go: moving between
            // the terminal and the places beside it is what a swipe along the bar means. With no
            // wall — a terminal-only install — it means nothing; it used to fold and unfold the
            // bar, and that older meaning surfacing under a wall drag is how a place change kept
            // undoing the form the user had chosen.
            if (down.wallEligible) {
                pagingDelta = along;
                claim = Claim.WALL_PAGING;
            } else {
                claim = Claim.CHILD_OWNED;
            }
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
