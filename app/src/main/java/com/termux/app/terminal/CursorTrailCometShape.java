package com.termux.app.terminal;

/**
 * Where the Comet trail is at a given moment, in the overlay's y-down pixels. Pure JVM, no
 * Android types, so it is unit tested directly; {@link CursorTrailComet} only paints it.
 *
 * <p>The Comet is this app's own style, drawn in kitty's language: a flat fill of the trail colour
 * with crisp edges that never paints over the cursor. When a move starts, a streak as wide as the
 * cursor reaches from the cursor all the way back to where it came from. Its head sits on the
 * cursor's trailing edge, the edge facing the old position, so the brightest part shows right
 * behind the cursor instead of under it; its tail tapers to a point and fades out toward the old
 * position. Over {@link #DURATION_MS} the tail draws in toward the head while the whole streak
 * fades, so it reads as the cursor having shot across with its trail catching up.
 */
public final class CursorTrailCometShape {

    /** How long one comet shows, as kitty's own blaze trail (cursor-trail-blaze.slang). */
    public static final long DURATION_MS = 500L;
    /** Steps along each side of the taper; enough for a smooth curve at any tail length. */
    static final int TAPER_STEPS = 8;
    /** Outline points: two inside the cursor, one per taper step on each side, and the tip. */
    public static final int MAX_POINTS = 2 + 2 * TAPER_STEPS + 1;
    /** How round the taper is: 1 is a straight wedge, smaller bulges the tail near the head. */
    private static final float TAPER_POWER = 0.6f;
    /** The thinnest streak, as a fraction of the cursor's larger side, for beam and underline. */
    private static final float MIN_WIDTH_FRACTION = 0.35f;
    /** The head's glow radius, in streak half-widths. */
    static final float HEAD_GLOW_RADIUS = 1.6f;
    /** Moves shorter than this, in pixels beyond the cursor's own edge, show nothing. */
    private static final float MIN_LENGTH_PX = 1f;

    private boolean mActive;
    private long mStartMs;
    private float mCenterX, mCenterY;
    private float mDirX, mDirY;
    private float mHeadX, mHeadY;
    private float mHalfWidth;
    private float mFullLength;
    private float mBoundsLeft, mBoundsTop, mBoundsRight, mBoundsBottom;

    // The latest layout.
    private final float[] mOutline = new float[MAX_POINTS * 2];
    private int mPointCount;
    private float mTailX, mTailY;
    private float mLength;
    private float mStrength;

    public void reset() {
        mActive = false;
    }

    /**
     * A move the trail accepted, made at {@code nowMs}: the cursor's rect before and after it.
     * Starts a new comet in place of any still showing.
     */
    public void start(float fromL, float fromT, float fromR, float fromB,
                      float toL, float toT, float toR, float toB, long nowMs) {
        float fromX = (fromL + fromR) * 0.5f, fromY = (fromT + fromB) * 0.5f;
        float cx = (toL + toR) * 0.5f, cy = (toT + toB) * 0.5f;
        float pathX = fromX - cx, pathY = fromY - cy;
        float pathLen = (float) Math.sqrt((double) pathX * pathX + (double) pathY * pathY);
        mActive = false;
        if (pathLen <= 0f) return;
        // Unit vector of travel, from the old position to the cursor.
        float dirX = -pathX / pathLen, dirY = -pathY / pathLen;
        float halfW = (toR - toL) * 0.5f, halfH = (toB - toT) * 0.5f;
        // How far from the cursor's centre the path back to the old position leaves its rect.
        float exit = Float.MAX_VALUE;
        if (Math.abs(dirX) > 1e-6f) exit = Math.min(exit, halfW / Math.abs(dirX));
        if (Math.abs(dirY) > 1e-6f) exit = Math.min(exit, halfH / Math.abs(dirY));
        float fullLength = pathLen - exit;
        if (fullLength < MIN_LENGTH_PX) return;
        // As wide as the cursor is across the direction of travel.
        float across = Math.abs(dirY) * (toR - toL) + Math.abs(dirX) * (toB - toT);
        float larger = Math.max(toR - toL, toB - toT);
        mHalfWidth = 0.5f * Math.max(across, MIN_WIDTH_FRACTION * larger);
        mActive = true;
        mStartMs = nowMs;
        mCenterX = cx;
        mCenterY = cy;
        mDirX = dirX;
        mDirY = dirY;
        mHeadX = cx - dirX * exit;
        mHeadY = cy - dirY * exit;
        mFullLength = fullLength;
        float slack = mHalfWidth * HEAD_GLOW_RADIUS + 2f;
        mBoundsLeft = Math.min(cx, fromX) - slack;
        mBoundsTop = Math.min(cy, fromY) - slack;
        mBoundsRight = Math.max(cx, fromX) + slack;
        mBoundsBottom = Math.max(cy, fromY) + slack;
    }

    /** Whether a comet was started and has not yet aged out; clears itself once it has. */
    public boolean alive(long nowMs) {
        if (mActive && nowMs - mStartMs >= DURATION_MS) mActive = false;
        return mActive;
    }

    public boolean active() {
        return mActive;
    }

    /**
     * Lays the comet out as it stands at {@code nowMs}.
     *
     * @return false when there is nothing to draw
     */
    public boolean layout(long nowMs) {
        if (!mActive) return false;
        float age = (nowMs - mStartMs) / (float) DURATION_MS;
        if (age >= 1f) return false;
        if (age < 0f) age = 0f;
        float remaining = 1f - age;
        // The tail draws in quickly at first and settles into the head; the streak stays bright
        // for most of its life and fades out at the end.
        mLength = mFullLength * remaining * remaining;
        mStrength = 1f - age * age;
        mTailX = mHeadX - mDirX * mLength;
        mTailY = mHeadY - mDirY * mLength;
        float perpX = -mDirY, perpY = mDirX;
        int n = 0;
        // The base sits inside the cursor, under the mask, so the head shows full width.
        n = put(n, mCenterX + perpX * mHalfWidth, mCenterY + perpY * mHalfWidth);
        for (int i = 0; i < TAPER_STEPS; i++) {
            float f = i / (float) TAPER_STEPS;
            float w = taper(f);
            n = put(n, mHeadX - mDirX * mLength * f + perpX * w,
                mHeadY - mDirY * mLength * f + perpY * w);
        }
        n = put(n, mTailX, mTailY);
        for (int i = TAPER_STEPS - 1; i >= 0; i--) {
            float f = i / (float) TAPER_STEPS;
            float w = taper(f);
            n = put(n, mHeadX - mDirX * mLength * f - perpX * w,
                mHeadY - mDirY * mLength * f - perpY * w);
        }
        n = put(n, mCenterX - perpX * mHalfWidth, mCenterY - perpY * mHalfWidth);
        mPointCount = n / 2;
        return true;
    }

    private float taper(float f) {
        return mHalfWidth * (float) Math.pow(1f - f, TAPER_POWER);
    }

    private int put(int n, float x, float y) {
        mOutline[n] = x;
        mOutline[n + 1] = y;
        return n + 2;
    }

    /** The outline of the latest {@link #layout}, x then y, {@link #pointCount()} points. */
    public float[] outline() {
        return mOutline;
    }

    public int pointCount() {
        return mPointCount;
    }

    /** Where the streak meets the cursor's trailing edge; the brightest point. */
    public float headX() {
        return mHeadX;
    }

    public float headY() {
        return mHeadY;
    }

    public float tailX() {
        return mTailX;
    }

    public float tailY() {
        return mTailY;
    }

    /** The tail's length from the head, as of the latest {@link #layout}. */
    public float length() {
        return mLength;
    }

    /** How strongly the comet shows, 0..1, as of the latest {@link #layout}. */
    public float strength() {
        return mStrength;
    }

    public float halfWidth() {
        return mHalfWidth;
    }

    /** The box the last started comet can paint inside, glow included. */
    public float boundsLeft() {
        return mBoundsLeft;
    }

    public float boundsTop() {
        return mBoundsTop;
    }

    public float boundsRight() {
        return mBoundsRight;
    }

    public float boundsBottom() {
        return mBoundsBottom;
    }
}
