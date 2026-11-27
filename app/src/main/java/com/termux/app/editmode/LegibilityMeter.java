package com.termux.app.editmode;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.termux.app.chrome.LegibilityLevel;
import com.termux.app.chrome.OnGlass;

/**
 * The legibility meter edit mode shows while Opacity or Blur is dragged (#448): the contrast of
 * the surface's ink against the face it stands on, and, below readable, the smallest wallpaper
 * dim that would make it read. Nothing here refuses a value; the meter only says what the screen
 * now shows.
 *
 * <p>Pure and context free, so it unit tests on the JVM. The reading itself is the launcher's own
 * measurement ({@code ChromeInk#bandFaces}, the faces its ink is chosen on); this class only
 * judges it.</p>
 */
public final class LegibilityMeter {

    private LegibilityMeter() {}

    /** "Readable": body text at the default level, WCAG AA. The one constant for the meter. */
    public static final double READABLE = LegibilityLevel.DEFAULT.bodyText;

    /** What the launcher measured: the ink in use, the opaque faces it stands on, the dim now. */
    public static final class Reading {
        @ColorInt public final int ink;
        @NonNull public final int[] faces;
        /** The wallpaper dim now, 0 to 100. */
        public final int dim;

        public Reading(@ColorInt int ink, @NonNull int[] faces, int dim) {
            this.ink = ink;
            this.faces = faces.clone();
            this.dim = Math.max(0, Math.min(100, dim));
        }
    }

    /** What the meter shows. */
    public static final class State {
        /** The worst contrast across the faces, as a ratio ("4.5" means 4.5 to 1). */
        public final double ratio;
        /** True at or above {@link #READABLE}; false turns the meter amber. */
        public final boolean readable;
        /** The dim (percent) that reads, offered as Dim wallpaper; -1 when none is offered. */
        public final int dimTo;

        State(double ratio, boolean readable, int dimTo) {
            this.ratio = ratio;
            this.readable = readable;
            this.dimTo = dimTo;
        }

        /** Whether Dim wallpaper is on offer. */
        public boolean offersDim() {
            return dimTo >= 0;
        }
    }

    /** The meter for a reading, or null when nothing could be measured (no faces). */
    public static State of(Reading reading) {
        if (reading == null || reading.faces.length == 0) return null;
        double ratio = worst(reading.ink, reading.faces);
        boolean readable = ratio >= READABLE;
        return new State(ratio, readable, readable ? -1 : dimToRead(reading));
    }

    /**
     * Whether the meter is on screen for {@code state}: nothing measured, never; while a value is
     * being dragged, always; after the finger lifts, only while it is below readable, so the one
     * tap on Dim wallpaper stays within reach.
     */
    public static boolean shows(State state, boolean dragging) {
        if (state == null) return false;
        return dragging || !state.readable;
    }

    /** The lowest contrast of {@code ink} over {@code faces}. */
    static double worst(@ColorInt int ink, @NonNull int[] faces) {
        double worst = Double.MAX_VALUE;
        for (int face : faces) worst = Math.min(worst, OnGlass.ratio(ink, face));
        return worst;
    }

    /**
     * The smallest dim above the current one at which every face reads. The dim is black under
     * the glass, so a face is modelled as scaling toward black with it (the same share on every
     * channel); that is exact for a face that is mostly wallpaper and generous for pale glass, and
     * the meter measures again after the tap. -1 when no dim reads (dark ink, or a face already
     * black).
     */
    static int dimToRead(@NonNull Reading reading) {
        if (reading.dim >= 100) return -1;
        for (int dim = reading.dim + 1; dim <= 100; dim++) {
            float scale = (100f - dim) / (100f - reading.dim);
            int[] dimmed = new int[reading.faces.length];
            for (int i = 0; i < dimmed.length; i++) dimmed[i] = scaled(reading.faces[i], scale);
            if (worst(reading.ink, dimmed) >= READABLE) return dim;
        }
        return -1;
    }

    @ColorInt
    private static int scaled(@ColorInt int face, float scale) {
        return Color.rgb(Math.round(Color.red(face) * scale), Math.round(Color.green(face) * scale),
            Math.round(Color.blue(face) * scale));
    }
}
