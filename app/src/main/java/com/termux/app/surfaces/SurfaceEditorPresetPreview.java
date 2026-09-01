package com.termux.app.surfaces;

/**
 * Geometry for the preset strip's mini device mocks: a phone-aspect card whose layers — status
 * pill, terminal field, and the dock/keyboard slab — sit where they sit on the real screen, scaled
 * down from a reference device width. The controller renders the layers with the live glass
 * recipe at each preset's own values; this class only decides where each layer's rectangle is, so
 * the placement is pure arithmetic and testable without a view tree.
 *
 * <p>All returned insets are {left, top, right, bottom} pixel arrays for
 * {@code LayerDrawable.setLayerInset}.
 */
public final class SurfaceEditorPresetPreview {

    private SurfaceEditorPresetPreview() {}

    public static final int CARD_WIDTH_DP = 48;
    public static final int CARD_HEIGHT_DP = 80;
    /** The card's own clip corner. */
    public static final float CARD_CORNER_DP = 6f;

    /** The device width the preset's dp values are scaled down from. */
    private static final float REFERENCE_WIDTH_DP = 360f;

    /**
     * The card height the band constants below are drawn against. They are absolute rather than
     * fractions because that is how the mock was laid out by eye; {@link #cardScale()} is what
     * keeps the bands in proportion when the card itself is resized, so shrinking the strip is one
     * constant rather than six.
     */
    private static final float REFERENCE_CARD_HEIGHT_DP = 120f;

    private static final float STATUS_TOP_DP = 4f;
    private static final float STATUS_HEIGHT_DP = 7f;
    private static final float TERMINAL_TOP_DP = 15f;
    private static final float TERMINAL_BOTTOM_GAP_DP = 2f;
    private static final float BOTTOM_SLAB_HEIGHT_DP = 22f;
    private static final float FLOATING_BOTTOM_AIR_DP = 3f;

    /** How many mock-px one real-device dp of a preset value is worth. */
    public static float presetScale() {
        return CARD_WIDTH_DP / REFERENCE_WIDTH_DP;
    }

    /** How much of a reference-card dp survives at the card's current height. */
    private static float cardScale() {
        return CARD_HEIGHT_DP / REFERENCE_CARD_HEIGHT_DP;
    }

    /** One of the mock's vertical bands, in pixels at the card's current height. */
    private static int bandPx(float referenceDp, float density) {
        return Math.round(referenceDp * cardScale() * density);
    }

    /** The status pill band. It floats with the side gap in both dock styles. */
    public static int[] statusInsets(int widthPx, int heightPx, float density, int sideGapDp) {
        int side = Math.round(Math.max(2f, sideGapDp * presetScale()) * density);
        int top = bandPx(STATUS_TOP_DP, density);
        int bottom = heightPx - top - bandPx(STATUS_HEIGHT_DP, density);
        return new int[] {side, top, side, Math.max(0, bottom)};
    }

    /**
     * The terminal field. Full-bleed between status and the bottom slab unless the preset gives
     * the terminal its own radius, which turns it into a bounded slab inset by the pane gap —
     * exactly the rule the real render path applies.
     */
    public static int[] terminalInsets(int widthPx, int heightPx, float density,
                                       int paneGapDp, int terminalRadiusDp) {
        int margin = terminalRadiusDp > 0
            ? Math.round(Math.max(1f, paneGapDp * presetScale()) * density) : 0;
        int top = bandPx(TERMINAL_TOP_DP, density) + margin;
        int bottomEdge = bottomSlabTopPx(heightPx, density)
            - bandPx(TERMINAL_BOTTOM_GAP_DP, density) - margin;
        return new int[] {margin, top, margin, Math.max(0, heightPx - bottomEdge)};
    }

    /**
     * The dock/keyboard slab — one piece, because that is the unified material. Docked runs flush
     * to the card's bottom and sides; Floating pulls in by the side gap and leaves bottom air.
     */
    public static int[] bottomSlabInsets(int widthPx, int heightPx, float density,
                                         int sideGapDp, boolean floating) {
        int side = floating
            ? Math.round(Math.max(2f, sideGapDp * presetScale()) * density) : 0;
        int bottom = floating ? bandPx(FLOATING_BOTTOM_AIR_DP, density) : 0;
        return new int[] {side, bottomSlabTopPx(heightPx, density) - bottom, side, bottom};
    }

    private static int bottomSlabTopPx(int heightPx, float density) {
        return heightPx - bandPx(BOTTOM_SLAB_HEIGHT_DP + FLOATING_BOTTOM_AIR_DP, density);
    }

    /** A glass surface's corner on the mock: scaled in Floating, square where Docked is flush. */
    public static float surfaceRadiusPx(float density, int radiusDp, boolean floating) {
        if (!floating)
            return 0f;
        return Math.max(1.5f, radiusDp * presetScale()) * density;
    }

    /** The terminal slab's corner on the mock; 0 keeps the full-bleed field square. */
    public static float terminalRadiusPx(float density, int terminalRadiusDp) {
        return Math.max(0, terminalRadiusDp) * presetScale() * density;
    }
}
