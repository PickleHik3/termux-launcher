package com.termux.app.glass;

import androidx.annotation.NonNull;

import java.util.Arrays;

/**
 * What one band's legends have to stand on at a page's rest, as measured: the opaque face under
 * each row of the band, top to bottom (the dock has one row, the keyboard four), and the legend
 * tone chosen on its bare face ({@link #TONE_NONE}, {@link #TONE_DARK}, {@link #TONE_PALE}).
 * Immutable; a crossing blends two of them and measures nothing ({@link LegibilityBlend}).
 */
public final class LegibilityCorrection {

    public static final int TONE_NONE = 0;
    public static final int TONE_DARK = 1;
    public static final int TONE_PALE = 2;

    @NonNull private final int[] mFaces;
    private final int mTone;

    private LegibilityCorrection(@NonNull int[] faces, int tone) {
        mFaces = faces;
        mTone = tone;
    }

    /** A one-row correction: the dock's face. */
    @NonNull
    public static LegibilityCorrection ofFace(int face) {
        return new LegibilityCorrection(new int[] {face}, TONE_NONE);
    }

    /** A correction of several rows and a legend tone; the rows are copied. */
    @NonNull
    public static LegibilityCorrection ofRows(@NonNull int[] faces, int tone) {
        return new LegibilityCorrection(faces.clone(), tone);
    }

    /** How many rows it holds. */
    public int rows() {
        return mFaces.length;
    }

    /** The face under row {@code row}, clamped into range; transparent when there are no rows. */
    public int face(int row) {
        if (mFaces.length == 0) return 0;
        return mFaces[Math.max(0, Math.min(mFaces.length - 1, row))];
    }

    public int tone() {
        return mTone;
    }

    /** Whether this is the one-row correction for {@code face} with no tone, without building one. */
    boolean isFace(int face) {
        return mFaces.length == 1 && mFaces[0] == face && mTone == TONE_NONE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LegibilityCorrection)) return false;
        LegibilityCorrection that = (LegibilityCorrection) other;
        return mTone == that.mTone && Arrays.equals(mFaces, that.mFaces);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(mFaces) + mTone;
    }
}
