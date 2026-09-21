package com.termux.app.terminal.inappkeyboard;

/**
 * Where the pressed-key glyph sits and how big it is.
 *
 * <p>Pure arithmetic, in pixels, so the overlay view only has to paint the answer. The popup is one
 * glyph and nothing else: its size is stepped off the label's length, and its anchor is the centre
 * of the cap, lifted just clear of the cap's top edge and held inside the keyboard's own width. The
 * design's px are read as dp; callers scale by the display density.
 */
public final class KeyPopupGeometry {

    private KeyPopupGeometry() {}

    /** Gap between the top of the cap and the bottom of the glyph's line box, in dp. */
    public static final float GAP_ABOVE_CAP_DP = 6f;
    /** How much clear room the glyph keeps inside the keyboard's left and right edges, in dp. */
    public static final float SIDE_MARGIN_DP = 4f;

    /** Type and layout size of the glyph, stepped off the label's length. */
    public static final class Metrics {
        /** Text size of the glyph, in px. */
        public final float glyphSizePx;
        /** Font weight, for the platforms that can pick one. */
        public final int weight;
        /** Whether the glyph is drawn in the monospace face rather than the label face. */
        public final boolean monospace;

        Metrics(float glyphSizePx, int weight, boolean monospace) {
            this.glyphSizePx = glyphSizePx;
            this.weight = weight;
            this.monospace = monospace;
        }
    }

    /** The four size tiers, by how many characters the label has. */
    public static Metrics metricsFor(String label, float density) {
        int n = label == null ? 0 : label.length();
        if (n <= 1) return new Metrics(30f * density, 300, false);
        if (n == 2) return new Metrics(20f * density, 400, true);
        if (n <= 4) return new Metrics(14f * density, 500, true);
        return new Metrics(12f * density, 500, true);
    }

    /**
     * Horizontal centre of the glyph. An edge-column key slides its glyph inward rather than having
     * it cut off; a glyph wider than the room it has is centred on what room there is.
     *
     * @param halfWidthPx half the measured width of the glyph
     */
    public static float anchorX(float keyCentreXPx, float halfWidthPx, float leftBoundPx,
                                float rightBoundPx, float density) {
        float margin = SIDE_MARGIN_DP * density;
        float low = leftBoundPx + halfWidthPx + margin;
        float high = rightBoundPx - halfWidthPx - margin;
        if (high < low) return (leftBoundPx + rightBoundPx) / 2f;
        return Math.min(Math.max(keyCentreXPx, low), high);
    }

    /**
     * Vertical centre of the glyph: its line box sits {@link #GAP_ABOVE_CAP_DP} above the top of
     * the cap, but never above the top of the overlay. A top-row popup floats over the terminal
     * output.
     */
    public static float anchorY(float keyTopPx, float glyphSizePx, float topBoundPx,
                                float density) {
        float half = glyphSizePx / 2f;
        return Math.max(keyTopPx - GAP_ABOVE_CAP_DP * density - half, topBoundPx + half);
    }
}
