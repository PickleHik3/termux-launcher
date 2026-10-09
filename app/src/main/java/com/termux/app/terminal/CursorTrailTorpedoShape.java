package com.termux.app.terminal;

/**
 * Where the Torpedo trail is at a given moment, in the overlay's y-down pixels. Pure JVM, no
 * Android types, so it is unit tested directly; {@link CursorTrailTorpedo} only paints it.
 *
 * <p>A torpedo, named after kitty's {@code cursor-trail-torpedo}: one solid body with a round nose
 * as wide as the cursor and sides that taper to a point at the tail, a few cells long. When the
 * cursor jumps, the body slides out of the old position and travels the path, fast off the mark
 * and slowing as it closes, until it has run nose first into the cursor and disappeared under it.
 * As its tail passes along the path it leaves a faint wake of small rings that drift off to either
 * side, grow and fade. It travels over a time set from {@code cursor_trail_decay}'s slow decay,
 * a little longer for a longer jump, so it is always visibly slower than Railgun's shot.
 */
public final class CursorTrailTorpedoShape {

    public static final int MAX_WAKE = 16;
    static final int MIN_WAKE = 3;
    /** Points round the nose, end to end, and along each tapering side. */
    static final int NOSE_STEPS = 6;
    static final int TAPER_STEPS = 8;
    /** Outline points: the tail tip, both sides, and the nose. */
    public static final int MAX_POINTS = 2 * TAPER_STEPS + NOSE_STEPS;
    /** The body's width at the nose, as a fraction of the cursor's width across the path. */
    static final float BODY_WIDTH = 0.8f;
    /** The narrowest the body gets, as a fraction of the cursor's larger side (beam cursors). */
    static final float MIN_ACROSS = 0.5f;
    /** Body length in cursor sizes, and never more than this share of the path. */
    static final float BODY_LENGTH = 3f;
    static final float MAX_BODY_SHARE = 0.9f;
    /** How round the taper is: 1 is a straight cone, smaller fills the sides out. */
    static final float TAPER_POWER = 0.85f;
    /** Travel time in slow decays: a base, and a little more per doubling of the jump. */
    static final float TRAVEL_BASE = 0.6f;
    static final float TRAVEL_PER_DOUBLING = 0.1f;
    static final long MIN_TRAVEL_MS = 120L;
    static final long MAX_TRAVEL_MS = 3_000L;
    /** One wake ring per this many body radii of path, between the counts above. */
    static final float WAKE_SPACING = 2.2f;
    /** A ring's life, in slow decays, within the bounds below. */
    static final float WAKE_LIFE_SCALE = 0.9f;
    static final long MIN_WAKE_MS = 150L;
    static final long MAX_WAKE_MS = 2_000L;
    /** A fresh ring's opacity against the body's: the wake is faint. */
    static final float WAKE_ALPHA = 0.4f;
    /** A ring's radius when born and when gone, in body radii. */
    static final float WAKE_START_RADIUS = 0.22f;
    static final float WAKE_END_RADIUS = 0.45f;
    /** How far to the side a ring is born, and how fast it drifts out and back, in body radii. */
    static final float WAKE_OFFSET_MAX = 0.6f;
    static final float WAKE_DRIFT = 1.2f;
    static final float WAKE_BACK = 0.5f;
    /** Moves shorter than this, centre to centre, show nothing. */
    private static final float MIN_LENGTH_PX = 1f;

    private boolean mActive;
    private long mStartMs;
    private long mTravelMs;
    private long mWakeMs;
    private float mFromX, mFromY;
    private float mDirX, mDirY;
    private float mPathLen;
    private float mRadius;
    private float mBodyLength;
    private int mWake;
    /** Moves started so far; mixed into each seed so two identical runs still differ. */
    private long mSequence;
    // Per ring: where it is born, its drift in px/s, and when it is born after the start.
    private final float[] mWakeX = new float[MAX_WAKE];
    private final float[] mWakeY = new float[MAX_WAKE];
    private final float[] mWakeVx = new float[MAX_WAKE];
    private final float[] mWakeVy = new float[MAX_WAKE];
    private final float[] mWakeBornMs = new float[MAX_WAKE];
    private final float[] mR = new float[4];
    private float mBoundsLeft, mBoundsTop, mBoundsRight, mBoundsBottom;

    // The latest layout.
    private boolean mBodyVisible;
    private final float[] mOutline = new float[MAX_POINTS * 2];
    private int mPointCount;
    private float mNoseX, mNoseY, mTailX, mTailY;
    /** Per live ring: x, y, radius, alpha. */
    private final float[] mRings = new float[MAX_WAKE * 4];
    private int mRingCount;

    public void reset() {
        mActive = false;
    }

    /**
     * A move the trail accepted, made at {@code nowMs}: the cursor's rect before and after it.
     * Launches a new torpedo in place of any still running.
     *
     * @param decaySlowSec {@code cursor_trail_decay}'s slow time, in seconds
     */
    public void start(float fromL, float fromT, float fromR, float fromB,
                      float toL, float toT, float toR, float toB, long nowMs, float decaySlowSec) {
        float fx = (fromL + fromR) * 0.5f, fy = (fromT + fromB) * 0.5f;
        float tx = (toL + toR) * 0.5f, ty = (toT + toB) * 0.5f;
        float dx = tx - fx, dy = ty - fy;
        float len = (float) Math.sqrt((double) dx * dx + (double) dy * dy);
        mActive = false;
        if (len < MIN_LENGTH_PX) return;
        float dirX = dx / len, dirY = dy / len;
        float w = Math.max(toR - toL, 0f), h = Math.max(toB - toT, 0f);
        float larger = Math.max(Math.max(w, h), 1f);
        float across = Math.abs(dirY) * w + Math.abs(dirX) * h;
        float decay = Math.max(decaySlowSec, 0.01f);
        mActive = true;
        mStartMs = nowMs;
        mFromX = fx;
        mFromY = fy;
        mDirX = dirX;
        mDirY = dirY;
        mPathLen = len;
        mRadius = 0.5f * BODY_WIDTH * Math.max(across, MIN_ACROSS * larger);
        mBodyLength = Math.min(BODY_LENGTH * larger, MAX_BODY_SHARE * len);
        mTravelMs = travelMs(len, larger, decay);
        mWakeMs = clamp(Math.round(WAKE_LIFE_SCALE * decay * 1000f), MIN_WAKE_MS, MAX_WAKE_MS);

        double seed = dx * 0.1371 + dy * 0.2113 + tx * 0.0173 + ty * 0.0291
            + (mSequence++ % 4096L) * 0.6180339887;
        float s = (float) ((seed - Math.floor(seed)) * 1000.0);
        float perpX = -dirY, perpY = dirX;
        mWake = wakeCount(len, mRadius);
        for (int k = 0; k < mWake; k++) {
            CursorTrailParticles.hash43(k, s, 43f, mR);
            float at = len * (k + 0.2f + 0.6f * mR[0]) / mWake;
            float side = mR[1] < 0.5f ? -1f : 1f;
            float offset = side * mRadius * WAKE_OFFSET_MAX * (0.3f + 0.7f * mR[2]);
            float drift = side * mRadius * WAKE_DRIFT * (0.5f + 0.5f * mR[3]);
            mWakeX[k] = fx + dirX * at + perpX * offset;
            mWakeY[k] = fy + dirY * at + perpY * offset;
            mWakeVx[k] = perpX * drift - dirX * WAKE_BACK * mRadius;
            mWakeVy[k] = perpY * drift - dirY * WAKE_BACK * mRadius;
            // Born as the tail passes: when the run, a body length ahead of the tail, reaches it.
            mWakeBornMs[k] = tailPassesAt((at + mBodyLength) / (len + mBodyLength)) * mTravelMs;
        }
        float wakeSec = mWakeMs / 1000f;
        float slack = mRadius * (1f + WAKE_OFFSET_MAX + (WAKE_DRIFT + WAKE_BACK) * wakeSec
            + WAKE_END_RADIUS) + 2f;
        mBoundsLeft = Math.min(fx, tx) - slack;
        mBoundsTop = Math.min(fy, ty) - slack;
        mBoundsRight = Math.max(fx, tx) + slack;
        mBoundsBottom = Math.max(fy, ty) + slack;
    }

    /** Whether a torpedo was launched and its wake has not yet faded; clears itself once so. */
    public boolean alive(long nowMs) {
        if (mActive && nowMs - mStartMs >= mTravelMs + mWakeMs) mActive = false;
        return mActive;
    }

    public boolean active() {
        return mActive;
    }

    /**
     * Lays the torpedo out as it stands at {@code nowMs}.
     *
     * @return false when there is nothing to draw
     */
    public boolean layout(long nowMs) {
        if (!mActive) return false;
        long ageMs = Math.max(0L, nowMs - mStartMs);
        if (ageMs >= mTravelMs + mWakeMs) return false;
        float x = Math.min(1f, ageMs / (float) mTravelMs);
        // How far the nose would be without the cursor in the way; the tail is a body behind it.
        float run = (mPathLen + mBodyLength) * travel(x);
        float nose = Math.min(run, mPathLen);
        float tail = Math.max(0f, Math.min(run - mBodyLength, mPathLen));
        float body = nose - tail;
        mBodyVisible = x < 1f && body > 0.5f;
        mNoseX = mFromX + mDirX * nose;
        mNoseY = mFromY + mDirY * nose;
        mTailX = mFromX + mDirX * tail;
        mTailY = mFromY + mDirY * tail;
        if (mBodyVisible) outline(nose, tail);

        float wakeSec = mWakeMs / 1000f;
        int n = 0;
        for (int k = 0; k < mWake; k++) {
            float age = (ageMs - mWakeBornMs[k]) / 1000f;
            if (age < 0f || age >= wakeSec) continue;
            float f = age / wakeSec;
            int o = n * 4;
            mRings[o] = mWakeX[k] + mWakeVx[k] * age;
            mRings[o + 1] = mWakeY[k] + mWakeVy[k] * age;
            mRings[o + 2] = mRadius * (WAKE_START_RADIUS + (WAKE_END_RADIUS - WAKE_START_RADIUS) * f);
            mRings[o + 3] = WAKE_ALPHA * (1f - f);
            n++;
        }
        mRingCount = n;
        return true;
    }

    /** The body's outline between these distances along the path: tail tip, side, nose, side. */
    private void outline(float nose, float tail) {
        float body = nose - tail;
        float r = Math.min(mRadius, 0.4f * body);
        float cx = mFromX + mDirX * (nose - r), cy = mFromY + mDirY * (nose - r);
        float taper = body - r;
        float perpX = -mDirY, perpY = mDirX;
        int n = put(0, mTailX, mTailY);
        for (int i = TAPER_STEPS - 1; i >= 1; i--) {
            float f = i / (float) TAPER_STEPS;
            float w = halfWidth(r, f);
            n = put(n, cx - mDirX * taper * f + perpX * w, cy - mDirY * taper * f + perpY * w);
        }
        for (int j = 0; j <= NOSE_STEPS; j++) {
            double phi = Math.PI / 2 - Math.PI * j / NOSE_STEPS;
            float along = r * (float) Math.cos(phi), side = r * (float) Math.sin(phi);
            n = put(n, cx + mDirX * along + perpX * side, cy + mDirY * along + perpY * side);
        }
        for (int i = 1; i <= TAPER_STEPS - 1; i++) {
            float f = i / (float) TAPER_STEPS;
            float w = halfWidth(r, f);
            n = put(n, cx - mDirX * taper * f - perpX * w, cy - mDirY * taper * f - perpY * w);
        }
        mPointCount = n / 2;
    }

    /** The body's half-width a fraction {@code f} of the way from the nose (0) to the tail (1). */
    static float halfWidth(float noseRadius, float f) {
        if (f <= 0f) return noseRadius;
        if (f >= 1f) return 0f;
        return noseRadius * (float) Math.pow(1f - f, TAPER_POWER);
    }

    /** How far through its run the torpedo is: fast off the mark, slowing as it closes. */
    static float travel(float x) {
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        return 1f - (1f - x) * (1f - x);
    }

    /** The inverse of {@link #travel}: when, as a fraction of the run, it has come {@code y}. */
    static float tailPassesAt(float y) {
        if (y <= 0f) return 0f;
        if (y >= 1f) return 1f;
        return 1f - (float) Math.sqrt(1f - y);
    }

    /** How long the run takes: longer for a slower decay and, a little, for a longer jump. */
    static long travelMs(float pathLen, float cursorSize, float decaySlowSec) {
        double doublings = Math.log(1.0 + pathLen / Math.max(cursorSize, 1f)) / Math.log(2.0);
        double ms = decaySlowSec * 1000.0 * (TRAVEL_BASE + TRAVEL_PER_DOUBLING * doublings);
        return clamp(Math.round(ms), MIN_TRAVEL_MS, MAX_TRAVEL_MS);
    }

    /** A handful of rings: one per {@link #WAKE_SPACING} body radii of path, within bounds. */
    static int wakeCount(float pathLen, float radius) {
        int n = Math.round(pathLen / (WAKE_SPACING * Math.max(radius, 1f)));
        return Math.max(MIN_WAKE, Math.min(MAX_WAKE, n));
    }

    private static long clamp(long v, long lo, long hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private int put(int n, float x, float y) {
        mOutline[n] = x;
        mOutline[n + 1] = y;
        return n + 2;
    }

    public boolean bodyVisible() {
        return mBodyVisible;
    }

    /** The outline of the latest {@link #layout}, x then y, {@link #pointCount()} points. */
    public float[] outline() {
        return mOutline;
    }

    public int pointCount() {
        return mPointCount;
    }

    /** The front of the nose, on the path. */
    public float noseX() {
        return mNoseX;
    }

    public float noseY() {
        return mNoseY;
    }

    /** The point of the tail, on the path. */
    public float tailX() {
        return mTailX;
    }

    public float tailY() {
        return mTailY;
    }

    /** The body's half-width at its nose, before it is squeezed short. */
    public float radius() {
        return mRadius;
    }

    /** The run's length in milliseconds; the wake outlives it. */
    public long travelMs() {
        return mTravelMs;
    }

    /** Live wake rings as of the latest layout. */
    public int ringCount() {
        return mRingCount;
    }

    /** Per live ring, four floats: x, y, radius, alpha 0..1. */
    public float[] rings() {
        return mRings;
    }

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
