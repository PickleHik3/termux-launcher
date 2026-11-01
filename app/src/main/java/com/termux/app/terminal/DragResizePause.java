package com.termux.app.terminal;

/**
 * When a finger drag of a split divider or a floating pane's corner resizes its panes (ADR 0019):
 * the grid is held while the finger moves, the held size is applied once the finger has stayed
 * within touch slop of one point for {@link #PAUSE_MS}, the next move holds again, and the lift
 * applies once more only if the size moved since the last pause.
 *
 * <p>The decision only; the caller owns the hold, the timer and the clock. Kitty's model: one
 * reflow per hesitation, none while the finger travels. Every other held transition (keyboard,
 * resize keys, focus grow) does not use this and keeps its single resize at the end.
 */
final class DragResizePause {

    /** How long the finger stays still before the panes resize. Not a setting. */
    static final long PAUSE_MS = 100L;

    /** {@link #onMove}: nothing to do — the finger is still within slop of where it stopped. */
    static final int MOVE_STILL = 0;
    /** {@link #onMove}: the finger moved on while held; re-arm the pause timer. */
    static final int MOVE_REARM = 1;
    /** {@link #onMove}: the finger moved on after a pause applied the size; hold again, re-arm. */
    static final int MOVE_HOLD_AGAIN = 2;

    private final float mSlop;
    private boolean mActive;
    private boolean mHeld;
    private float mAnchorX;
    private float mAnchorY;
    private long mStillSinceMs;

    DragResizePause(float touchSlopPx) {
        mSlop = Math.max(0f, touchSlopPx);
    }

    /** The drag began at ({@code x}, {@code y}) with its grid held. */
    void start(float x, float y, long nowMs) {
        mActive = true;
        mHeld = true;
        mAnchorX = x;
        mAnchorY = y;
        mStillSinceMs = nowMs;
    }

    /** One move of the finger; see the {@code MOVE_} results. */
    int onMove(float x, float y, long nowMs) {
        if (!mActive) return MOVE_STILL;
        float dx = x - mAnchorX;
        float dy = y - mAnchorY;
        if (dx * dx + dy * dy <= mSlop * mSlop) return MOVE_STILL;
        mAnchorX = x;
        mAnchorY = y;
        mStillSinceMs = nowMs;
        if (mHeld) return MOVE_REARM;
        mHeld = true;
        return MOVE_HOLD_AGAIN;
    }

    /** The finger has been still since when, for the timer's delay. */
    long stillSinceMs() {
        return mStillSinceMs;
    }

    /**
     * The pause timer fired: true once, when the grid is held and the finger has been still for
     * {@link #PAUSE_MS}; the caller then applies the held size (releases its hold).
     */
    boolean onTimer(long nowMs) {
        if (!mActive || !mHeld || nowMs - mStillSinceMs < PAUSE_MS) return false;
        mHeld = false;
        return true;
    }

    /**
     * The finger lifted: true when the size may have moved since the last pause (the grid is
     * held), so the caller releases its hold and the panes resize once more.
     */
    boolean onRelease() {
        boolean apply = mActive && mHeld;
        mActive = false;
        mHeld = false;
        return apply;
    }

    boolean isHeld() {
        return mActive && mHeld;
    }
}
