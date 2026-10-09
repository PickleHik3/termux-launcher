package com.termux.app.terminal;

/**
 * Where the Motion blur trail is at a given moment, in the overlay's y-down pixels. Pure JVM, no
 * Android types, so it is unit tested directly; {@link CursorTrailMotionBlur} only paints it.
 *
 * <p>Motion blur reads as a long exposure of the cursor: a soft, wide, low-contrast smear of
 * feathered ghost copies of the cursor, spread along the path from a head that lags behind the
 * cursor back to a tail that lags further still. Both ends ease toward the cursor on the trail's
 * own corner law, {@code 1 - 2^(-10 t / decay)}, at decays longer than {@code cursor_trail_decay}'s
 * slow one, so the smear follows the cursor instead of leading it and lingers after Default has
 * settled. Where the ghosts are spread far apart each one stretches along the direction of travel,
 * so a long jump still reads as one continuous streak, and their alpha is set from how deeply they
 * overlap, so the pile at the head stays at about {@link #PEAK_ALPHA} however many there are.
 */
public final class CursorTrailBlurShape {

    public static final int MAX_GHOSTS = 24;
    /** The head's and the tail's decay, in multiples of {@code cursor_trail_decay}'s slow time. */
    static final float HEAD_DECAY_SCALE = 1.25f;
    static final float TAIL_DECAY_SCALE = 2.5f;
    /** How long one smear shows, in multiples of the slow decay, within the bounds below. */
    static final float LIFETIME_SCALE = 2.25f;
    static final long MIN_LIFETIME_MS = 250L;
    static final long MAX_LIFETIME_MS = 3_000L;
    /** Feather added round each ghost, as a fraction of the cursor's larger side. */
    static final float FEATHER = 0.4f;
    /** The opacity where the ghosts pile up at full weight. */
    static final float PEAK_ALPHA = 0.55f;
    /** The tail ghost's weight against the head's: the smear thins out toward where it began. */
    static final float TAIL_WEIGHT = 0.15f;
    /** Ghost spacing, as a fraction of an unstretched ghost's length along the path. */
    static final float GHOST_STEP = 0.45f;
    /** A stretched ghost's half-length, in ghost spacings: neighbours always overlap. */
    static final float STRETCH = 0.75f;
    /** Moves shorter than this, centre to centre, show nothing. */
    private static final float MIN_LENGTH_PX = 1f;

    private boolean mActive;
    private long mStartMs;
    private float mFromX, mFromY, mToX, mToY;
    private float mPathLen;
    private float mDegrees;
    /** A ghost's half-length along the path and half-width across it, feather included. */
    private float mBaseAlong, mAcross;
    private float mHeadDecay, mTailDecay;
    private long mLifetimeMs;
    private float mBoundsLeft, mBoundsTop, mBoundsRight, mBoundsBottom;

    // The latest layout.
    private final float[] mGhostX = new float[MAX_GHOSTS];
    private final float[] mGhostY = new float[MAX_GHOSTS];
    private final float[] mGhostAlpha = new float[MAX_GHOSTS];
    private int mGhostCount;
    private float mAlong;
    private float mStrength;
    private float mHeadX, mHeadY, mTailX, mTailY;

    public void reset() {
        mActive = false;
    }

    /**
     * A move the trail accepted, made at {@code nowMs}. A smear still showing is picked up where
     * its head is, so a second jump continues from what is on screen rather than snapping back.
     *
     * @param decaySlowSec {@code cursor_trail_decay}'s slow time, in seconds
     */
    public void start(float fromL, float fromT, float fromR, float fromB,
                      float toL, float toT, float toR, float toB, long nowMs, float decaySlowSec) {
        float fromX, fromY;
        if (layout(nowMs)) {
            fromX = mHeadX;
            fromY = mHeadY;
        } else {
            fromX = (fromL + fromR) * 0.5f;
            fromY = (fromT + fromB) * 0.5f;
        }
        float toX = (toL + toR) * 0.5f, toY = (toT + toB) * 0.5f;
        float dx = toX - fromX, dy = toY - fromY;
        float len = (float) Math.sqrt((double) dx * dx + (double) dy * dy);
        mActive = false;
        if (len < MIN_LENGTH_PX) return;
        float dirX = dx / len, dirY = dy / len;
        float w = Math.max(toR - toL, 0f), h = Math.max(toB - toT, 0f);
        float feather = FEATHER * Math.max(Math.max(w, h), 1f);
        mBaseAlong = 0.5f * (Math.abs(dirX) * w + Math.abs(dirY) * h) + feather;
        mAcross = 0.5f * (Math.abs(dirY) * w + Math.abs(dirX) * h) + feather;
        float decay = Math.max(decaySlowSec, 0.01f);
        mHeadDecay = HEAD_DECAY_SCALE * decay;
        mTailDecay = TAIL_DECAY_SCALE * decay;
        mLifetimeMs = lifetimeMs(decay);
        mActive = true;
        mStartMs = nowMs;
        mFromX = fromX;
        mFromY = fromY;
        mToX = toX;
        mToY = toY;
        mPathLen = len;
        mDegrees = (float) Math.toDegrees(Math.atan2(dirY, dirX));
        // A ghost stretches only once the spacing passes one step, so this is its longest.
        float maxAlong = Math.max(mBaseAlong, STRETCH * len / (MAX_GHOSTS - 1));
        float slack = Math.max(maxAlong, mAcross) + 2f;
        mBoundsLeft = Math.min(fromX, toX) - slack;
        mBoundsTop = Math.min(fromY, toY) - slack;
        mBoundsRight = Math.max(fromX, toX) + slack;
        mBoundsBottom = Math.max(fromY, toY) + slack;
    }

    /** Whether a smear was started and has not yet aged out; clears itself once it has. */
    public boolean alive(long nowMs) {
        if (mActive && nowMs - mStartMs >= mLifetimeMs) mActive = false;
        return mActive;
    }

    public boolean active() {
        return mActive;
    }

    /**
     * Lays the smear out as it stands at {@code nowMs}.
     *
     * @return false when there is nothing to draw
     */
    public boolean layout(long nowMs) {
        if (!mActive) return false;
        long ageMs = Math.max(0L, nowMs - mStartMs);
        if (ageMs >= mLifetimeMs) return false;
        float age = ageMs / 1000f;
        float x = ageMs / (float) mLifetimeMs;
        float head = progress(age, mHeadDecay);
        float tail = progress(age, mTailDecay);
        mHeadX = mFromX + (mToX - mFromX) * head;
        mHeadY = mFromY + (mToY - mFromY) * head;
        mTailX = mFromX + (mToX - mFromX) * tail;
        mTailY = mFromY + (mToY - mFromY) * tail;
        mStrength = 1f - x * x;
        float span = mPathLen * (head - tail);
        int n = ghostCount(span, mBaseAlong);
        float spacing = span / (n - 1);
        mAlong = Math.max(mBaseAlong, STRETCH * spacing);
        float alpha = ghostAlpha(2f * mAlong, spacing, n);
        for (int i = 0; i < n; i++) {
            float f = i / (float) (n - 1);
            mGhostX[i] = mHeadX + (mTailX - mHeadX) * f;
            mGhostY[i] = mHeadY + (mTailY - mHeadY) * f;
            mGhostAlpha[i] = alpha * (1f - (1f - TAIL_WEIGHT) * f) * mStrength;
        }
        mGhostCount = n;
        return true;
    }

    /** How long a smear shows for this slow decay, in milliseconds. */
    static long lifetimeMs(float decaySlowSec) {
        long ms = Math.round(LIFETIME_SCALE * decaySlowSec * 1000f);
        return Math.max(MIN_LIFETIME_MS, Math.min(MAX_LIFETIME_MS, ms));
    }

    /** The trail's corner law: how far along its path a point easing at {@code decaySec} is. */
    static float progress(float ageSec, float decaySec) {
        if (ageSec <= 0f) return 0f;
        return 1f - (float) Math.pow(2.0, -10.0 * ageSec / decaySec);
    }

    /**
     * How many ghosts cover a smear {@code span} long: one per step, head and tail always, so the
     * count changes by one ghost at a time as the smear grows and shrinks.
     */
    static int ghostCount(float span, float baseAlong) {
        float step = GHOST_STEP * 2f * baseAlong;
        if (span <= 0f || step <= 0f) return 2;
        return Math.min(MAX_GHOSTS, 2 + (int) Math.floor(span / step));
    }

    /**
     * Each ghost's alpha, so that the ghosts piled over one point, {@code ghostLength / spacing}
     * deep and never more than {@code count}, add up to {@link #PEAK_ALPHA}.
     */
    static float ghostAlpha(float ghostLength, float spacing, int count) {
        float depth = spacing > 0f ? ghostLength / spacing : count;
        depth = Math.max(1f, Math.min(count, depth));
        return 1f - (float) Math.pow(1f - PEAK_ALPHA, 1f / depth);
    }

    public int ghostCount() {
        return mGhostCount;
    }

    /** Ghost {@code i} of the latest layout, 0 the head; its centre and its alpha, 0..1. */
    public float ghostX(int i) {
        return mGhostX[i];
    }

    public float ghostY(int i) {
        return mGhostY[i];
    }

    public float ghostAlpha(int i) {
        return mGhostAlpha[i];
    }

    /** Every ghost's half-length along the path, as of the latest layout, feather included. */
    public float along() {
        return mAlong;
    }

    /** Every ghost's half-width across the path, feather included. */
    public float across() {
        return mAcross;
    }

    /** The direction of travel, in degrees clockwise from +x (y is down). */
    public float degrees() {
        return mDegrees;
    }

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

    /** How strongly the smear shows, 0..1, as of the latest layout. */
    public float strength() {
        return mStrength;
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
