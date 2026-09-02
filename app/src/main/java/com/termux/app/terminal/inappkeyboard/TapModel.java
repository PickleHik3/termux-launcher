package com.termux.app.terminal.inappkeyboard;

import juloo.keyboard2.TapGeometry;

import java.util.Arrays;

/**
 * Where a user's taps land on one rendered key grid, and the key a press near a boundary
 * should resolve to. Pure arithmetic over {@link TapGeometry}; no Android types.
 *
 * <p>Per key it keeps a tap count and the summed offset of each tap from the key's centre, in
 * key-width units. A press that the static grid resolved to key {@code r} is moved to a
 * neighbour {@code n} when the point, shifted back by the pair's estimated bias, lands inside
 * {@code n}. The pair bias is the mean of both keys' shrunk centroid offsets, so the boundary
 * between any two keys is one consistent line seen from either side: a press cannot cross from
 * {@code r} into {@code n} at a spot where a press in {@code n} would cross back.
 *
 * <p>Observations are always recorded against the key the static grid chose, never the key the
 * model chose, so the model never trains on its own output.
 */
public final class TapModel {

    /** Taps a key needs before its estimate carries half weight. */
    static final float SHRINK_TAPS = 20f;
    /** A boundary never moves further than this fraction of the narrower key. */
    static final float MAX_SHIFT_FRACTION = 0.3f;
    /** When a key reaches this many taps its statistics are halved so the estimate can adapt. */
    static final int FORGET_AT = 400;

    private final int mKeyCount;
    private final float[] mCount;
    private final float[] mSumX;
    private final float[] mSumY;

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

    /** Total taps this model has learned from, after forgetting. */
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
     * Records a released tap. Ignored for swipes and for keys whose centre value is not a plain
     * character, so deliberate off-centre presses never teach the model anything.
     */
    public void observe(TapGeometry geometry, int rawIndex, float x, float y, boolean swiped) {
        if (swiped || !valid(geometry, rawIndex) || !geometry.isChar[rawIndex])
            return;
        if (mCount[rawIndex] >= FORGET_AT) {
            mCount[rawIndex] *= 0.5f;
            mSumX[rawIndex] *= 0.5f;
            mSumY[rawIndex] *= 0.5f;
        }
        mCount[rawIndex] += 1f;
        mSumX[rawIndex] += x - geometry.centerX(rawIndex);
        mSumY[rawIndex] += y - geometry.centerY(rawIndex);
    }

    /**
     * The key a press at ({@code x}, {@code y}) that the static grid resolved to
     * {@code rawIndex} should commit. Returns {@code rawIndex} whenever no character neighbour
     * claims the corrected point.
     */
    public int resolve(TapGeometry geometry, int rawIndex, float x, float y) {
        if (!valid(geometry, rawIndex) || !geometry.isChar[rawIndex])
            return rawIndex;
        int best = rawIndex;
        float bestDepth = 0f;
        for (int n = 0; n < geometry.keyCount; n++) {
            if (n == rawIndex || !geometry.isChar[n])
                continue;
            float bx = clamp(pairBiasX(rawIndex, n),
                MAX_SHIFT_FRACTION * Math.min(geometry.width(rawIndex), geometry.width(n)));
            float by = clamp(pairBiasY(rawIndex, n),
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

    /** Shrunk mean horizontal offset of taps on key {@code i}, in key widths. */
    public float biasX(int i) {
        return mCount[i] <= 0f ? 0f : mSumX[i] / mCount[i] * shrink(mCount[i]);
    }

    public float biasY(int i) {
        return mCount[i] <= 0f ? 0f : mSumY[i] / mCount[i] * shrink(mCount[i]);
    }

    private float pairBiasX(int a, int b) {
        return (biasX(a) + biasX(b)) * 0.5f;
    }

    private float pairBiasY(int a, int b) {
        return (biasY(a) + biasY(b)) * 0.5f;
    }

    private static float shrink(float count) {
        return count / (count + SHRINK_TAPS);
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
