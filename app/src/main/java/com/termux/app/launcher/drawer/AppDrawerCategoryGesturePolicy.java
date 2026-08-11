package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/** Pure one-way arbitration for a DOWN-frozen category stream. */
public final class AppDrawerCategoryGesturePolicy {
    public enum Claim { PENDING, ACTION, SCROLL, COLLAPSE_DRAG, CLOSE_DRAG, OVERPULL }

    public static final class Down {
        @NonNull public final AppDrawerCategoryTouchRegions.Part part;
        public final boolean atTop;
        public final boolean scrollable;
        public final long armedAtMs;

        public Down(@NonNull AppDrawerCategoryTouchRegions.Part part, boolean atTop,
                    boolean scrollable, long armedAtMs) {
            this.part = part;
            this.atTop = atTop;
            this.scrollable = scrollable;
            this.armedAtMs = armedAtMs;
        }
    }

    private final AppDrawerCloseArmingPolicy overviewClose = new AppDrawerCloseArmingPolicy();
    @NonNull private Claim claim = Claim.PENDING;
    @NonNull private Down down = new Down(AppDrawerCategoryTouchRegions.Part.OUTSIDE,
        false, false, 0L);
    private boolean active;
    private boolean finalized;
    private boolean clickSuppressed;

    public void begin(@NonNull Down snapshot, long nowMs) {
        down = snapshot;
        claim = isAction(snapshot.part) ? Claim.ACTION : Claim.PENDING;
        active = true;
        finalized = false;
        clickSuppressed = false;
        if (isOverview(snapshot.part)) {
            overviewClose.begin(new AppDrawerCloseArmingPolicy.Down(true, snapshot.atTop,
                snapshot.scrollable), nowMs);
        }
    }

    @NonNull public Claim claimOnPreScroll(int dy) {
        if (!active) return Claim.SCROLL;
        if (claim == Claim.COLLAPSE_DRAG || claim == Claim.CLOSE_DRAG || claim == Claim.SCROLL)
            return claim;
        if (down.part == AppDrawerCategoryTouchRegions.Part.DETAIL_LIST) {
            if (!down.atTop || dy >= 0) return latch(Claim.SCROLL);
            return latch(Claim.COLLAPSE_DRAG);
        }
        if (!isOverview(down.part)) return claim;
        AppDrawerCloseArmingPolicy.Decision decision = overviewClose.claimOnPreScroll(dy);
        if (decision == AppDrawerCloseArmingPolicy.Decision.CLOSE_DRAG)
            return latch(Claim.CLOSE_DRAG);
        if (decision == AppDrawerCloseArmingPolicy.Decision.OVERPULL) {
            if (claim == Claim.ACTION) clickSuppressed = true;
            claim = Claim.OVERPULL;
            return claim;
        }
        return latch(Claim.SCROLL);
    }

    public boolean finishOverview(float overpullPx, float armPx, float velocity, boolean atTop,
                                  long nowMs) {
        if (!active || finalized) return overviewClose.isArmed();
        finalized = true;
        active = false;
        return overviewClose.end(overpullPx, armPx, velocity, atTop, nowMs);
    }

    public boolean finishOnce() {
        if (!active || finalized) return false;
        finalized = true;
        active = false;
        return true;
    }

    public void cancel() {
        active = false;
        finalized = true;
        claim = Claim.PENDING;
        clickSuppressed = true;
    }

    public void disarm() { overviewClose.disarm(); }
    public boolean isArmed() { return overviewClose.isArmed(); }
    public boolean suppressClick() { return clickSuppressed || claim == Claim.CLOSE_DRAG
        || claim == Claim.COLLAPSE_DRAG || claim == Claim.SCROLL || claim == Claim.OVERPULL; }
    @NonNull public Claim claim() { return claim; }
    @NonNull public Down down() { return down; }

    @NonNull private Claim latch(@NonNull Claim next) {
        claim = next;
        if (next != Claim.ACTION && next != Claim.PENDING) clickSuppressed = true;
        return claim;
    }

    private static boolean isAction(AppDrawerCategoryTouchRegions.Part part) {
        return part == AppDrawerCategoryTouchRegions.Part.EXPAND_ACTION
            || part == AppDrawerCategoryTouchRegions.Part.COLLAPSE_ACTION;
    }

    private static boolean isOverview(AppDrawerCategoryTouchRegions.Part part) {
        return part == AppDrawerCategoryTouchRegions.Part.OVERVIEW_LIST
            || part == AppDrawerCategoryTouchRegions.Part.EXPAND_ACTION;
    }
}
