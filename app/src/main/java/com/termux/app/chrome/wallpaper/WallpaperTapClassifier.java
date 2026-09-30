package com.termux.app.chrome.wallpaper;

/**
 * Decides whether a touch stream is a tap for the generated wallpaper's ripple: one finger, down
 * and up within the slop and the tap timeout, and only when the caller judged at DOWN that the
 * finger landed on bare wallpaper. Feed it every event; {@link #up} answers. Pure, no Android types.
 */
public final class WallpaperTapClassifier {

    private final float mSlopPx;
    private final long mTimeoutMs;
    private boolean mArmed;
    private float mDownX;
    private float mDownY;
    private long mDownTimeMs;

    public WallpaperTapClassifier(float slopPx, long timeoutMs) {
        mSlopPx = slopPx;
        mTimeoutMs = timeoutMs;
    }

    /** A first finger landed; {@code bare} is the caller's verdict on what is under it. */
    public void down(float x, float y, long timeMs, boolean bare) {
        mArmed = bare;
        mDownX = x;
        mDownY = y;
        mDownTimeMs = timeMs;
    }

    public void move(float x, float y) {
        if (!mArmed) return;
        float dx = x - mDownX;
        float dy = y - mDownY;
        if (dx * dx + dy * dy > mSlopPx * mSlopPx) mArmed = false;
    }

    /** A second finger, a cancel or anything else that makes the stream not a tap. */
    public void cancel() {
        mArmed = false;
    }

    /** The finger lifted; true exactly when the whole stream was a tap on bare wallpaper. */
    public boolean up(float x, float y, long timeMs) {
        move(x, y);
        boolean tap = mArmed && timeMs - mDownTimeMs <= mTimeoutMs;
        mArmed = false;
        return tap;
    }

    /** Where the last DOWN landed, for the moment's position. */
    public float downX() { return mDownX; }

    public float downY() { return mDownY; }
}
