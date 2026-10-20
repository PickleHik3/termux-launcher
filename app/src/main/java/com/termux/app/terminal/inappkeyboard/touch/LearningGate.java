package com.termux.app.terminal.inappkeyboard.touch;

/**
 * Whether learned borders may correct taps yet: warm-up, then active, paused by the drift alarm.
 *
 * <p>Warm-up is the first {@link #WARM_UP_TAPS} taps: the model learns, nothing is corrected,
 * and the take-back rate seen over them becomes the baseline. Once active, the take-back rate
 * over the last {@link #DRIFT_WINDOW} taps is watched; when it reaches {@link #DRIFT_RISE} times
 * the baseline, and by more than chance would explain, correction pauses itself. Resume starts a
 * fresh warm-up.
 *
 * <p>The window is a ring of one bit per tap, set when the tap was taken back. Pure state; no
 * Android types.
 */
public final class LearningGate {

    public static final int STATE_LEARNING = 0;
    public static final int STATE_ACTIVE = 1;
    public static final int STATE_PAUSED = 2;

    /** Taps that learn without correcting and set the baseline. */
    public static final int WARM_UP_TAPS = 1_000;
    /** Taps the drift alarm looks back over. */
    public static final int DRIFT_WINDOW = 500;
    /** How far above the baseline the window's take-back rate must rise to pause correction. */
    public static final double DRIFT_RISE = 1.2;
    /** Standard deviations above the expected count: a one-sided Poisson chance of about 1%. */
    public static final double DRIFT_CHANCE_SIGMAS = 2.33;
    /** Take-backs added on top, so a near-zero baseline still needs a real rise. */
    public static final int DRIFT_CHANCE_MARGIN = 1;
    /** Longs holding one bit per window slot. */
    public static final int WINDOW_WORDS = (DRIFT_WINDOW + 63) / 64;

    private int mState = STATE_LEARNING;
    private int mWarmTaps;
    private int mWarmTakeBacks;
    /** Take-backs per 100 taps over the warm-up. */
    private float mBaseline;
    private final long[] mWindow = new long[WINDOW_WORDS];
    /** Slot the next tap goes into. */
    private int mWindowNext;
    private int mWindowFill;
    private int mWindowTakeBacks;

    public LearningGate() {
    }

    /** Restores a gate; values out of range start a fresh warm-up. */
    public LearningGate(int state, int warmTaps, int warmTakeBacks, float baseline,
                        long[] window, int windowNext, int windowFill) {
        boolean ok = state >= STATE_LEARNING && state <= STATE_PAUSED
            && warmTaps >= 0 && warmTaps <= WARM_UP_TAPS
            && warmTakeBacks >= 0 && warmTakeBacks <= warmTaps
            && baseline >= 0f && !Float.isNaN(baseline) && !Float.isInfinite(baseline)
            && window != null && window.length == WINDOW_WORDS
            && windowNext >= 0 && windowNext < DRIFT_WINDOW
            && windowFill >= 0 && windowFill <= DRIFT_WINDOW;
        if (!ok)
            return;
        mState = state;
        mWarmTaps = warmTaps;
        mWarmTakeBacks = warmTakeBacks;
        mBaseline = baseline;
        System.arraycopy(window, 0, mWindow, 0, WINDOW_WORDS);
        // Bits past the window's end never count.
        int tail = DRIFT_WINDOW % 64;
        if (tail != 0)
            mWindow[WINDOW_WORDS - 1] &= (1L << tail) - 1;
        mWindowNext = windowNext;
        mWindowFill = windowFill;
        for (long word : mWindow) mWindowTakeBacks += Long.bitCount(word);
    }

    public int state() {
        return mState;
    }

    /** Whether learned borders may move taps now. */
    public boolean isCorrecting() {
        return mState == STATE_ACTIVE;
    }

    /** Warm-up taps counted so far, up to {@link #WARM_UP_TAPS}. */
    public int warmUpTaps() {
        return mWarmTaps;
    }

    public int warmUpTakeBacks() {
        return mWarmTakeBacks;
    }

    public float baseline() {
        return mBaseline;
    }

    public long[] window() {
        return mWindow.clone();
    }

    public int windowNext() {
        return mWindowNext;
    }

    public int windowFill() {
        return mWindowFill;
    }

    public int windowTakeBacks() {
        return mWindowTakeBacks;
    }

    /**
     * The last counted tap was taken back. Called before the retyped tap itself is counted, so
     * the bit lands on the tap that missed.
     */
    public void onTakeBack() {
        switch (mState) {
            case STATE_LEARNING:
                if (mWarmTakeBacks < mWarmTaps)
                    mWarmTakeBacks++;
                break;
            case STATE_ACTIVE:
                if (mWindowFill == 0)
                    return;
                int last = (mWindowNext + DRIFT_WINDOW - 1) % DRIFT_WINDOW;
                if (!bit(last)) {
                    setBit(last, true);
                    mWindowTakeBacks++;
                }
                checkDrift();
                break;
            default:
                break;
        }
    }

    /** A correctable tap was counted. */
    public void onTap() {
        switch (mState) {
            case STATE_LEARNING:
                mWarmTaps++;
                if (mWarmTaps >= WARM_UP_TAPS) {
                    mBaseline = mWarmTakeBacks * 100f / mWarmTaps;
                    clearWindow();
                    mState = STATE_ACTIVE;
                }
                break;
            case STATE_ACTIVE:
                if (bit(mWindowNext)) {
                    setBit(mWindowNext, false);
                    mWindowTakeBacks--;
                }
                mWindowNext = (mWindowNext + 1) % DRIFT_WINDOW;
                if (mWindowFill < DRIFT_WINDOW)
                    mWindowFill++;
                checkDrift();
                break;
            default:
                break;
        }
    }

    /** Leaves a pause, or any state, for a fresh warm-up. */
    public void restartWarmUp() {
        mState = STATE_LEARNING;
        mWarmTaps = 0;
        mWarmTakeBacks = 0;
        mBaseline = 0f;
        clearWindow();
    }

    /** Pauses correction now, as the drift alarm would. */
    public void pause() {
        mState = STATE_PAUSED;
    }

    private void checkDrift() {
        if (mWindowFill < DRIFT_WINDOW)
            return;
        if (isDrift(mWindowTakeBacks, mBaseline))
            mState = STATE_PAUSED;
    }

    /**
     * Whether {@code takeBacks} in a full window is a rise worth pausing for against a baseline
     * of {@code baseline} per 100 taps: at least {@link #DRIFT_RISE} times the expected count,
     * and unlikely by chance alone.
     */
    static boolean isDrift(int takeBacks, float baseline) {
        double expected = baseline * DRIFT_WINDOW / 100.0;
        double chance = expected + DRIFT_CHANCE_SIGMAS * Math.sqrt(Math.max(expected, 1.0))
            + DRIFT_CHANCE_MARGIN;
        return takeBacks >= expected * DRIFT_RISE - 1e-9 && takeBacks >= chance - 1e-9;
    }

    private void clearWindow() {
        for (int i = 0; i < WINDOW_WORDS; i++) mWindow[i] = 0L;
        mWindowNext = 0;
        mWindowFill = 0;
        mWindowTakeBacks = 0;
    }

    private boolean bit(int slot) {
        return (mWindow[slot >>> 6] & (1L << (slot & 63))) != 0;
    }

    private void setBit(int slot, boolean on) {
        if (on) mWindow[slot >>> 6] |= 1L << (slot & 63);
        else mWindow[slot >>> 6] &= ~(1L << (slot & 63));
    }
}
