package com.termux.app.surfaces;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * The global set's Depth slider over the six stored glass edge keys, as pure functions.
 *
 * <p>Depth runs 0 to 100 and owns the bend and the edge width: how thick the glass edge looks.
 * Bend is {@code floor(0.30 t)} and the width follows it, {@code R(2 bend / 3)}, so the bend is
 * always one and a half bands, as the references draw it, and Clear, Mist and Tint (27/18, 24/16,
 * 21/14) sit at 90, 80 and 70. Moving it writes only those two keys, so a saved Custom never
 * loses a value it does not touch. The edge light, specular, dispersion and clarity are on no
 * slider: each Look carries its own, a Custom keeps whatever it has, and the glass draws its rim
 * light as fixed material whatever the light and specular say. {@code R(v)} is
 * {@code floor(v + 0.5)}; both curves are done here in integers so they never land a hair off.</p>
 *
 * <p>Depth reads the stored pair back as the position whose curve output is nearest: squared
 * errors, each over the key's full range, summed; the smaller position on a tie. A pair on the
 * curve therefore reads as the smallest position that writes it. Reading never writes. The
 * shipped Looks sit on the curve (held by a test), so a Look's stop and its slider agree.</p>
 */
public final class GlassEdgeControls {

    private GlassEdgeControls() {}

    /** The slider's range. */
    public static final int MIN = 0;
    public static final int MAX = 100;

    /** No remembered position: the slider reads where its stored values put it. */
    public static final int NONE = -1;

    private static final int BEND_RANGE =
        TERMUX_APP.MAX_FANCIER_GLASS_BEND - TERMUX_APP.MIN_FANCIER_GLASS_BEND;
    private static final int WIDTH_RANGE =
        TERMUX_APP.MAX_FANCIER_GLASS_EDGE_WIDTH - TERMUX_APP.MIN_FANCIER_GLASS_EDGE_WIDTH;

    // ---------------------------------------------------------------------------------- Depth

    /** The bend a Depth position writes, in dp: {@code floor(0.30 t)}. */
    public static int bendForDepth(int depth) {
        int t = slider(depth);
        return clamp(3 * t / 10,
            TERMUX_APP.MIN_FANCIER_GLASS_BEND, TERMUX_APP.MAX_FANCIER_GLASS_BEND);
    }

    /** The edge width a Depth position writes, in dp: {@code R(2 bend / 3)}, never under 1. */
    public static int widthForDepth(int depth) {
        int bend = bendForDepth(depth);
        return clamp((4 * bend + 3) / 6,
            TERMUX_APP.MIN_FANCIER_GLASS_EDGE_WIDTH, TERMUX_APP.MAX_FANCIER_GLASS_EDGE_WIDTH);
    }

    /** Where Depth stands for a stored bend and edge width. */
    public static int depthFor(int bend, int width) {
        int best = MIN;
        long bestError = Long.MAX_VALUE;
        for (int t = MIN; t <= MAX; t++) {
            // Each error over its key's range, squared and summed, in integers: scaled by both
            // ranges squared so the comparison is exact.
            long b = bend - bendForDepth(t);
            long w = width - widthForDepth(t);
            long error = b * b * WIDTH_RANGE * WIDTH_RANGE + w * w * BEND_RANGE * BEND_RANGE;
            if (error < bestError) {
                bestError = error;
                best = t;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------------------- the six keys

    /**
     * The six stored edge values, read once; {@link #withDepth} gives the edge Depth writes,
     * every key outside the bend and the width as it was.
     */
    public static final class Edge {
        public final int bend;
        public final int width;
        public final int light;
        public final int specular;
        public final int dispersion;
        public final int clarity;

        public Edge(int bend, int width, int light, int specular, int dispersion, int clarity) {
            this.bend = bend;
            this.width = width;
            this.light = light;
            this.specular = specular;
            this.dispersion = dispersion;
            this.clarity = clarity;
        }

        public int depth() {
            return depthFor(bend, width);
        }

        /**
         * Where Depth stands, keeping a {@code remembered} position the user set while it still
         * writes exactly this edge: a flat stretch of the curve then reads back where the thumb
         * was left, not at the stretch's first position. {@link #NONE} reads as {@link #depth()}.
         */
        public int depth(int remembered) {
            return remembered >= MIN && remembered <= MAX && equals(withDepth(remembered))
                ? remembered : depth();
        }

        @NonNull
        public Edge withDepth(int depth) {
            return new Edge(bendForDepth(depth), widthForDepth(depth), light, specular,
                dispersion, clarity);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Edge))
                return false;
            Edge edge = (Edge) other;
            return bend == edge.bend && width == edge.width && light == edge.light
                && specular == edge.specular && dispersion == edge.dispersion
                && clarity == edge.clarity;
        }

        @Override
        public int hashCode() {
            int hash = bend;
            hash = 31 * hash + width;
            hash = 31 * hash + light;
            hash = 31 * hash + specular;
            hash = 31 * hash + dispersion;
            return 31 * hash + clarity;
        }

        @NonNull
        @Override
        public String toString() {
            return bend + "/" + width + "/" + light + "/" + specular + "/" + dispersion + "/"
                + clarity;
        }
    }

    private static int slider(int value) {
        return clamp(value, MIN, MAX);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
