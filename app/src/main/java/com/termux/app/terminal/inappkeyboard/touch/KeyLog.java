package com.termux.app.terminal.inappkeyboard.touch;

/**
 * What was recorded about each key's taps, kept for study and acted on by nothing: landing
 * position, press duration, contact size and the gap since the previous tap, plus where each
 * take-back into the key landed and which key it came from.
 *
 * <p>Each key keeps its own bounded bucket, overwritten oldest first, and the buckets share no
 * clock and no sequence: what was typed, and in which order, cannot be rebuilt from them.
 * Values are stored as shorts — horizontal offsets and sizes in thousandths of a key width,
 * vertical offsets in thousandths of the key's height, times in milliseconds clamped to
 * {@link Short#MAX_VALUE} — so the whole log for a 60-key grid holds
 * about 12.5 KB.
 */
public final class KeyLog {

    /** Recent taps kept per key. */
    public static final int SAMPLES_PER_KEY = 16;
    /** dx, dy, duration, contact size, gap. */
    public static final int SAMPLE_FIELDS = 5;
    /** Recent take-backs kept per key they were meant for. */
    public static final int TAKE_BACKS_PER_KEY = 8;
    /** The key that was hit, dx, dy. */
    public static final int TAKE_BACK_FIELDS = 3;

    private static final float MILLI = 1000f;

    private final int mKeyCount;
    private final short[] mSamples;
    private final short[] mSampleFill;
    private final int[] mSampleNext;
    private final short[] mTakeBacks;
    private final short[] mTakeBackFill;
    private final int[] mTakeBackNext;

    public KeyLog(int keyCount) {
        this(keyCount, new short[keyCount * SAMPLES_PER_KEY * SAMPLE_FIELDS],
            new short[keyCount], new short[keyCount * TAKE_BACKS_PER_KEY * TAKE_BACK_FIELDS],
            new short[keyCount]);
    }

    /** Restores a log; arrays of the wrong length yield an empty one. */
    public KeyLog(int keyCount, short[] samples, short[] sampleFill, short[] takeBacks,
                  short[] takeBackFill) {
        mKeyCount = keyCount;
        boolean ok = samples != null && sampleFill != null && takeBacks != null
            && takeBackFill != null
            && samples.length == keyCount * SAMPLES_PER_KEY * SAMPLE_FIELDS
            && sampleFill.length == keyCount
            && takeBacks.length == keyCount * TAKE_BACKS_PER_KEY * TAKE_BACK_FIELDS
            && takeBackFill.length == keyCount;
        mSamples = ok ? samples.clone() : new short[keyCount * SAMPLES_PER_KEY * SAMPLE_FIELDS];
        mSampleFill = ok ? sampleFill.clone() : new short[keyCount];
        mTakeBacks = ok ? takeBacks.clone()
            : new short[keyCount * TAKE_BACKS_PER_KEY * TAKE_BACK_FIELDS];
        mTakeBackFill = ok ? takeBackFill.clone() : new short[keyCount];
        mSampleNext = new int[keyCount];
        mTakeBackNext = new int[keyCount];
        for (int i = 0; i < keyCount; i++) {
            mSampleFill[i] = (short) Math.max(0, Math.min(SAMPLES_PER_KEY, mSampleFill[i]));
            mTakeBackFill[i] = (short) Math.max(0, Math.min(TAKE_BACKS_PER_KEY, mTakeBackFill[i]));
            mSampleNext[i] = mSampleFill[i] % SAMPLES_PER_KEY;
            mTakeBackNext[i] = mTakeBackFill[i] % TAKE_BACKS_PER_KEY;
        }
    }

    public int keyCount() {
        return mKeyCount;
    }

    /**
     * Records a tap on {@code key}: its offset from the key's centre ({@code dx} in key widths,
     * {@code dy} in the key's heights), its contact size in key widths, and its duration and the gap since the previous tap in milliseconds (a negative
     * gap means there was none).
     */
    public void addSample(int key, float dx, float dy, long durationMs, float contactSize,
                          long gapMs) {
        if (key < 0 || key >= mKeyCount)
            return;
        int at = (key * SAMPLES_PER_KEY + mSampleNext[key]) * SAMPLE_FIELDS;
        mSamples[at] = milli(dx);
        mSamples[at + 1] = milli(dy);
        mSamples[at + 2] = millis(durationMs);
        mSamples[at + 3] = milli(contactSize);
        mSamples[at + 4] = gapMs < 0 ? -1 : millis(gapMs);
        mSampleNext[key] = (mSampleNext[key] + 1) % SAMPLES_PER_KEY;
        if (mSampleFill[key] < SAMPLES_PER_KEY)
            mSampleFill[key]++;
    }

    /**
     * Records a take-back: a tap that hit {@code fromKey} at an offset from {@code toKey}'s
     * centre, {@code dx} in key widths and {@code dy} in {@code toKey}'s heights.
     */
    public void addTakeBack(int toKey, int fromKey, float dx, float dy) {
        if (toKey < 0 || toKey >= mKeyCount || fromKey < 0 || fromKey >= mKeyCount)
            return;
        int at = (toKey * TAKE_BACKS_PER_KEY + mTakeBackNext[toKey]) * TAKE_BACK_FIELDS;
        mTakeBacks[at] = (short) fromKey;
        mTakeBacks[at + 1] = milli(dx);
        mTakeBacks[at + 2] = milli(dy);
        mTakeBackNext[toKey] = (mTakeBackNext[toKey] + 1) % TAKE_BACKS_PER_KEY;
        if (mTakeBackFill[toKey] < TAKE_BACKS_PER_KEY)
            mTakeBackFill[toKey]++;
    }

    public int sampleCount(int key) {
        return key < 0 || key >= mKeyCount ? 0 : mSampleFill[key];
    }

    public int takeBackCount(int key) {
        return key < 0 || key >= mKeyCount ? 0 : mTakeBackFill[key];
    }

    public boolean isEmpty() {
        for (int i = 0; i < mKeyCount; i++)
            if (mSampleFill[i] > 0 || mTakeBackFill[i] > 0)
                return false;
        return true;
    }

    public short[] samples() {
        return mSamples.clone();
    }

    public short[] sampleFill() {
        return mSampleFill.clone();
    }

    public short[] takeBacks() {
        return mTakeBacks.clone();
    }

    public short[] takeBackFill() {
        return mTakeBackFill.clone();
    }

    private static short milli(float value) {
        if (Float.isNaN(value))
            return 0;
        return clampShort(Math.round(value * MILLI));
    }

    private static short millis(long value) {
        return clampShort(value);
    }

    private static short clampShort(long value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }
}
