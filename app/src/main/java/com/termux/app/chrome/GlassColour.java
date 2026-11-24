package com.termux.app.chrome;

import androidx.annotation.NonNull;

/**
 * The glass's colour: Vibrancy (percent) and the frame tint ({@link GlassFrameTint}), with the
 * 4x5 matrix they make ({@link GlassVibrancy#bakeMatrix}) built the first time it is asked for.
 * Immutable. One is made only when the colour changes; per-frame callers ask {@link #is} and keep
 * the one they hold, so nothing allocates at rest.
 */
public final class GlassColour {

    /** The colour that leaves the backdrop as it is. */
    public static final GlassColour NONE =
        new GlassColour(GlassVibrancy.NO_VIBRANCY, GlassFrameTint.NONE);

    /** Vibrancy held to the control's range, 70 to 150. */
    public final int vibrancyPercent;
    public final int frameTint;
    private float[] mMatrix;

    private GlassColour(int vibrancyPercent, int frameTint) {
        this.vibrancyPercent = GlassVibrancy.clampPercent(vibrancyPercent);
        this.frameTint = frameTint;
    }

    @NonNull
    public static GlassColour of(int vibrancyPercent, int frameTint) {
        if (NONE.is(vibrancyPercent, frameTint)) return NONE;
        return new GlassColour(vibrancyPercent, frameTint);
    }

    /** Whether this is the colour {@code vibrancyPercent} and {@code frameTint} describe. */
    public boolean is(int vibrancyPercent, int frameTint) {
        return this.vibrancyPercent == GlassVibrancy.clampPercent(vibrancyPercent)
            && this.frameTint == frameTint;
    }

    /** True when the colour changes the backdrop at all. */
    public boolean changesBackdrop() {
        return vibrancyPercent != GlassVibrancy.NO_VIBRANCY || frameTint != GlassFrameTint.NONE;
    }

    /** The colour matrix, built once. Read only: callers never write into it. */
    @NonNull
    public synchronized float[] matrix() {
        float[] m = mMatrix;
        if (m == null) {
            m = GlassVibrancy.bakeMatrix(vibrancyPercent, frameTint);
            mMatrix = m;
        }
        return m;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GlassColour)) return false;
        GlassColour that = (GlassColour) o;
        return vibrancyPercent == that.vibrancyPercent && frameTint == that.frameTint;
    }

    @Override
    public int hashCode() {
        return vibrancyPercent * 31 + frameTint;
    }
}
