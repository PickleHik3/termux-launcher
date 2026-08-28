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
 * motion has to start meaning "put the drawer away". This is the model every shipped launcher
 * (Launcher3, Nova, Lawnchair) and {@code BottomSheetBehavior} converge on, and it is the one
 * implemented here:
 *
 * <ul>
 *   <li><b>A pull that begins at the top closes, in one gesture.</b> The close is a finger-tracked
 *       drag, not a fire-and-forget dismissal: {@link AppDrawerCommitPolicy} only commits a release
 *       past half the travel or a real downward fling, so a timid or accidental pull springs the
 *       drawer back open. That commit gate — not a second swipe — is the accident protection.
 *   <li><b>The drag that carries you to the top is never the drag that closes.</b> A stream that
 *       began mid-list, or that ever scrolled (an upward delta means the list left its top),
 *       scrolls for its whole life, however far past the top it is pulled — the leftover travel
 *       becomes damped overpull. Only the next, deliberate pull can close.
 * </ul>
 *
 * <p>An earlier revision additionally demanded that a first at-top pull merely "arm" a second one
 * within a time window — every intentional close cost two swipes. That doubled the mid-list guard
 * it was meant to back up and read as the drawer refusing the gesture, so it is gone.
 *
 * <p>The claim is one-way within a stream, like {@link AppDrawerGestureArbiter}'s, in both
 * directions: once a pre-scroll has been answered with {@link Decision#CLOSE_DRAG} the rest of the
 * stream drives the close, so the {@code RecyclerView} never scrolls, is never told to disallow
 * interception and never sees an {@code ACTION_CANCEL}; and once a stream has scrolled it can only
 * scroll, so a wiggle that leaves the top and comes back cannot throw the drawer away mid-list.
 */
public final class AppDrawerCloseArmingPolicy {

    /** What the parent does with one nested pre-scroll delta. */
    public enum Decision {
        /** The parent consumes the delta and drives the close transition. */
        CLOSE_DRAG,
        /** The child scrolls; the parent takes nothing and adds nothing. */
        SCROLL,
        /**
         * Retired: no claim answers this any more. A scrolling stream's leftover downward travel
         * still rubber-bands, but through the unconsumed-scroll path, not through a claim.
         */
        OVERPULL
    }

    /**
     * Everything the arbitration depends on, sampled once at {@code ACTION_DOWN}.
     *
     * <p>Sampled rather than read live because {@code atTop} is the field the gesture itself
     * changes, and re-reading it mid-stream is precisely the bug — the finger that scrolls the
     * list to its top would arrive at a grid that now reports "at top" and close the drawer it
     * was scrolling. The in-stream reversal case is covered by the one-way scroll latch instead.
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
    private boolean mClosing;
    /** One-way within a stream: a gesture that has scrolled the list can never become a close. */
    private boolean mScrollLatched;

    /** Starts a fresh stream at {@code ACTION_DOWN}. */
    public void begin(@NonNull Down down, long nowMs) {
        mDown = down;
        mClosing = false;
        mScrollLatched = false;
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

        // Chrome, and a grid that cannot scroll, have no scroll to protect: a pull closes.
        if (mDown.behavesAsChrome()) return claimClose();

        if (mScrollLatched) return Decision.SCROLL;
        // An upward delta means the list is leaving its top — the snapshot's atTop is stale for
        // the rest of this stream, so the stream is a scroll for life. A mid-list stream likewise.
        if (dy >= 0 || !mDown.atTop) {
            mScrollLatched = true;
            return Decision.SCROLL;
        }
        // At the top, first movement downward: the drawer leaves with the finger. The commit
        // policy at release is what separates a close from a changed mind.
        return claimClose();
    }

    /**
     * Settles the stream when a touch gesture ends, i.e. at {@code onStopNestedScroll(TYPE_TOUCH)}.
     * Parameters are kept for the call sites; nothing is armed any more.
     *
     * @return false, always — closing needs no prior arming
     */
    public boolean end(float overpullPx, float armOverpullPx, float velocityPxPerSec,
                       boolean atTopAtEnd, long nowMs) {
        mClosing = false;
        mScrollLatched = false;
        mDown = MID_LIST;
        return false;
    }

    /** Retired arming hook, kept for its call sites: there is no armed state to forget. */
    public void disarm() {
    }

    /** @return false, always: a pull at the top closes without prior arming. */
    public boolean isArmed() {
        return false;
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
        return Decision.CLOSE_DRAG;
    }
}
