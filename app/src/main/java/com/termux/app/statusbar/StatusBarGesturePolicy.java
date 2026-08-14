package com.termux.app.statusbar;

import androidx.annotation.NonNull;

/** Pure one-way status gesture arbitration over one immutable DOWN snapshot. */
public final class StatusBarGesturePolicy {
    public enum Claim {
        PENDING, HORIZONTAL_SWIPE, LONG_PRESS, PULL_DOWN, PULL_UP, COLLAPSE_SWIPE, CHILD_OWNED,
        CANCELLED
    }

    public static final class Down {
        public final int pointerId;
        public final float rawX;
        public final float rawY;
        public final float localX;
        public final float localY;
        public final long uptimeMillis;
        @NonNull public final TopStatusBarState state;
        @NonNull public final TopStatusBarState normalTarget;
        public final boolean insideWindowBar;
        public final boolean insideInteractiveChild;
        public final boolean nestedChildOwned;
        public final boolean anotherSurfaceEngaged;
        /**
         * The FULL-pane pull-down may claim this stream. Unlike {@link #eligible()} it survives an
         * interactive child under the finger: pulling down works along the bar's entire length,
         * window chips included. The layout computes it from the DOWN point and the bar state.
         */
        public final boolean pullDownEligible;
        /** FULL is open and this point may drag it closed (chrome/empty areas, not widgets). */
        public final boolean pullUpEligible;
        public final int touchSlop;
        public final long timeoutToken;

        public Down(int pointerId, float rawX, float rawY, float localX, float localY,
                    long uptimeMillis, @NonNull TopStatusBarState state,
                    @NonNull TopStatusBarState normalTarget, boolean insideWindowBar,
                    boolean insideInteractiveChild, boolean nestedChildOwned,
                    boolean anotherSurfaceEngaged, int touchSlop, long timeoutToken) {
            this(pointerId, rawX, rawY, localX, localY, uptimeMillis, state, normalTarget,
                insideWindowBar, insideInteractiveChild, nestedChildOwned, anotherSurfaceEngaged,
                false, false, touchSlop, timeoutToken);
        }

        public Down(int pointerId, float rawX, float rawY, float localX, float localY,
                    long uptimeMillis, @NonNull TopStatusBarState state,
                    @NonNull TopStatusBarState normalTarget, boolean insideWindowBar,
                    boolean insideInteractiveChild, boolean nestedChildOwned,
                    boolean anotherSurfaceEngaged, boolean pullDownEligible, int touchSlop,
                    long timeoutToken) {
            this(pointerId, rawX, rawY, localX, localY, uptimeMillis, state, normalTarget,
                insideWindowBar, insideInteractiveChild, nestedChildOwned, anotherSurfaceEngaged,
                pullDownEligible, false, touchSlop, timeoutToken);
        }

        public Down(int pointerId, float rawX, float rawY, float localX, float localY,
                    long uptimeMillis, @NonNull TopStatusBarState state,
                    @NonNull TopStatusBarState normalTarget, boolean insideWindowBar,
                    boolean insideInteractiveChild, boolean nestedChildOwned,
                    boolean anotherSurfaceEngaged, boolean pullDownEligible,
                    boolean pullUpEligible, int touchSlop, long timeoutToken) {
            this.pointerId = pointerId;
            this.rawX = rawX;
            this.rawY = rawY;
            this.localX = localX;
            this.localY = localY;
            this.uptimeMillis = uptimeMillis;
            this.state = state;
            this.normalTarget = normalTarget;
            this.insideWindowBar = insideWindowBar;
            this.insideInteractiveChild = insideInteractiveChild;
            this.nestedChildOwned = nestedChildOwned;
            this.anotherSurfaceEngaged = anotherSurfaceEngaged;
            this.pullDownEligible = pullDownEligible;
            this.pullUpEligible = pullUpEligible;
            this.touchSlop = Math.max(0, touchSlop);
            this.timeoutToken = timeoutToken;
        }

        public boolean eligible() {
            return state.allowsNormalSwipe() && !insideInteractiveChild
                && !nestedChildOwned && !anotherSurfaceEngaged;
        }
    }

    @NonNull private final Down down;
    @NonNull private Claim claim;
    private float horizontalDelta;

    public StatusBarGesturePolicy(@NonNull Down down) {
        this.down = down;
        claim = down.eligible() || down.pullDownEligible || down.pullUpEligible
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
        if (ay > down.touchSlop && ay > ax) {
            if (dy > 0f && down.pullDownEligible) claim = Claim.PULL_DOWN;
            else if (dy < 0f && down.pullUpEligible) claim = Claim.PULL_UP;
            // The unified vertical gesture's other direction: with the bar expanded, an upward
            // drag collapses it. Gated on the pull-down's eligibility so both directions work
            // along the bar's entire length and share the same vetoes (blocked, FULL, top slot).
            else if (dy < 0f && down.pullDownEligible
                && down.state == TopStatusBarState.EXPANDED) claim = Claim.COLLAPSE_SWIPE;
            else claim = Claim.CHILD_OWNED;
        } else if (ax > down.touchSlop && ax > ay) {
            // A stream that only pull-down could claim stays a child's for everything else.
            if (down.eligible()) {
                horizontalDelta = dx;
                claim = Claim.HORIZONTAL_SWIPE;
            } else {
                claim = Claim.CHILD_OWNED;
            }
        }
        return claim;
    }

    @NonNull public Claim secondPointer() { return latch(Claim.CHILD_OWNED); }
    @NonNull public Claim nestedScrollStarted() { return latch(Claim.CHILD_OWNED); }
    @NonNull public Claim cancel() { return latch(Claim.CANCELLED); }

    @NonNull
    public Claim timeout(long token) {
        // eligible() keeps long-press off interactive children: a chip hold is the chip's,
        // even though pull-down alone kept this stream PENDING.
        if (claim == Claim.PENDING && token == down.timeoutToken && down.eligible()) {
            claim = Claim.LONG_PRESS;
        }
        return claim;
    }

    private Claim latch(Claim value) {
        if (claim == Claim.PENDING) claim = value;
        return claim;
    }
}
