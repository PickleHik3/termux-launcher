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
 * gesture simply stays {@link Claim#PENDING}. Backwards drags can never satisfy the drawer test
 * (it requires travel along {@link Pull}'s own direction), so the badged-icon swipe-up is
 * untouched by construction.
 */
public final class AppDrawerGestureArbiter {

    /** Who owns the touch stream. */
    public enum Claim { PENDING, PAGE_SWIPE, DRAWER_DRAG, CHILD_OWNED }

    /**
     * Which way the drawer's pull travels off the dock — the dock's own geometry decides.
     *
     * <p>Landscape made this a direction rather than a flag: the pinned row becomes a vertical rail
     * there, so the portrait pull-down had no surface left to start from and the drawer was simply
     * unreachable. The rail's pull runs away from the edge it is docked to, and {@link #NONE} is
     * the "no surface to pull from" case that used to be the {@code portrait} veto.
     */
    public enum Pull { DOWN, RIGHT, LEFT, NONE }

    /** Distance along the pull's axis, as a multiple of touch slop, before the drawer may claim. */
    public static final float DRAWER_SLOP_FACTOR = 1.15f;
    /** How far the drag must be dominated by its pull-axis component for the drawer to claim. */
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
        /** Which way this dock's pull travels; {@link Pull#NONE} means nothing to pull from. */
        @NonNull public final Pull pull;
        /** The surface editor is closed — it owns drags on the dock itself while up. */
        public final boolean surfaceEditorClosed;
        /** The command palette is closed. */
        public final boolean paletteClosed;
        /** No long-press pickup and no pinned-icon drag in flight. */
        public final boolean noActivePickup;
        /** The drawer is neither open nor already animating. */
        public final boolean drawerIdle;
        /** The transient FULL status pane is neither open nor transitioning. */
        public final boolean fullStatusPaneClosed;

        /** The portrait dock: {@code portrait} false is the landscape row that is {@code GONE}. */
        public Eligibility(boolean drawerEnabled, boolean searchEmpty, boolean azInactive,
                           boolean portrait, boolean surfaceEditorClosed, boolean paletteClosed,
                           boolean noActivePickup, boolean drawerIdle) {
            this(drawerEnabled, searchEmpty, azInactive, portrait, surfaceEditorClosed, paletteClosed,
                noActivePickup, drawerIdle, true);
        }

        /** The portrait dock: {@code portrait} false is the landscape row that is {@code GONE}. */
        public Eligibility(boolean drawerEnabled, boolean searchEmpty, boolean azInactive,
                           boolean portrait, boolean surfaceEditorClosed, boolean paletteClosed,
                           boolean noActivePickup, boolean drawerIdle,
                           boolean fullStatusPaneClosed) {
            this(drawerEnabled, searchEmpty, azInactive, portrait ? Pull.DOWN : Pull.NONE,
                surfaceEditorClosed, paletteClosed, noActivePickup, drawerIdle, fullStatusPaneClosed);
        }

        public Eligibility(boolean drawerEnabled, boolean searchEmpty, boolean azInactive,
                           @NonNull Pull pull, boolean surfaceEditorClosed, boolean paletteClosed,
                           boolean noActivePickup, boolean drawerIdle,
                           boolean fullStatusPaneClosed) {
            this.drawerEnabled = drawerEnabled;
            this.searchEmpty = searchEmpty;
            this.azInactive = azInactive;
            this.pull = pull;
            this.surfaceEditorClosed = surfaceEditorClosed;
            this.paletteClosed = paletteClosed;
            this.noActivePickup = noActivePickup;
            this.drawerIdle = drawerIdle;
            this.fullStatusPaneClosed = fullStatusPaneClosed;
        }

        /** Every veto clear, for an already-open plane or its full-width pager. */
        @NonNull
        public static Eligibility allClear() {
            return new Eligibility(true, true, true, Pull.DOWN, true, true, true, true, true);
        }

        /** @return true when every veto is clear and the drawer may claim a drag along its pull. */
        public boolean drawerEligible() {
            return drawerEnabled && searchEmpty && azInactive && pull != Pull.NONE
                && surfaceEditorClosed && paletteClosed && noActivePickup && drawerIdle
                && fullStatusPaneClosed;
        }
    }

    /** Every veto set, used before the first {@link #begin} so a stray move can never claim. */
    private static final Eligibility INELIGIBLE =
        new Eligibility(false, false, false, Pull.NONE, false, false, false, false, false);

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
        if (mEligibility.drawerEligible()) {
            float along = travelAlongPull(mEligibility.pull, dx, dy);
            // The landscape rail scrolls vertically, so the cross axis is what keeps a scroll from
            // reading as a pull: only a drag dominated by the pull's own axis may claim.
            float across = mEligibility.pull == Pull.DOWN ? adx : ady;
            if (along >= slopPx * DRAWER_SLOP_FACTOR && along > across * DRAWER_DOMINANCE) {
                mClaim = Claim.DRAWER_DRAG;
                return mClaim;
            }
        }
        // The page swipe belongs to the portrait dock's pager. A horizontal pull is the landscape
        // rail, which has no pager and whose sideways axis is the pull's own: running this test
        // there would latch — and so deaden — every sideways drag that fell short of the drawer's
        // slightly longer threshold, which is most of a slow swipe.
        if (!isHorizontal(mEligibility.pull) && adx >= slopPx && adx > ady * PAGE_DOMINANCE) {
            mClaim = Claim.PAGE_SWIPE;
            return mClaim;
        }
        return mClaim;
    }

    /** @return true for the landscape rail's two pulls, whose axis is the page swipe's own. */
    public static boolean isHorizontal(@NonNull Pull pull) {
        return pull == Pull.RIGHT || pull == Pull.LEFT;
    }

    /**
     * Signed distance the drag has covered towards the open state. Negative for a drag running
     * back into the dock, which is how the badged-icon swipe-up and a rail flick towards its own
     * edge stay unclaimable.
     */
    public static float travelAlongPull(@NonNull Pull pull, float dx, float dy) {
        switch (pull) {
            case DOWN: return dy;
            case RIGHT: return dx;
            case LEFT: return -dx;
            case NONE:
            default: return 0f;
        }
    }
}
