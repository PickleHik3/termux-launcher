package com.termux.app.chrome;

/**
 * The skip rule for a frost pass: a pass whose every input (rects, corner, visibility, look, the
 * frost source) hashes to what the last landed pass saw, with nothing marked dirty since, has
 * nothing to change and is skipped. Any input that moves, or a dirty mark (wallpaper, scheme, a
 * frame landing), runs the pass.
 *
 * <p>The key is two independent 64-bit running hashes, so a pass allocates nothing for it. Pure.
 */
public final class FrostPassGate {

    private static final long SEED_A = 0xcbf29ce484222325L;
    private static final long PRIME_A = 0x100000001b3L;
    private static final long SEED_B = 0x9E3779B97F4A7C15L;

    private long mCurA;
    private long mCurB;
    private long mLandedA;
    private long mLandedB;
    private boolean mLanded;

    /** A pass starts building its key. */
    public void begin() {
        mCurA = SEED_A;
        mCurB = SEED_B;
    }

    public FrostPassGate add(long value) {
        mCurA = (mCurA ^ value) * PRIME_A;
        long b = (mCurB + value) * 0xBF58476D1CE4E5B9L;
        mCurB = b ^ (b >>> 31);
        return this;
    }

    public FrostPassGate add(boolean value) {
        return add(value ? 1L : 0L);
    }

    public FrostPassGate add(float value) {
        return add((long) Float.floatToIntBits(value));
    }

    /**
     * Whether this pass can be skipped: its key is the landed pass's and nothing is dirty. When it
     * is not, the caller runs the pass and calls {@link #landed} or {@link #invalidate}.
     */
    public boolean unchanged(boolean dirty) {
        return !dirty && mLanded && mCurA == mLandedA && mCurB == mLandedB;
    }

    /** The pass ran and put the frost where it stays: its key is the one to match. */
    public void landed() {
        mLandedA = mCurA;
        mLandedB = mCurB;
        mLanded = true;
    }

    /** The pass left nothing to match (hidden, no frame yet): the next one runs. */
    public void invalidate() {
        mLanded = false;
    }
}
