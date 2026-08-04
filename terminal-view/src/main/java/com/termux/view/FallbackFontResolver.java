package com.termux.view;

/**
 * Ordered fallback-face selection for one code point, memoized.
 *
 * <p>The chain is only consulted when the primary face for the run genuinely lacks the glyph, and
 * the first configured face that has it wins; if none does, the answer is {@link #NO_OVERRIDE} and
 * Android's own platform fallback is left to do what it already does. Coverage probing is
 * expensive — {@code Paint.hasGlyph} shapes a string — and the render loop asks per cell, so every
 * answer is remembered.
 *
 * <p>The memo is a fixed open-addressed table keyed by code point and SGR face, sized once. It
 * cannot grow: when it fills past {@link #LOAD_LIMIT_NUMERATOR}/{@link #LOAD_LIMIT_DENOMINATOR} of
 * capacity the whole table is dropped and repopulated on demand, which costs a handful of probes
 * on a screen whose repertoire has genuinely outgrown the table and nothing at all otherwise. A
 * renderer is rebuilt whenever the faces or the chain change, so there is no other invalidation to
 * get wrong.
 *
 * <p>Probing is behind {@link Coverage} so the ordering, precedence and eviction policy are unit
 * tested on the JVM without a real {@code Paint} or {@code Typeface}.
 */
final class FallbackFontResolver {

    /** The primary face, and after it Android's platform fallback, is used unchanged. */
    static final int NO_OVERRIDE = -1;

    static final int DEFAULT_CAPACITY = 512;

    static final int LOAD_LIMIT_NUMERATOR = 3;

    static final int LOAD_LIMIT_DENOMINATOR = 4;

    /** Whether one candidate face covers a code point. */
    interface Coverage {
        /**
         * @param faceStyle the run's SGR face as bold | italic &lt;&lt; 1.
         * @param faceIndex {@link #NO_OVERRIDE} for the primary face, otherwise a chain position.
         */
        boolean hasGlyph(int faceStyle, int faceIndex, int codePoint);
    }

    private final int mChainLength;
    private final int mMask;
    private final int mLoadLimit;
    private final int[] mKeys;
    private final short[] mValues;
    private int mSize;

    FallbackFontResolver(int chainLength) {
        this(chainLength, DEFAULT_CAPACITY);
    }

    FallbackFontResolver(int chainLength, int capacity) {
        if (Integer.bitCount(capacity) != 1 || capacity < 4)
            throw new IllegalArgumentException("Capacity must be a power of two of at least four");
        mChainLength = Math.max(0, Math.min(chainLength, Short.MAX_VALUE));
        mMask = capacity - 1;
        mLoadLimit = capacity * LOAD_LIMIT_NUMERATOR / LOAD_LIMIT_DENOMINATOR;
        mKeys = new int[capacity];
        mValues = new short[capacity];
    }

    /**
     * The chain position of the face that should draw {@code codePoint}, or {@link #NO_OVERRIDE}.
     */
    int resolve(int faceStyle, int codePoint, Coverage coverage) {
        if (mChainLength == 0) return NO_OVERRIDE;
        // Zero marks an empty slot, so every stored key is shifted past it.
        final int key = ((codePoint << 2) | (faceStyle & 3)) + 1;
        int slot = slotFor(key);
        while (mKeys[slot] != 0) {
            if (mKeys[slot] == key) return mValues[slot];
            slot = (slot + 1) & mMask;
        }
        final int resolved = probe(faceStyle, codePoint, coverage);
        if (mSize >= mLoadLimit) {
            clear();
            slot = slotFor(key);
        }
        mKeys[slot] = key;
        mValues[slot] = (short) resolved;
        mSize++;
        return resolved;
    }

    private int probe(int faceStyle, int codePoint, Coverage coverage) {
        if (coverage.hasGlyph(faceStyle, NO_OVERRIDE, codePoint)) return NO_OVERRIDE;
        for (int index = 0; index < mChainLength; index++) {
            if (coverage.hasGlyph(faceStyle, index, codePoint)) return index;
        }
        return NO_OVERRIDE;
    }

    private int slotFor(int key) {
        int hash = key * 0x9E3779B1;
        hash ^= hash >>> 16;
        return hash & mMask;
    }

    /** Forget every memoized answer without releasing the table. */
    void clear() {
        java.util.Arrays.fill(mKeys, 0);
        mSize = 0;
    }

    int size() {
        return mSize;
    }

    int capacity() {
        return mKeys.length;
    }

    int chainLength() {
        return mChainLength;
    }
}
