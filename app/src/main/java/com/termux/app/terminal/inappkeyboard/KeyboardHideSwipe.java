package com.termux.app.terminal.inappkeyboard;

/**
 * The deliberate swipe that puts the keyboard away: a press in the strip above the first row of
 * keys, pulled mostly downward past a clear distance, or flicked down. Pure geometry in pixels
 * and milliseconds so the rules can be tested without a view; {@link KeyboardHideSwipeGesture}
 * feeds it touches and moves the keyboard.
 *
 * <p>Sequence: {@link #begin} arms it. Each {@link #move} either keeps it armed inside the slop,
 * abandons it (sideways first, or upward), or starts the drag, from where {@link #dragPx()} is how
 * far the keyboard follows the finger. {@link #release} answers whether the keyboard goes away or
 * springs back. An abandoned swipe eats the rest of its stream and does nothing: nothing under the
 * strip wants a press there.
 */
public final class KeyboardHideSwipe {

    public enum Phase { IDLE, ARMED, DRAGGING, ABANDONED }

    public enum Outcome { NONE, HIDE, SPRING_BACK }

    /** Velocity is read over the last stretch of movement, no older than this. */
    static final long VELOCITY_WINDOW_MS = 100L;
    private static final int SAMPLES = 8;

    private final float mSlopPx;
    private final float mThresholdPx;
    private final float mFlingPxPerS;
    private final float mFlingMinDistancePx;

    private Phase mPhase = Phase.IDLE;
    private float mDownX;
    private float mDownY;
    /** Where the drag took hold; the keyboard follows the finger from here, without a jump. */
    private float mAnchorY;
    private float mLastY;
    private final float[] mSampleY = new float[SAMPLES];
    private final long[] mSampleT = new long[SAMPLES];
    private int mSampleCount;
    private int mSampleHead;

    /**
     * @param slopPx how far a finger may wander before the swipe has a direction
     * @param thresholdPx how far down, from the press, a release completes the hide
     * @param flingPxPerS the downward speed at which a shorter swipe still completes it
     * @param flingMinDistancePx how far a fling must have travelled to count, so a twitch cannot
     */
    public KeyboardHideSwipe(float slopPx, float thresholdPx, float flingPxPerS,
                             float flingMinDistancePx) {
        mSlopPx = Math.max(0f, slopPx);
        mThresholdPx = Math.max(0f, thresholdPx);
        mFlingPxPerS = Math.max(0f, flingPxPerS);
        mFlingMinDistancePx = Math.max(0f, flingMinDistancePx);
    }

    /**
     * How far down from the keyboard's top the swipe may start. The whole strip that hits no
     * key ({@code nullBandPx}) always; when that is thinner than {@code minBandPx} the band grows
     * down to the minimum, but never past the drawn top of the first row's caps
     * ({@code firstCapTopPx}), since a press on a key stays a key gesture.
     */
    public static float bandBottomPx(float nullBandPx, float firstCapTopPx, float minBandPx) {
        return Math.max(nullBandPx, Math.min(minBandPx, firstCapTopPx));
    }

    /** Whether a press at {@code y} (view coordinates) is in the start band. */
    public static boolean bandContains(float y, float nullBandPx, float firstCapTopPx,
                                       float minBandPx) {
        return y >= 0f && y < bandBottomPx(nullBandPx, firstCapTopPx, minBandPx);
    }

    public Phase phase() {
        return mPhase;
    }

    /** A press landed in the band. */
    public void begin(float x, float y, long timeMs) {
        mPhase = Phase.ARMED;
        mDownX = x;
        mDownY = y;
        mAnchorY = y;
        mLastY = y;
        mSampleCount = 0;
        mSampleHead = 0;
        sample(y, timeMs);
    }

    public Phase move(float x, float y, long timeMs) {
        if (mPhase == Phase.IDLE || mPhase == Phase.ABANDONED)
            return mPhase;
        mLastY = y;
        sample(y, timeMs);
        if (mPhase == Phase.ARMED) {
            float dx = x - mDownX;
            float dy = y - mDownY;
            if (Math.abs(dx) > mSlopPx && Math.abs(dx) > Math.abs(dy)) {
                mPhase = Phase.ABANDONED;
            } else if (dy < -mSlopPx) {
                mPhase = Phase.ABANDONED;
            } else if (dy > mSlopPx) {
                mPhase = Phase.DRAGGING;
                mAnchorY = y;
            }
        }
        return mPhase;
    }

    /** How far the keyboard sits below its resting place while the finger drags it. */
    public float dragPx() {
        return mPhase == Phase.DRAGGING ? Math.max(0f, mLastY - mAnchorY) : 0f;
    }

    public Outcome release(float x, float y, long timeMs) {
        move(x, y, timeMs);
        Outcome outcome = Outcome.NONE;
        if (mPhase == Phase.DRAGGING) {
            float travelled = y - mDownY;
            boolean pastThreshold = travelled >= mThresholdPx;
            boolean flung = travelled >= mFlingMinDistancePx
                && velocityPxPerS() >= mFlingPxPerS;
            outcome = pastThreshold || flung ? Outcome.HIDE : Outcome.SPRING_BACK;
        }
        mPhase = Phase.IDLE;
        return outcome;
    }

    /** The stream was taken away: whatever moved goes back. */
    public Outcome cancel() {
        Outcome outcome = mPhase == Phase.DRAGGING ? Outcome.SPRING_BACK : Outcome.NONE;
        mPhase = Phase.IDLE;
        return outcome;
    }

    /** Downward speed over the trailing window, positive going down; 0 without enough samples. */
    float velocityPxPerS() {
        if (mSampleCount < 2)
            return 0f;
        int newest = (mSampleHead - 1 + SAMPLES) % SAMPLES;
        long tNew = mSampleT[newest];
        float yNew = mSampleY[newest];
        int oldest = newest;
        for (int i = 1; i < mSampleCount; i++) {
            int index = (newest - i + SAMPLES) % SAMPLES;
            if (tNew - mSampleT[index] > VELOCITY_WINDOW_MS)
                break;
            oldest = index;
        }
        long dt = tNew - mSampleT[oldest];
        if (dt <= 0)
            return 0f;
        return (yNew - mSampleY[oldest]) * 1000f / dt;
    }

    private void sample(float y, long timeMs) {
        mSampleY[mSampleHead] = y;
        mSampleT[mSampleHead] = timeMs;
        mSampleHead = (mSampleHead + 1) % SAMPLES;
        if (mSampleCount < SAMPLES)
            mSampleCount++;
    }
}
