package com.termux.app.terminal.inappkeyboard;

/**
 * Where every part of the pressed-key popup sits.
 *
 * <p>Pure arithmetic, in pixels, so the overlay view only has to paint the answer. Everything is
 * derived from the ring radius and from the metrics of the label currently in the middle, so no
 * number here is tied to one key size. The design's px are read as dp; callers scale by the
 * display density.
 *
 * <p>Corner indexes are the keyboard's own: 1 nw, 2 ne, 3 sw, 4 se, 5 w, 6 e, 7 n, 8 s, with 0
 * standing for the centre.
 */
public final class KeyPopupGeometry {

    private KeyPopupGeometry() {}

    /** Orbit radius of the alternates, in dp. */
    public static final float RING_RADIUS_DP = 34f;

    /** Unit vectors of the eight corners, indexed by corner; index 0 is the centre. */
    public static final float[] DIRECTION_X = {0f, -0.707f, 0.707f, -0.707f, 0.707f, -1f, 1f, 0f, 0f};
    public static final float[] DIRECTION_Y = {0f, -0.707f, -0.707f, 0.707f, 0.707f, 0f, 0f, -1f, 1f};

    /** Scale the targeted alternate grows to. */
    public static final float TARGET_SCALE = 1.45f;
    /** Opacity the other alternates fall to once a direction is targeted. */
    public static final float NON_TARGET_ALPHA = 0.2f;

    private static final float HALF_WIDTH_BASE_DP = 34f;

    /**
     * Type and layout size of the centre glyph, stepped off the label's length so that Ctrl, Home
     * and space sit inside the halo instead of bursting it.
     */
    public static final class Metrics {
        /** Text size of the centre glyph, in px. */
        public final float glyphSizePx;
        /** Width of its outline, in px. */
        public final float strokeWidthPx;
        /** The half-width the ring and the clamps are laid out against, in px. */
        public final float halfWidthPx;
        /** Font weight, for the platforms that can pick one. */
        public final int weight;
        /** Whether the glyph is drawn in the monospace face rather than the label face. */
        public final boolean monospace;

        Metrics(float glyphSizePx, float strokeWidthPx, float halfWidthPx, int weight,
                boolean monospace) {
            this.glyphSizePx = glyphSizePx;
            this.strokeWidthPx = strokeWidthPx;
            this.halfWidthPx = halfWidthPx;
            this.weight = weight;
            this.monospace = monospace;
        }
    }

    /** The four size tiers, by how many characters the label has. */
    public static Metrics metricsFor(String label, float density) {
        int n = label == null ? 0 : label.length();
        if (n <= 1) return new Metrics(46f * density, 1.4f * density, 34f * density, 300, false);
        if (n == 2) return new Metrics(30f * density, 1.1f * density, 34f * density, 400, true);
        if (n <= 4) return new Metrics(22f * density, 0.9f * density, 38f * density, 500, true);
        return new Metrics(17f * density, 0.8f * density, 42f * density, 500, true);
    }

    /** Text size of one alternate: the wider labels step down so they still fit their orbit. */
    public static float ringGlyphSizePx(String ringLabel, float density) {
        return (ringLabel != null && ringLabel.length() > 1 ? 11f : 14f) * density;
    }

    /**
     * Orbit radius of one alternate. The targeted one is flung outward, multi-character ones sit
     * further out, and the whole ring widens for a wide centre label.
     */
    public static float orbitRadiusPx(Metrics centre, float ringRadiusPx, String ringLabel,
                                      boolean target, float density) {
        boolean wide = ringLabel != null && ringLabel.length() > 1;
        return (target ? ringRadiusPx + 13f * density : ringRadiusPx)
            + (wide ? 9f * density : 0f)
            + (centre.halfWidthPx - HALF_WIDTH_BASE_DP * density);
    }

    /** Radius of the halo behind the centre glyph. */
    public static float haloRadiusPx(Metrics centre, float ringRadiusPx) {
        return Math.round((centre.halfWidthPx + ringRadiusPx) * 1.55f) / 2f;
    }

    /** True when any configured alternate carries more than one character. */
    public static boolean hasWideRing(String[] ringLabels) {
        if (ringLabels == null) return false;
        for (int i = 1; i < ringLabels.length; i++)
            if (ringLabels[i] != null && ringLabels[i].length() > 1) return true;
        return false;
    }

    /** How far the popup reaches to either side of its anchor. */
    public static float sideExtentPx(Metrics centre, float ringRadiusPx, boolean wideRing,
                                     float density) {
        return Math.max(centre.halfWidthPx, ringRadiusPx + (wideRing ? 22f : 12f) * density);
    }

    /** How far the popup reaches below its anchor; a modifier's sub-label asks for more. */
    public static float belowPx(Metrics centre, float ringRadiusPx, boolean modifier,
                                float density) {
        return Math.max(ringRadiusPx + 6f * density, centre.glyphSizePx * 0.55f + 6f * density)
            + (modifier ? 12f * density : 0f);
    }

    /** How far the popup reaches above its anchor. */
    public static float abovePx(Metrics centre, float ringRadiusPx, float density) {
        return Math.max(ringRadiusPx + 8f * density, centre.glyphSizePx * 0.62f);
    }

    /**
     * Horizontal centre of the popup. An edge-column key slides its popup inward rather than
     * having it cut off; a popup wider than the room it has is pinned to the right-hand limit,
     * which is the clamp the design's own prototype settles on.
     */
    public static float anchorX(float keyCentreXPx, float sideExtentPx, float leftBoundPx,
                                float rightBoundPx, float density) {
        float margin = 8f * density;
        float low = leftBoundPx + sideExtentPx + margin;
        float high = rightBoundPx - sideExtentPx - margin;
        return Math.min(Math.max(keyCentreXPx, low), high);
    }

    /**
     * Vertical centre of the popup: clear of the top of the cap, but never above the top of the
     * screen. A top-row popup is expected to float over the terminal output.
     */
    public static float anchorY(float keyTopPx, float belowPx, float abovePx, float topBoundPx,
                                float density) {
        return Math.max(keyTopPx - belowPx - 20f * density, topBoundPx + abovePx + 10f * density);
    }
}
