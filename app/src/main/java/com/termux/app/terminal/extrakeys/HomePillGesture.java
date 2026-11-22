package com.termux.app.terminal.extrakeys;

/**
 * One touch stream on the Home pill: a hold inside the slop picks the pill up, a move after that
 * drags it, a release drops it. A move past the slop before the hold hands the stream back to the
 * keys. Pure; the view passes {@code ViewConfiguration}'s slop and long-press time and calls
 * {@link #holdElapsed} when its timer fires.
 */
public final class HomePillGesture {

    public enum Move { NONE, DRAG, CANCEL_HOLD }

    public enum Hold { NONE, PICK_UP }

    public enum Up { NONE, DROP }

    private enum State { IDLE, DOWN, PICKED_UP, HANDED_BACK }

    private final float mSlop;
    /** How long a press waits before it picks the pill up; the view arms its timer with this. */
    private final long mHoldMillis;
    private State mState = State.IDLE;
    private float mDownX;
    private float mDownY;
    private float mLastX;
    private float mLastY;
    private float mAnchorX;
    private float mAnchorY;
    private float mDx;
    private float mDy;

    public HomePillGesture(float slopPx, long holdMillis) {
        mSlop = slopPx;
        mHoldMillis = holdMillis;
    }

    public long holdMillis() {
        return mHoldMillis;
    }

    public void down(float x, float y) {
        mState = State.DOWN;
        mDownX = x;
        mDownY = y;
        mLastX = x;
        mLastY = y;
        mDx = 0f;
        mDy = 0f;
    }

    /**
     * {@link Move#DRAG} once picked up (read {@link #dragDx} and {@link #dragDy}); {@link
     * Move#CANCEL_HOLD} the one time a move leaves the slop before the hold, after which the
     * stream is the keys'.
     */
    public Move move(float x, float y) {
        mLastX = x;
        mLastY = y;
        switch (mState) {
            case DOWN:
                float dx = x - mDownX;
                float dy = y - mDownY;
                if (dx * dx + dy * dy > mSlop * mSlop) {
                    mState = State.HANDED_BACK;
                    return Move.CANCEL_HOLD;
                }
                return Move.NONE;
            case PICKED_UP:
                mDx = x - mAnchorX;
                mDy = y - mAnchorY;
                return Move.DRAG;
            default:
                return Move.NONE;
        }
    }

    /** The hold timer fired: pick up when the finger is still down and inside the slop. */
    public Hold holdElapsed() {
        if (mState != State.DOWN) return Hold.NONE;
        mState = State.PICKED_UP;
        mAnchorX = mLastX;
        mAnchorY = mLastY;
        mDx = 0f;
        mDy = 0f;
        return Hold.PICK_UP;
    }

    /** The finger lifted: {@link Up#DROP} when the pill was picked up. */
    public Up up() {
        boolean dropped = mState == State.PICKED_UP;
        mState = State.IDLE;
        return dropped ? Up.DROP : Up.NONE;
    }

    public void cancel() {
        mState = State.IDLE;
    }

    public boolean isPickedUp() {
        return mState == State.PICKED_UP;
    }

    /** The drag so far, from where the pill was picked up. */
    public float dragDx() {
        return mDx;
    }

    public float dragDy() {
        return mDy;
    }
}
