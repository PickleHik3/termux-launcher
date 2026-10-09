package com.termux.app.terminal;

/**
 * Where the Railgun trail is at a given moment, in the overlay's y-down pixels. Pure JVM, no
 * Android types, so it is unit tested directly; {@link CursorTrailRailgun} only paints it.
 *
 * <p>A railgun shot, named after kitty's {@code cursor-trail-railgun}: the instant the cursor
 * moves, a straight beam a fraction of the cell wide joins the old position to the cursor. Over a
 * very short life, set from {@code cursor_trail_decay}'s fast time, the beam's far end snaps in
 * toward the cursor while the beam thins and fades, and a few sparks fly off the path to either
 * side, leaning the way the shot went and slowing as they go, then burn out a little after it.
 */
public final class CursorTrailRailgunShape {

    public static final int MAX_SPARKS = 8;
    static final int MIN_SPARKS = 3;
    /** The beam's core width, as a fraction of the cursor's larger side, and its floor in px. */
    static final float CORE_WIDTH = 0.1f;
    static final float MIN_CORE_PX = 1.5f;
    /** How long the beam shows, in multiples of the fast decay, within the bounds below. */
    static final float BEAM_LIFE_SCALE = 2.2f;
    static final long MIN_BEAM_MS = 90L;
    static final long MAX_BEAM_MS = 600L;
    /** The longest a spark lives, in beam lives. */
    static final float SPARK_LIFE_SCALE = 1.6f;
    /** One spark per this many cursor sizes of path, between the counts above. */
    static final float SPARK_SPACING = 2.5f;
    /** A spark's launch speed, in cursor sizes per second, and the drag that slows it. */
    static final float SPARK_SPEED_MIN = 8f;
    static final float SPARK_SPEED_MAX = 16f;
    static final float SPARK_DRAG = 7f;
    /** How far a spark leans from square-on toward the direction of the shot, in radians. */
    static final float SPARK_LEAN_MIN = 0.25f;
    static final float SPARK_LEAN_MAX = 0.9f;
    /** A spark is drawn as the stretch it covered over this many seconds. */
    static final float STREAK_SEC = 0.03f;
    /** The beam thins to this fraction of its width by the end of its life. */
    static final float END_WIDTH = 0.4f;
    /** How far the core's colour is mixed toward white. */
    static final float CORE_WHITE = 0.8f;
    /** Moves shorter than this, centre to centre, show nothing. */
    private static final float MIN_LENGTH_PX = 1f;

    private boolean mActive;
    private long mStartMs;
    private long mBeamMs;
    private long mEndMs;
    private float mFromX, mFromY, mToX, mToY;
    private float mCoreWidth;
    private int mSparks;
    /** Moves started so far; mixed into each seed so two identical shots still differ. */
    private long mSequence;
    // Per spark: start x, y, velocity x, y in px/s, and life in seconds.
    private final float[] mSparkX = new float[MAX_SPARKS];
    private final float[] mSparkY = new float[MAX_SPARKS];
    private final float[] mSparkVx = new float[MAX_SPARKS];
    private final float[] mSparkVy = new float[MAX_SPARKS];
    private final float[] mSparkLife = new float[MAX_SPARKS];
    private final float[] mR = new float[4];
    private final float[] mR2 = new float[4];
    private float mBoundsLeft, mBoundsTop, mBoundsRight, mBoundsBottom;

    // The latest layout.
    private boolean mBeamVisible;
    private float mBeamTailX, mBeamTailY;
    private float mBeamWidth;
    private float mBeamAlpha;
    /** Per live spark: tail x, y, head x, y, alpha. */
    private final float[] mStreaks = new float[MAX_SPARKS * 5];
    private int mStreakCount;

    public void reset() {
        mActive = false;
    }

    /**
     * A move the trail accepted, made at {@code nowMs}: the cursor's rect before and after it.
     * Fires a new shot in place of any still showing.
     *
     * @param decayFastSec {@code cursor_trail_decay}'s fast time, in seconds
     */
    public void start(float fromL, float fromT, float fromR, float fromB,
                      float toL, float toT, float toR, float toB, long nowMs, float decayFastSec) {
        float fx = (fromL + fromR) * 0.5f, fy = (fromT + fromB) * 0.5f;
        float tx = (toL + toR) * 0.5f, ty = (toT + toB) * 0.5f;
        float dx = tx - fx, dy = ty - fy;
        float len = (float) Math.sqrt((double) dx * dx + (double) dy * dy);
        mActive = false;
        if (len < MIN_LENGTH_PX) return;
        float dirX = dx / len, dirY = dy / len;
        float larger = Math.max(Math.max(toR - toL, toB - toT), 1f);
        mActive = true;
        mStartMs = nowMs;
        mFromX = fx;
        mFromY = fy;
        mToX = tx;
        mToY = ty;
        mCoreWidth = Math.max(MIN_CORE_PX, CORE_WIDTH * larger);
        mBeamMs = beamLifeMs(decayFastSec);
        long sparkMs = Math.round(mBeamMs * SPARK_LIFE_SCALE);
        mEndMs = Math.max(mBeamMs, sparkMs);

        double seed = dx * 0.1371 + dy * 0.2113 + tx * 0.0173 + ty * 0.0291
            + (mSequence++ % 4096L) * 0.6180339887;
        float s = (float) ((seed - Math.floor(seed)) * 1000.0);
        mSparks = sparkCount(len, larger);
        for (int k = 0; k < mSparks; k++) {
            CursorTrailParticles.hash43(k, s, 31f, mR);
            CursorTrailParticles.hash43(s, k, 71f, mR2);
            float u = (k + 0.15f + 0.7f * mR[0]) / mSparks;
            float side = mR[1] < 0.5f ? -1f : 1f;
            float lean = lerp(SPARK_LEAN_MIN, SPARK_LEAN_MAX, mR[2]);
            // Square-on to the path on one side, leaning toward where the shot went.
            double angle = side * (Math.PI / 2 - lean);
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            float speed = larger * lerp(SPARK_SPEED_MIN, SPARK_SPEED_MAX, mR[3]);
            mSparkX[k] = fx + dx * u;
            mSparkY[k] = fy + dy * u;
            mSparkVx[k] = (cos * dirX - sin * dirY) * speed;
            mSparkVy[k] = (sin * dirX + cos * dirY) * speed;
            mSparkLife[k] = sparkMs / 1000f * lerp(0.55f, 1f, mR2[0]);
        }
        float slack = larger * SPARK_SPEED_MAX / SPARK_DRAG + 4f * mCoreWidth + 2f;
        mBoundsLeft = Math.min(fx, tx) - slack;
        mBoundsTop = Math.min(fy, ty) - slack;
        mBoundsRight = Math.max(fx, tx) + slack;
        mBoundsBottom = Math.max(fy, ty) + slack;
    }

    /** Whether a shot was fired and has not yet burnt out; clears itself once it has. */
    public boolean alive(long nowMs) {
        if (mActive && nowMs - mStartMs >= mEndMs) mActive = false;
        return mActive;
    }

    public boolean active() {
        return mActive;
    }

    /**
     * Lays the shot out as it stands at {@code nowMs}.
     *
     * @return false when there is nothing to draw
     */
    public boolean layout(long nowMs) {
        if (!mActive) return false;
        long ageMs = Math.max(0L, nowMs - mStartMs);
        if (ageMs >= mEndMs) return false;
        float x = ageMs / (float) mBeamMs;
        mBeamVisible = x < 1f;
        if (mBeamVisible) {
            // The far end lingers for a moment, then snaps in to the cursor.
            float pull = x * x;
            mBeamTailX = mFromX + (mToX - mFromX) * pull;
            mBeamTailY = mFromY + (mToY - mFromY) * pull;
            mBeamWidth = mCoreWidth * (1f - (1f - END_WIDTH) * x);
            mBeamAlpha = 1f - x * x;
        } else {
            mBeamAlpha = 0f;
        }
        float t = ageMs / 1000f;
        int n = 0;
        for (int k = 0; k < mSparks; k++) {
            float life = mSparkLife[k];
            if (t >= life) continue;
            float d = dragDistance(t);
            float d0 = dragDistance(Math.max(0f, t - STREAK_SEC));
            int o = n * 5;
            mStreaks[o] = mSparkX[k] + mSparkVx[k] * d0;
            mStreaks[o + 1] = mSparkY[k] + mSparkVy[k] * d0;
            mStreaks[o + 2] = mSparkX[k] + mSparkVx[k] * d;
            mStreaks[o + 3] = mSparkY[k] + mSparkVy[k] * d;
            mStreaks[o + 4] = 1f - t / life;
            n++;
        }
        mStreakCount = n;
        return true;
    }

    /** How long the beam shows for this fast decay, in milliseconds. */
    static long beamLifeMs(float decayFastSec) {
        long ms = Math.round(BEAM_LIFE_SCALE * decayFastSec * 1000f);
        return Math.max(MIN_BEAM_MS, Math.min(MAX_BEAM_MS, ms));
    }

    /** A few sparks: one per {@link #SPARK_SPACING} cursor sizes of path, within bounds. */
    static int sparkCount(float pathLen, float cursorSize) {
        int n = Math.round(pathLen / (SPARK_SPACING * Math.max(cursorSize, 1f)));
        return Math.max(MIN_SPARKS, Math.min(MAX_SPARKS, n));
    }

    /** How far a spark has gone after {@code t} seconds, per unit of launch speed. */
    static float dragDistance(float t) {
        return (1f - (float) Math.exp(-SPARK_DRAG * t)) / SPARK_DRAG;
    }

    /** The core's white-hot colour for a trail colour, RGB only. */
    static int hotColor(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += Math.round((255 - r) * CORE_WHITE);
        g += Math.round((255 - g) * CORE_WHITE);
        b += Math.round((255 - b) * CORE_WHITE);
        return (r << 16) | (g << 8) | b;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public boolean beamVisible() {
        return mBeamVisible;
    }

    /** The beam's far end; it runs from here to the cursor's centre. */
    public float beamTailX() {
        return mBeamTailX;
    }

    public float beamTailY() {
        return mBeamTailY;
    }

    /** The cursor's centre, where the beam ends. */
    public float beamHeadX() {
        return mToX;
    }

    public float beamHeadY() {
        return mToY;
    }

    /** The beam's core width as of the latest layout; the glow round it is wider. */
    public float beamWidth() {
        return mBeamWidth;
    }

    public float beamAlpha() {
        return mBeamAlpha;
    }

    /** The core width the shot started with; sparks are drawn thinner than it. */
    public float coreWidth() {
        return mCoreWidth;
    }

    /** Live sparks as of the latest layout. */
    public int sparkCount() {
        return mStreakCount;
    }

    /** Per live spark, five floats: tail x, tail y, head x, head y, alpha 0..1. */
    public float[] sparks() {
        return mStreaks;
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
