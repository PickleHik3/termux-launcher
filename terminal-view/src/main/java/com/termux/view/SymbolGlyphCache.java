package com.termux.view;

/**
 * Whether the symbol face a code point maps to actually has a glyph for it, memoized.
 *
 * <p>A {@code symbol_map} hit ends the search in the draw path: the fallback chain is skipped for
 * that cell because the user said which font draws it. A range is routinely wider than the font's
 * cmap — the app's own managed config maps the whole private-use area to one symbols face — so
 * without this a code point inside a hole in that face drew tofu while a face that had the glyph
 * sat unconsulted. Asking is {@link android.graphics.Paint#hasGlyph}, which shapes a string, and
 * the render loop asks once per cell, so every answer is remembered.
 *
 * <p>The memo is a fixed open-addressed table keyed by code point and symbol face, sized once and
 * never grown: when it fills past {@link #LOAD_LIMIT_NUMERATOR}/{@link #LOAD_LIMIT_DENOMINATOR} of
 * capacity the whole table is dropped and repopulated on demand. A renderer is rebuilt whenever
 * its maps change, so there is no other invalidation to get wrong.
 *
 * <p>Probing is behind {@link Coverage} so the policy is unit tested on the JVM without a real
 * {@code Paint}, exactly as {@link FallbackFontResolver} is.
 */
final class SymbolGlyphCache {

    static final int DEFAULT_CAPACITY = 256;

    static final int LOAD_LIMIT_NUMERATOR = 3;

    static final int LOAD_LIMIT_DENOMINATOR = 4;

    /** The most distinct symbol faces a key can distinguish; beyond this the answer is not kept. */
    static final int MAX_FACES = 64;

    /** Whether one symbol face has a glyph for a code point. */
    interface Coverage {
        boolean hasGlyph(int faceIndex, int codePoint);
    }

    private final int mMask;
    private final int mLoadLimit;
    private final int[] mKeys;
    private final boolean[] mValues;
    private int mSize;

    SymbolGlyphCache() {
        this(DEFAULT_CAPACITY);
    }

    SymbolGlyphCache(int capacity) {
        if (Integer.bitCount(capacity) != 1 || capacity < 4)
            throw new IllegalArgumentException("Capacity must be a power of two of at least four");
        mMask = capacity - 1;
        mLoadLimit = capacity * LOAD_LIMIT_NUMERATOR / LOAD_LIMIT_DENOMINATOR;
        mKeys = new int[capacity];
        mValues = new boolean[capacity];
    }

    /** True when this face can draw the code point, so the symbol map may claim the cell. */
    boolean covers(int faceIndex, int codePoint, Coverage coverage) {
        if (faceIndex < 0 || faceIndex >= MAX_FACES) return coverage.hasGlyph(faceIndex, codePoint);
        // Zero marks an empty slot, so every stored key is shifted past it.
        final int key = ((codePoint * MAX_FACES) | faceIndex) + 1;
        int slot = slotFor(key);
        while (mKeys[slot] != 0) {
            if (mKeys[slot] == key) return mValues[slot];
            slot = (slot + 1) & mMask;
        }
        final boolean covered = coverage.hasGlyph(faceIndex, codePoint);
        if (mSize >= mLoadLimit) {
            clear();
            slot = slotFor(key);
        }
        mKeys[slot] = key;
        mValues[slot] = covered;
        mSize++;
        return covered;
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
}
