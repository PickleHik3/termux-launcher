package com.termux.app.statusbar;

import androidx.annotation.NonNull;

/** Pure one-way status gesture arbitration over one immutable DOWN snapshot. */
public final class StatusBarGesturePolicy {
    public enum Claim { PENDING, HORIZONTAL_SWIPE, LONG_PRESS, CHILD_OWNED, CANCELLED }

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
        public final int touchSlop;
        public final long timeoutToken;

        public Down(int pointerId, float rawX, float rawY, float localX, float localY,
                    long uptimeMillis, @NonNull TopStatusBarState state,
                    @NonNull TopStatusBarState normalTarget, boolean insideWindowBar,
                    boolean insideInteractiveChild, boolean nestedChildOwned,
                    boolean anotherSurfaceEngaged, int touchSlop, long timeoutToken) {
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
        claim = down.eligible() ? Claim.PENDING : Claim.CHILD_OWNED;
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
        if (ay > down.touchSlop && ay > ax) claim = Claim.CHILD_OWNED;
        else if (ax > down.touchSlop && ax > ay) {
            horizontalDelta = dx;
            claim = Claim.HORIZONTAL_SWIPE;
        }
        return claim;
    }

    @NonNull public Claim secondPointer() { return latch(Claim.CHILD_OWNED); }
    @NonNull public Claim nestedScrollStarted() { return latch(Claim.CHILD_OWNED); }
    @NonNull public Claim cancel() { return latch(Claim.CANCELLED); }

    @NonNull
    public Claim timeout(long token) {
        if (claim == Claim.PENDING && token == down.timeoutToken) claim = Claim.LONG_PRESS;
        return claim;
    }

    private Claim latch(Claim value) {
        if (claim == Claim.PENDING) claim = value;
        return claim;
    }
}
