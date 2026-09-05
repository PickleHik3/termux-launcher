package com.termux.app.statusbar;

import androidx.annotation.NonNull;

/** Pure one-way status gesture arbitration over one immutable DOWN snapshot. */
public final class StatusBarGesturePolicy {
    public enum Claim {
        PENDING, WALL_HORIZONTAL, EXPAND_SWIPE, COLLAPSE_SWIPE, CHILD_OWNED, CANCELLED
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
         * A vertical drag from this point may change the pane's form. Unlike {@link #eligible()}
         * it survives an interactive child under the finger: the drag works along the bar's
         * entire length, window chips included. The layout computes it from the DOWN point and
         * the bar state.
         */
        public final boolean verticalEligible;
        /**
         * The pane wall has somewhere to go and this point may drag it there. A sideways drag on
         * the status bar means the wall and nothing else: the bar is the pager, and its own form
         * changes only by the vertical drag.
         */
        public final boolean wallEligible;
        public final int touchSlop;

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
                    boolean anotherSurfaceEngaged, boolean verticalEligible,
                    boolean wallEligible, int touchSlop) {
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
            this.verticalEligible = verticalEligible;
            this.wallEligible = wallEligible;
            this.touchSlop = Math.max(0, touchSlop);
        }

        public boolean eligible() {
            return !insideInteractiveChild && !nestedChildOwned && !anotherSurfaceEngaged;
        }
    }

    /**
     * With the wall in reach, the form toggle asks for a clearly vertical drag: twice the slop
     * of travel, at least twice as far up or down as sideways. A sideways swipe across the bar
     * often starts with a small curl, and a claim made on that first slop of vertical movement
     * folded the pane the user was only trying to slide.
     */
    static final float WALL_VERTICAL_SLOP_FACTOR = 2f;
    static final float WALL_VERTICAL_DOMINANCE = 2f;

    @NonNull private final Down down;
    @NonNull private Claim claim;
    private float horizontalDelta;

    public StatusBarGesturePolicy(@NonNull Down down) {
        this.down = down;
        claim = down.eligible() || down.verticalEligible || down.wallEligible
            ? Claim.PENDING : Claim.CHILD_OWNED;
    }

    @NonNull public Down down() { return down; }
    @NonNull public Claim claim() { return claim; }
    public float horizontalDelta() { return horizontalDelta; }

    @NonNull
    public Claim move(float localX, float localY) {
        if (claim != Claim.PENDING) return claim;
        float dx = localX - down.localX;
        float dy = localY - down.localY;
        float ax = Math.abs(dx);
        float ay = Math.abs(dy);
        boolean vertical = down.wallEligible
            ? ay > down.touchSlop * WALL_VERTICAL_SLOP_FACTOR && ay > ax * WALL_VERTICAL_DOMINANCE
            : ay > down.touchSlop && ay > ax;
        if (vertical) {
            // One vertical gesture with two directions: down expands the pane, up collapses it.
            // Both share the same vetoes, so both work along the bar's entire length.
            if (!down.verticalEligible) claim = Claim.CHILD_OWNED;
            else if (dy > 0f) {
                claim = down.state == TopStatusBarState.COMPACT
                    ? Claim.EXPAND_SWIPE : Claim.CHILD_OWNED;
            } else {
                claim = down.state == TopStatusBarState.EXPANDED
                    ? Claim.COLLAPSE_SWIPE : Claim.CHILD_OWNED;
            }
        } else if (ax > down.touchSlop && ax > ay) {
            // The wall takes the sideways drag wherever it has a place to go: moving between the
            // terminal and the pages beside it is what a horizontal swipe on the bar means. With
            // no wall — a terminal-only install — a sideways drag means nothing; it used to fold
            // and unfold the bar, and that older meaning surfacing under a wall drag is how a
            // place change kept undoing the form the user had chosen.
            if (down.wallEligible) {
                horizontalDelta = dx;
                claim = Claim.WALL_HORIZONTAL;
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
