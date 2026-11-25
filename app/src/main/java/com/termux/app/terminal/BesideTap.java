package com.termux.app.terminal;

/**
 * A tap on Home beside every float: a press that went down beside them and lifts where it went
 * down. A press that moves past the slop is a swipe (Home's pager turning the page, a drawer
 * pull), a second finger is a pinch, and a cancel is the wall taking the gesture: none of them
 * is a tap, so none gives focus back or puts the keyboard away. Only the lift decides, so a page
 * swipe that starts beside the floats leaves the focused float, and its keyboard, as they were.
 */
public final class BesideTap {

    private final float mSlopPx;
    private boolean mPending;
    private float mDownX;
    private float mDownY;

    public BesideTap(float slopPx) {
        mSlopPx = Math.max(0f, slopPx);
    }

    /** A press went down at ({@code x}, {@code y}); {@code beside} when it is on no float. */
    public void down(float x, float y, boolean beside) {
        mPending = beside;
        mDownX = x;
        mDownY = y;
    }

    /** The finger is at ({@code x}, {@code y}): past the slop the press is a swipe. */
    public void move(float x, float y) {
        if (!mPending) return;
        if (Math.abs(x - mDownX) > mSlopPx || Math.abs(y - mDownY) > mSlopPx) mPending = false;
    }

    /** A second finger came down, or the gesture was taken from Home: not a tap. */
    public void cancel() {
        mPending = false;
    }

    /** The finger lifted at ({@code x}, {@code y}): true when the press was a tap beside the floats. */
    public boolean up(float x, float y) {
        move(x, y);
        boolean tap = mPending;
        mPending = false;
        return tap;
    }

    /** Whether a press beside the floats is down and still a tap. */
    public boolean pending() {
        return mPending;
    }
}
