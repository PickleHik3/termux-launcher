package com.termux.app.terminal;

import androidx.annotation.NonNull;

/**
 * Holds the panes' grid from the moment the launcher leaves the screen until the layout it comes
 * back to has settled, so the shell hears at most one size for the whole round trip.
 *
 * <p>Coming back from another app is a burst of geometry: the window's insets (the root fits
 * system windows), the window frame, the fullscreen flags re-applied on focus, the in-app
 * keyboard re-measuring against a root whose height is itself still moving, and the panes'
 * re-measure posted from onResume and onWindowFocusChanged. Each of those used to reach the PTY on
 * its own, unanchored, and an inline TUI (Claude Code, anything on Ink) would redraw for one size
 * while the grid already stood at the next — its prompt left at the top with blank rows under it.
 * In the usual case the size the window settles on is the size it left with, and then the shell
 * should hear nothing at all.
 *
 * <p>So the hold opens when the activity stops (the window is off screen: nothing the grid could
 * show is lost by not following it) and is let go once the activity is resumed, has window focus
 * and the layout facts the host reports have stayed the same for {@link #STABLE_FRAMES} frames in
 * a row with no layout pending. Two backstops make sure it can never stick: {@link
 * #FOCUS_BACKSTOP_MS} after focus, and {@link #UNFOCUSED_BACKSTOP_MS} after a resume that never
 * gains focus (a multi-window peer, a dialog over us).
 *
 * <p>Pure: the clock and the frames are the caller's, so the decision is provable on a fake
 * ({@code ReturnResizeHoldTest}).
 */
public final class ReturnResizeHold {

    /** The window-side half: the panes' pause itself. */
    public interface Host {

        /** Pauses every pane's grid sizing; sizes arriving meanwhile are kept as pending. */
        void beginHold();

        /** Resumes it, each visible pane sending its one size anchored at the bottom edge. */
        void finishHold(@NonNull String reason);
    }

    /** Frames in a row whose layout facts equal the one before, before the hold is let go. */
    static final int STABLE_FRAMES = 2;

    /** Longest the hold outlives window focus: the burst a return brings is over well before. */
    static final long FOCUS_BACKSTOP_MS = 600L;

    /** Longest it outlives a resume that never gains focus. */
    static final long UNFOCUSED_BACKSTOP_MS = 1500L;

    public static final String REASON_SETTLED = "settled";
    public static final String REASON_BACKSTOP = "backstop";
    public static final String REASON_BACKSTOP_UNFOCUSED = "backstop-unfocused";

    @NonNull private final Host mHost;

    private boolean mActive;
    private boolean mResumed;
    private boolean mFocused;
    private long mResumedAtMs;
    private long mFocusedAtMs;
    private boolean mHasKey;
    private long mLastKey;
    private int mStableFrames;

    public ReturnResizeHold(@NonNull Host host) {
        mHost = host;
    }

    /** The activity left the screen. False when a hold was already open. */
    public boolean begin() {
        if (mActive) return false;
        mActive = true;
        // A stop is always followed by a fresh resume and a fresh focus before the hold can go.
        mResumed = false;
        mFocused = false;
        resetStability();
        mHost.beginHold();
        return true;
    }

    public void onResumed(long nowMs) {
        mResumed = true;
        mResumedAtMs = nowMs;
        resetStability();
    }

    public void onPaused() {
        mResumed = false;
        mFocused = false;
        resetStability();
    }

    public void onFocusChanged(boolean focused, long nowMs) {
        if (focused == mFocused) return;
        mFocused = focused;
        if (focused) mFocusedAtMs = nowMs;
        // applyFullscreenMode runs on focus and moves the insets: what was steady before it is not
        // evidence the layout after it will be.
        resetStability();
    }

    /** True while frames should be fed to {@link #onFrame}. */
    public boolean wantsFrames() {
        return mActive && mResumed;
    }

    /**
     * One frame of the returning window.
     *
     * @param layoutKey     a digest of the layout facts the panes' size follows (pane host size,
     *                      keyboard and accessory height, the geometry pass's own inputs)
     * @param layoutPending a layout has been requested in that subtree and has not run yet
     * @return whether another frame is wanted
     */
    public boolean onFrame(long layoutKey, boolean layoutPending, long nowMs) {
        if (!wantsFrames()) return false;
        if (!mFocused) {
            if (nowMs - mResumedAtMs >= UNFOCUSED_BACKSTOP_MS) {
                release(REASON_BACKSTOP_UNFOCUSED);
                return false;
            }
            return true;
        }
        if (nowMs - mFocusedAtMs >= FOCUS_BACKSTOP_MS) {
            release(REASON_BACKSTOP);
            return false;
        }
        if (layoutPending) {
            resetStability();
            return true;
        }
        if (!mHasKey || layoutKey != mLastKey) {
            mHasKey = true;
            mLastKey = layoutKey;
            mStableFrames = 0;
            return true;
        }
        if (++mStableFrames >= STABLE_FRAMES) {
            release(REASON_SETTLED);
            return false;
        }
        return true;
    }

    /** The window is going away: the hold is dropped, not resumed; the panes go with it. */
    public void cancel() {
        mActive = false;
        mResumed = false;
        mFocused = false;
        resetStability();
    }

    public boolean isActive() {
        return mActive;
    }

    private void release(@NonNull String reason) {
        if (!mActive) return;
        mActive = false;
        resetStability();
        mHost.finishHold(reason);
    }

    private void resetStability() {
        mHasKey = false;
        mStableFrames = 0;
    }
}
