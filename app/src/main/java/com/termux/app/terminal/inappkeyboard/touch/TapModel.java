package com.termux.app.terminal.inappkeyboard.touch;

import juloo.keyboard2.TapGeometry;

import java.util.Arrays;

/**
 * Where a user's taps land on one rendered key grid, and the key a press near a border should
 * commit. Pure arithmetic over {@link TapGeometry}; no Android types.
 *
 * <p>Per key it keeps a weighted tap count and the summed offset of each tap from the key's
 * centre: horizontally in key widths, vertically in that key's heights, so a resize that changes
 * the keys' aspect keeps what was learned. A key's estimate is shrunk towards its group's
 * ({@link KeyGroups}), so a key with few taps of its own borrows the offset its neighbours share.
 * Wide keys learn their vertical offset only.
 *
 * <p>A press that the static grid gave to key {@code r} is moved to a correctable neighbour
 * {@code n} when the point, shifted back by the pair's bias, lands inside {@code n}. The pair
 * bias is the mean of both keys' estimates, so the border between two keys is one line seen from
 * either side, and it never moves more than {@link #MAX_SHIFT_FRACTION} of the narrower key: the
 * centre of every key stays that key.
 */
public final class TapModel {

    /** Taps a key needs before its own estimate carries half the weight. */
    static final float SHRINK_TAPS = 20f;
    /** A border never moves further than this fraction of the narrower key, per axis. */
    static final float MAX_SHIFT_FRACTION = 0.3f;
    /** When a key reaches this weight its statistics are halved so the estimate can adapt. */
    static final int FORGET_AT = 400;

    private final int mKeyCount;
    private final float[] mCount;
    private final float[] mSumX;
    private final float[] mSumY;

    /** Per-group prior offsets, rebuilt after any observation; sized for one group per key. */
    private final float[] mPriorX;
    private final float[] mPriorY;
    private boolean mPriorsValid;
    private TapGeometry mGroupsFor;
    private KeyGroups mGroups;

    public TapModel(int keyCount) {
        this(new float[keyCount], new float[keyCount], new float[keyCount]);
    }

    public TapModel(float[] count, float[] sumX, float[] sumY) {
        if (count.length != sumX.length || count.length != sumY.length)
            throw new IllegalArgumentException("mismatched statistics");
        mKeyCount = count.length;
        mCount = count.clone();
        mSumX = sumX.clone();
        mSumY = sumY.clone();
        mPriorX = new float[mKeyCount];
        mPriorY = new float[mKeyCount];
    }

    public int keyCount() {
        return mKeyCount;
    }

    public float[] counts() {
        return mCount.clone();
    }

    public float[] sumX() {
        return mSumX.clone();
    }

    public float[] sumY() {
        return mSumY.clone();
    }

    /** Total weight this model has learned from, after forgetting. */
    public float totalTaps() {
        float total = 0f;
        for (float c : mCount) total += c;
        return total;
    }

    public boolean isEmpty() {
        for (float c : mCount)
            if (c > 0f) return false;
        return true;
    }

    /**
     * Files a tap at ({@code x}, {@code y}) as meant for key {@code index}, counted
     * {@code weight} times. Returns false, recording nothing, for a key that is not correctable.
     */
    public boolean observe(TapGeometry geometry, int index, float x, float y, float weight) {
        if (!valid(geometry, index) || !geometry.correctable[index] || !(weight > 0f)
            || !(geometry.height(index) > 0f))
            return false;
        KeyGroups groups = groups(geometry);
        if (mCount[index] >= FORGET_AT) {
            mCount[index] *= 0.5f;
            mSumX[index] *= 0.5f;
            mSumY[index] *= 0.5f;
        }
        mCount[index] += weight;
        if (!groups.wide[index])
            mSumX[index] += weight * (x - geometry.centerX(index));
        mSumY[index] += weight * (y - geometry.centerY(index)) / geometry.height(index);
        mPriorsValid = false;
        return true;
    }

    /**
     * The key a press at ({@code x}, {@code y}) that the static grid gave to {@code rawIndex}
     * should commit. Returns {@code rawIndex} whenever no correctable neighbour claims the
     * shifted point, and always for a key that is not correctable.
     */
    public int resolve(TapGeometry geometry, int rawIndex, float x, float y) {
        if (!valid(geometry, rawIndex) || !geometry.correctable[rawIndex])
            return rawIndex;
        ensurePriors(geometry);
        int best = rawIndex;
        float bestDepth = 0f;
        for (int n = 0; n < geometry.keyCount; n++) {
            if (n == rawIndex || !geometry.correctable[n])
                continue;
            float bx = clamp((ownBiasX(rawIndex) + ownBiasX(n)) * 0.5f,
                MAX_SHIFT_FRACTION * Math.min(geometry.width(rawIndex), geometry.width(n)));
            // Stored in each key's heights; the pair's border moves in the grid's units.
            float by = clamp((ownBiasY(rawIndex) * geometry.height(rawIndex)
                    + ownBiasY(n) * geometry.height(n)) * 0.5f,
                MAX_SHIFT_FRACTION * Math.min(geometry.height(rawIndex), geometry.height(n)));
            if (bx == 0f && by == 0f)
                continue;
            float qx = x - bx;
            float qy = y - by;
            if (!geometry.contains(n, qx, qy))
                continue;
            float depth = Math.min(
                Math.min(qx - geometry.left[n], geometry.right[n] - qx),
                Math.min(qy - geometry.top[n], geometry.bottom[n] - qy));
            if (depth > bestDepth) {
                bestDepth = depth;
                best = n;
            }
        }
        return best;
    }

    /** Key {@code i}'s horizontal estimate, in key widths, shrunk towards its group's. */
    public float biasX(TapGeometry geometry, int i) {
        if (!valid(geometry, i))
            return 0f;
        ensurePriors(geometry);
        return ownBiasX(i);
    }

    /**
     * Key {@code i}'s vertical estimate on {@code geometry}, in key widths (the grid's units),
     * shrunk towards its group's. It is learned in key heights, so the same model answers in
     * proportion to the key on a taller or shorter grid.
     */
    public float biasY(TapGeometry geometry, int i) {
        if (!valid(geometry, i))
            return 0f;
        ensurePriors(geometry);
        return ownBiasY(i) * geometry.height(i);
    }

    private float ownBiasX(int i) {
        int g = mGroups.group[i];
        if (g < 0 || mGroups.wide[i])
            return 0f;
        return (mSumX[i] + SHRINK_TAPS * mPriorX[g]) / (mCount[i] + SHRINK_TAPS);
    }

    /** In key heights. */
    private float ownBiasY(int i) {
        int g = mGroups.group[i];
        if (g < 0)
            return 0f;
        return (mSumY[i] + SHRINK_TAPS * mPriorY[g]) / (mCount[i] + SHRINK_TAPS);
    }

    /**
     * Rebuilds each group's pooled offset: the group's summed offsets over its summed weight,
     * itself shrunk towards zero. A group of one has no neighbours to borrow from, so its prior
     * is zero and the key shrinks towards zero as a lone key always did.
     */
    private void ensurePriors(TapGeometry geometry) {
        KeyGroups groups = groups(geometry);
        if (mPriorsValid)
            return;
        int count = groups.groupCount;
        Arrays.fill(mPriorX, 0, count, 0f);
        Arrays.fill(mPriorY, 0, count, 0f);
        for (int g = 0; g < count; g++) {
            if (groups.members[g] < 2)
                continue;
            float n = 0f, sx = 0f, sy = 0f;
            for (int i = 0; i < mKeyCount; i++) {
                if (groups.group[i] != g)
                    continue;
                n += mCount[i];
                sx += mSumX[i];
                sy += mSumY[i];
            }
            mPriorX[g] = sx / (n + SHRINK_TAPS);
            mPriorY[g] = sy / (n + SHRINK_TAPS);
        }
        mPriorsValid = true;
    }

    private KeyGroups groups(TapGeometry geometry) {
        if (mGroupsFor != geometry) {
            mGroups = KeyGroups.of(geometry);
            mGroupsFor = geometry;
            mPriorsValid = false;
        }
        return mGroups;
    }

    private static float clamp(float value, float limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private boolean valid(TapGeometry geometry, int index) {
        return geometry != null && geometry.keyCount == mKeyCount
            && index >= 0 && index < mKeyCount;
    }

    @Override
    public String toString() {
        return "TapModel{keys=" + mKeyCount + ", taps=" + totalTaps()
            + ", counts=" + Arrays.toString(mCount) + "}";
    }
}
