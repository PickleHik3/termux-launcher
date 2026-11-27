package com.termux.app.tiling;

/**
 * Tells a tap from a drag on the Workspaces' empty space (edit mode's Wallpaper and colours card
 * opens on a tap). It only watches: a finger that moves past the slop, stays down past the timeout
 * or is joined by a second one is no tap, so the desktop's gestures are never in its way.
 *
 * <p>Pure: no views. Used from the main thread.
 */
public final class WallpaperTap {

    private final float mSlopPx;
    private final long mTimeoutMs;
    private boolean mDown;
    private float mDownX;
    private float mDownY;
    private long mDownAt;

    public WallpaperTap(float slopPx, long timeoutMs) {
        mSlopPx = slopPx;
        mTimeoutMs = timeoutMs;
    }

    /** The first finger landed on empty space: a tap may follow. */
    public void down(float x, float y, long timeMs) {
        mDown = true;
        mDownX = x;
        mDownY = y;
        mDownAt = timeMs;
    }

    /** The finger moved: past the slop, it is a drag. */
    public void move(float x, float y) {
        if (!mDown) return;
        float dx = x - mDownX;
        float dy = y - mDownY;
        if (dx * dx + dy * dy > mSlopPx * mSlopPx) mDown = false;
    }

    /** Another finger landed, or the stream was cancelled: no tap. */
    public void cancel() {
        mDown = false;
    }

    /** The finger lifted: whether the stream was a tap. */
    public boolean up(float x, float y, long timeMs) {
        move(x, y);
        boolean tap = mDown && timeMs - mDownAt <= mTimeoutMs;
        mDown = false;
        return tap;
    }
}
