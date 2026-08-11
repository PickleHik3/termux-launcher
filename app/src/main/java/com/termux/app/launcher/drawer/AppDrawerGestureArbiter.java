package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * The one-way claim machine behind the app drawer's pull-down gesture.
 *
 * <p>Pure: no {@code View}, no {@code Context}, no touch objects. The dock's touch pipeline
 * ({@code SuggestionBarView.dispatchTouchEvent}) feeds it the down point captured at
 * {@code ACTION_DOWN}, the current move point, the platform touch slop and an
 * {@link Eligibility} snapshot; it answers which gesture owns the stream.
 *
 * <p>Two properties matter more than the thresholds themselves:
 *
 * <ul>
 *   <li><b>The drawer test runs first.</b> The dock's horizontal page swipe used to be recomputed
 *       on every move; a vertical pull that drifted sideways could flip ownership mid-gesture.
 *   <li><b>The latch is one-way.</b> Once a claim leaves {@link Claim#PENDING} it is never
 *       re-evaluated until {@link #begin} starts a new gesture. Re-evaluation is what lets a
 *       single drag fire both the page swipe and the drawer.
 * </ul>
 *
 * <p>The dominance ratios are deliberately asymmetric — 1.2 for the drawer against 1.1 for the
 * page — which leaves a neutral cone around the diagonal where neither test passes and the
 * gesture simply stays {@link Claim#PENDING}. Upward drags can never satisfy the drawer test
 * (it requires a positive {@code dy}), so the badged-icon swipe-up is untouched by construction.
 */
public final class AppDrawerGestureArbiter {

    /** Who owns the touch stream. */
    public enum Claim { PENDING, PAGE_SWIPE, DRAWER_DRAG, CHILD_OWNED }

    /** Vertical distance, as a multiple of touch slop, before the drawer may claim. */
    public static final float DRAWER_SLOP_FACTOR = 1.15f;
    /** How far the drag must be dominated by its vertical component for the drawer to claim. */
    public static final float DRAWER_DOMINANCE = 1.2f;
    /** How far the drag must be dominated by its horizontal component for the page to claim. */
    public static final float PAGE_DOMINANCE = 1.1f;

    /**
     * Everything the drawer claim depends on, sampled once at {@code ACTION_DOWN}.
     *
     * <p>Sampled rather than read live because half of these flip <em>because of</em> the gesture
     * in flight: dismissing a popup or beginning a pickup mid-drag would otherwise revoke the
     * claim under a finger that is already dragging the plane.
     */
    public static final class Eligibility {

        /** The {@code app_launcher_drawer_enabled} preference. */
        public final boolean drawerEnabled;
        /** The dock search field holds nothing but whitespace. */
        public final boolean searchEmpty;
        /** No A-Z letter is being scrubbed. */
        public final boolean azInactive;
        /** Portrait: the apps row is {@code GONE} in landscape, so there is nothing to pull from. */
        public final boolean portrait;
        /** Dock tuning mode is off — it owns drags on the dock itself. */
        public final boolean notDockTuning;
        /** The command palette is closed. */
        public final boolean paletteClosed;
        /** No long-press pickup and no pinned-icon drag in flight. */
        public final boolean noActivePickup;
        /** The drawer is neither open nor already animating. */
        public final boolean drawerIdle;

        public Eligibility(boolean drawerEnabled, boolean searchEmpty, boolean azInactive,
                           boolean portrait, boolean notDockTuning, boolean paletteClosed,
                           boolean noActivePickup, boolean drawerIdle) {
            this.drawerEnabled = drawerEnabled;
            this.searchEmpty = searchEmpty;
            this.azInactive = azInactive;
            this.portrait = portrait;
            this.notDockTuning = notDockTuning;
            this.paletteClosed = paletteClosed;
            this.noActivePickup = noActivePickup;
            this.drawerIdle = drawerIdle;
        }

        /** @return true when every veto is clear and the drawer may claim a vertical drag. */
        public boolean drawerEligible() {
            return drawerEnabled && searchEmpty && azInactive && portrait
                && notDockTuning && paletteClosed && noActivePickup && drawerIdle;
        }
    }

    /** Every veto set, used before the first {@link #begin} so a stray move can never claim. */
    private static final Eligibility INELIGIBLE =
        new Eligibility(false, false, false, false, false, false, false, false);

    private Claim mClaim = Claim.PENDING;
    @NonNull private Eligibility mEligibility = INELIGIBLE;
    private float mDownX;
    private float mDownY;

    /** Starts a fresh gesture at {@code ACTION_DOWN}, dropping any previous latch. */
    public void begin(float downX, float downY, @NonNull Eligibility eligibility) {
        mClaim = Claim.PENDING;
        mEligibility = eligibility;
        mDownX = downX;
        mDownY = downY;
    }

    /** Clears the latch at {@code ACTION_UP}/{@code ACTION_CANCEL}. */
    public void reset() {
        mClaim = Claim.PENDING;
        mEligibility = INELIGIBLE;
        mDownX = 0f;
        mDownY = 0f;
    }

    /** @return the current claim without evaluating anything. */
    @NonNull
    public Claim claim() {
        return mClaim;
    }

    /** @return true once something owns the stream, i.e. the claim can no longer change. */
    public boolean isLatched() {
        return mClaim != Claim.PENDING;
    }

    /** @return true when the drag currently belongs to the drawer. */
    public boolean isDrawerDrag() {
        return mClaim == Claim.DRAWER_DRAG;
    }

    /**
     * Latches {@link Claim#CHILD_OWNED} for the cases a child has already taken — a shown context
     * menu, a started notification swipe, a started pinned-icon drag. Honours the one-way latch,
     * so a child cannot steal a stream the drawer is already dragging.
     */
    @NonNull
    public Claim claimChild() {
        if (mClaim == Claim.PENDING) mClaim = Claim.CHILD_OWNED;
        return mClaim;
    }

    /**
     * Evaluates the move point against the snapshot taken at {@link #begin}.
     *
     * @param slopPx {@code ViewConfiguration.getScaledTouchSlop()}
     * @return the (possibly newly latched) claim
     */
    @NonNull
    public Claim evaluate(float x, float y, float slopPx) {
        if (mClaim != Claim.PENDING) return mClaim;

        float dx = x - mDownX;
        float dy = y - mDownY;
        float adx = Math.abs(dx);
        float ady = Math.abs(dy);

        // Drawer first, unconditionally: the page test must never get to claim a drag the drawer
        // would also have accepted.
        if (mEligibility.drawerEligible()
            && dy >= slopPx * DRAWER_SLOP_FACTOR
            && dy > adx * DRAWER_DOMINANCE) {
            mClaim = Claim.DRAWER_DRAG;
            return mClaim;
        }
        if (adx >= slopPx && adx > ady * PAGE_DOMINANCE) {
            mClaim = Claim.PAGE_SWIPE;
            return mClaim;
        }
        return mClaim;
    }
}
