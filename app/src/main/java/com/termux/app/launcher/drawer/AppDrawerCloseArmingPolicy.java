package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * Scroll-versus-close arbitration for a touch stream that landed on the open drawer's grid.
 *
 * <p>Pure: no {@code View}, no {@code Context}, no {@code Handler}. The content view feeds it a
 * {@link Down} snapshot at {@code ACTION_DOWN} and the nested pre-scroll deltas as they arrive; it
 * answers who gets each delta.
 *
 * <p>The problem it exists to solve is that a downward drag on a scrollable grid is ambiguous —
 * it means "scroll up the list" until the list has no further up to give, at which point the same
 * motion has to start meaning "put the drawer away". Reading the two apart by watching the scroll
 * position live is what produces a drawer that closes itself at the end of an ordinary flick.
 * So the reading is deliberately conservative and depends on state sampled <em>before</em> the
 * gesture:
 *
 * <ul>
 *   <li><b>The drag that carries you to the top is never the drag that closes.</b> A stream that
 *       began mid-list scrolls for its whole life, however far past the top it is pulled. Only a
 *       second, deliberate pull can close.
 *   <li><b>A pull at the top must be paid for once before it counts.</b> The first one overpulls
 *       against a spring and, if it was committed enough — {@link #ARM_OVERPULL_DP} of travel or a
 *       downward fling — arms the next one. Arming expires, so a pull minutes later starts over.
 *   <li><b>Arming protects a scroll, so where there is no scroll there is nothing to protect.</b>
 *       A grid that cannot move (a short catalogue, a query filtered down to two results) and every
 *       piece of chrome around it close on the first pull, exactly as they did in B-1.
 * </ul>
 *
 * <p>The claim is one-way within a stream, like {@link AppDrawerGestureArbiter}'s: once a pre-scroll
 * has been answered with {@link Decision#CLOSE_DRAG} the rest of the stream drives the close, so the
 * {@code RecyclerView} never scrolls, is never told to disallow interception and never sees an
 * {@code ACTION_CANCEL}.
 */
public final class AppDrawerCloseArmingPolicy {

    /** What the parent does with one nested pre-scroll delta. */
    public enum Decision {
        /** The parent consumes the delta and drives the close transition. */
        CLOSE_DRAG,
        /** The child scrolls; the parent takes nothing and adds nothing. */
        SCROLL,
        /** The child scrolls first; whatever downward delta it leaves becomes damped overpull. */
        OVERPULL
    }

    /** Overpull a release must have reached, in dp, to arm the next pull. */
    public static final float ARM_OVERPULL_DP = 28f;
    /**
     * A downward fling at the top arms too, so a flick need not also be a long pull. Deliberately
     * the release policy's own threshold rather than a second number: "this was thrown, not
     * measured" means one thing across the whole drawer.
     */
    public static final float ARM_FLING_VELOCITY_PX_PER_SEC =
        AppDrawerCommitPolicy.FLING_VELOCITY_PX_PER_SEC;
    /** How long an armed grid stays armed, in ms. Checked at {@code ACTION_DOWN}, never on a timer. */
    public static final long ARM_WINDOW_MS = 1200L;

    /**
     * Everything the arbitration depends on, sampled once at {@code ACTION_DOWN}.
     *
     * <p>Sampled rather than read live for the same reason as B-1's eligibility snapshot, only more
     * so: {@code atTop} is the field the gesture itself changes, and re-reading it mid-stream is
     * precisely the bug — the finger that scrolls the list to its top would arrive at a grid that
     * now reports "at top" and close the drawer it was scrolling.
     */
    public static final class Down {

        /** The down point is over the grid rather than the pill, margins or bottom strip. */
        public final boolean overGrid;
        /** {@code !canScrollVertically(-1)} at the moment of the down. */
        public final boolean atTop;
        /** The grid can move at all — {@code canScrollVertically(-1) || canScrollVertically(1)}. */
        public final boolean scrollable;

        public Down(boolean overGrid, boolean atTop, boolean scrollable) {
            this.overGrid = overGrid;
            this.atTop = atTop;
            this.scrollable = scrollable;
        }

        /** @return true when the stream is a plain close drag because there is no scroll to protect. */
        public boolean behavesAsChrome() {
            return !overGrid || !scrollable;
        }
    }

    /**
     * Neutral snapshot held before the first {@link #begin} and again after {@link #end}: a stray
     * delta outside a gesture can only scroll, never close.
     */
    private static final Down MID_LIST = new Down(true, false, true);

    @NonNull private Down mDown = MID_LIST;
    private boolean mArmed;
    private long mArmedAtMs;
    private boolean mClosing;

    /**
     * Starts a fresh stream at {@code ACTION_DOWN} and expires a stale arming.
     *
     * <p>The window is checked here, against {@link #armedAtMs()}, rather than being cancelled by a
     * posted message — a timer would be the one piece of this class that needs a looper, and the
     * only moment the answer matters is this one.
     */
    public void begin(@NonNull Down down, long nowMs) {
        if (mArmed && nowMs - mArmedAtMs > ARM_WINDOW_MS) disarm();
        mDown = down;
        mClosing = false;
    }

    /**
     * Answers one {@code onNestedPreScroll} delta.
     *
     * @param dy the delta the child is about to consume, in scroll units: negative is a downward
     *           finger, i.e. a pull toward the top of the list
     */
    @NonNull
    public Decision claimOnPreScroll(int dy) {
        if (mClosing) return Decision.CLOSE_DRAG;

        // Chrome, and a grid that cannot scroll, keep the B-1 affordance: a pull closes.
        if (mDown.behavesAsChrome()) return claimClose();

        // A tap is a launch and an upward drag is a scroll; neither is a dismissal, and both spend
        // the arming so the pull after them starts from the top again.
        if (dy >= 0) {
            disarm();
            return Decision.SCROLL;
        }
        // Began mid-list: this stream scrolls, even once it reaches the top.
        if (!mDown.atTop) return Decision.SCROLL;
        if (mArmed) return claimClose();
        return Decision.OVERPULL;
    }

    /**
     * Settles the arming when a touch gesture ends, i.e. at {@code onStopNestedScroll(TYPE_TOUCH)}.
     *
     * @param overpullPx       how far past its top the grid was pulled at release
     * @param armOverpullPx    {@link #ARM_OVERPULL_DP} resolved to pixels by the caller
     * @param velocityPxPerSec release velocity, positive downwards
     * @param atTopAtEnd       {@code !canScrollVertically(-1)} at release
     * @return the armed state after this gesture; false is also how a gesture that scrolled away
     *         from the top, or one that closed the drawer, disarms
     */
    public boolean end(float overpullPx, float armOverpullPx, float velocityPxPerSec,
                       boolean atTopAtEnd, long nowMs) {
        boolean closed = mClosing;
        mClosing = false;
        mDown = MID_LIST;
        boolean arm = !closed && atTopAtEnd
            && (overpullPx >= armOverpullPx || velocityPxPerSec >= ARM_FLING_VELOCITY_PX_PER_SEC);
        if (arm) {
            mArmed = true;
            mArmedAtMs = nowMs;
        } else {
            disarm();
        }
        return mArmed;
    }

    /**
     * Forgets any arming. The caller drives this for everything that happens outside a nested
     * scroll — a tap, a query change, {@code beginDrag}, {@code close}, {@code onClosed}.
     */
    public void disarm() {
        mArmed = false;
        mArmedAtMs = 0L;
    }

    /** @return true when the next downward pull at the top would close the drawer. */
    public boolean isArmed() {
        return mArmed;
    }

    /** @return when the arming was recorded, for the window check at the next {@link #begin}. */
    public long armedAtMs() {
        return mArmedAtMs;
    }

    /** @return true once the current stream owns the close, i.e. the claim can no longer change. */
    public boolean isClosing() {
        return mClosing;
    }

    /**
     * Converts a nested-scroll fling velocity into the drawer's release convention.
     *
     * <p>{@code RecyclerView} hands the parent its fling in <em>scroll</em> units — a finger thrown
     * downwards scrolls the list toward its top and arrives as a negative {@code velocityY} — while
     * {@link AppDrawerCommitPolicy} and the controller's settle take velocity positive downwards,
     * with the finger. One negation, in one place: the same sign trap the closing settle already
     * carries a comment about, and getting it backwards launches the spring away from the target it
     * was just given.
     */
    public static float closeVelocityForNestedFling(float velocityY) {
        return -velocityY;
    }

    @NonNull
    private Decision claimClose() {
        mClosing = true;
        disarm();
        return Decision.CLOSE_DRAG;
    }
}
