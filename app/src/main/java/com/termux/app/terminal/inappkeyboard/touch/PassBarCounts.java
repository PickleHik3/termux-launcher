package com.termux.app.terminal.inappkeyboard.touch;

import java.util.Arrays;

/**
 * Per-key counts for judging the engine on a device: correctable taps by the key that was sent,
 * and take-backs twice, once by the key that was meant and once by the key that was hit, all
 * kept apart for the warm-up and for active correction. A key's take-back rate in each phase is
 * its take-backs (by meant key) over its taps; the by-hit counts say where those misses landed,
 * so "meant space, hit {@code b}" reads straight from the file. Paused taps count in neither
 * phase. Nothing reads these but a person; they hold no order and no characters.
 *
 * <p>The counts belong to one warm-up: the engine numbers each warm-up ({@link #epoch()}) and a
 * grid whose counts carry an older number is cleared the next time it is touched or saved.
 */
public final class PassBarCounts {

    private final int mKeyCount;
    private int mEpoch;
    private final int[] mWarmTaps;
    private final int[] mActiveTaps;
    private final int[] mWarmTakeBacks;
    private final int[] mActiveTakeBacks;
    private final int[] mWarmTakeBacksHit;
    private final int[] mActiveTakeBacksHit;

    public PassBarCounts(int keyCount) {
        this(keyCount, 0, null, null, null, null, null, null);
    }

    /** Restores counts; arrays of the wrong length yield empty ones. */
    public PassBarCounts(int keyCount, int epoch, int[] warmTaps, int[] activeTaps,
                         int[] warmTakeBacks, int[] activeTakeBacks, int[] warmTakeBacksHit,
                         int[] activeTakeBacksHit) {
        mKeyCount = keyCount;
        boolean ok = fits(warmTaps, keyCount) && fits(activeTaps, keyCount)
            && fits(warmTakeBacks, keyCount) && fits(activeTakeBacks, keyCount)
            && fits(warmTakeBacksHit, keyCount) && fits(activeTakeBacksHit, keyCount);
        mEpoch = ok ? epoch : 0;
        mWarmTaps = ok ? warmTaps.clone() : new int[keyCount];
        mActiveTaps = ok ? activeTaps.clone() : new int[keyCount];
        mWarmTakeBacks = ok ? warmTakeBacks.clone() : new int[keyCount];
        mActiveTakeBacks = ok ? activeTakeBacks.clone() : new int[keyCount];
        mWarmTakeBacksHit = ok ? warmTakeBacksHit.clone() : new int[keyCount];
        mActiveTakeBacksHit = ok ? activeTakeBacksHit.clone() : new int[keyCount];
    }

    public int epoch() {
        return mEpoch;
    }

    /** Clears the counts when they belong to an earlier warm-up than {@code epoch}. */
    public void sync(int epoch) {
        if (mEpoch == epoch)
            return;
        mEpoch = epoch;
        Arrays.fill(mWarmTaps, 0);
        Arrays.fill(mActiveTaps, 0);
        Arrays.fill(mWarmTakeBacks, 0);
        Arrays.fill(mActiveTakeBacks, 0);
        Arrays.fill(mWarmTakeBacksHit, 0);
        Arrays.fill(mActiveTakeBacksHit, 0);
    }

    void countTap(int key, int gateState) {
        bump(gateState == LearningGate.STATE_LEARNING ? mWarmTaps
            : gateState == LearningGate.STATE_ACTIVE ? mActiveTaps : null, key);
    }

    /** A take-back: meant for {@code meantKey}, landed on {@code hitKey}. */
    void countTakeBack(int meantKey, int hitKey, int gateState) {
        boolean warm = gateState == LearningGate.STATE_LEARNING;
        boolean active = gateState == LearningGate.STATE_ACTIVE;
        bump(warm ? mWarmTakeBacks : active ? mActiveTakeBacks : null, meantKey);
        bump(warm ? mWarmTakeBacksHit : active ? mActiveTakeBacksHit : null, hitKey);
    }

    public int[] warmTaps() {
        return mWarmTaps.clone();
    }

    public int[] activeTaps() {
        return mActiveTaps.clone();
    }

    /** Warm-up take-backs, by the key meant. */
    public int[] warmTakeBacks() {
        return mWarmTakeBacks.clone();
    }

    /** Active take-backs, by the key meant. */
    public int[] activeTakeBacks() {
        return mActiveTakeBacks.clone();
    }

    /** Warm-up take-backs, by the key hit. */
    public int[] warmTakeBacksHit() {
        return mWarmTakeBacksHit.clone();
    }

    /** Active take-backs, by the key hit. */
    public int[] activeTakeBacksHit() {
        return mActiveTakeBacksHit.clone();
    }

    private void bump(int[] counts, int key) {
        if (counts != null && key >= 0 && key < mKeyCount && counts[key] < Integer.MAX_VALUE)
            counts[key]++;
    }

    private static boolean fits(int[] values, int keyCount) {
        return values != null && values.length == keyCount;
    }
}
