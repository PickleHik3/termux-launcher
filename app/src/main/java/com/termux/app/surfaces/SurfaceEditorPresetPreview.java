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

    public static final int CARD_WIDTH_DP = 42;
    public static final int CARD_HEIGHT_DP = 68;

    /**
     * The shortest a card is allowed to get where the region cannot hold the full one. Small, but
     * still a phone with three bands in it: the strip is how a preset is told apart from the next
     * one, and a card below this reads as a smudge rather than a look.
     */
    public static final int CARD_MIN_HEIGHT_DP = 48;

    /** The card's own clip corner. */
    public static final float CARD_CORNER_DP = 5f;

    /** The device width the preset's dp values are scaled down from. */
    private static final float REFERENCE_WIDTH_DP = 360f;

    /**
     * The card height the band constants below are drawn against. They are absolute rather than
     * fractions because that is how the mock was laid out by eye; {@link #cardScale} is what keeps
     * the bands in proportion when the card itself is resized, so a strip that has to shrink for a
     * short region shrinks by one number rather than six.
     */
    private static final float REFERENCE_CARD_HEIGHT_DP = 120f;

    private static final float STATUS_TOP_DP = 4f;
    private static final float STATUS_HEIGHT_DP = 7f;
    private static final float TERMINAL_TOP_DP = 15f;
    private static final float TERMINAL_BOTTOM_GAP_DP = 2f;
    private static final float BOTTOM_SLAB_HEIGHT_DP = 22f;
    private static final float FLOATING_BOTTOM_AIR_DP = 3f;

    /**
     * The card's width at a given height, keeping the mock's phone aspect. A shrunk card is still a
     * phone; a card that only lost height would be a different device.
     */
    public static int widthPxForHeightPx(int heightPx) {
        float aspect = CARD_WIDTH_DP / (float) CARD_HEIGHT_DP;
        return Math.max(1, Math.round(Math.max(0, heightPx) * aspect));
    }

    /**
     * How many mock-px one real-device dp of a preset value is worth, at the card's current width.
     * Read from the card's own pixels rather than the constant, so a strip shrunk for a short
     * region scales its side gaps and corners with it.
     */
    public static float presetScale(int widthPx, float density) {
        float scale = Math.max(0.01f, density);
        return Math.max(0, widthPx) / (REFERENCE_WIDTH_DP * scale);
    }

    /** How much of a reference-card dp survives at the card's current height. */
    private static float cardScale(int heightPx, float density) {
        float scale = Math.max(0.01f, density);
        return Math.max(0, heightPx) / (REFERENCE_CARD_HEIGHT_DP * scale);
    }

    /** One of the mock's vertical bands, in pixels at the card's current height. */
    private static int bandPx(float referenceDp, int heightPx, float density) {
        return Math.round(referenceDp * cardScale(heightPx, density) * density);
    }

    /** The status pill band. It floats with the side gap in both dock styles. */
    public static int[] statusInsets(int widthPx, int heightPx, float density, int sideGapDp) {
        int side = Math.round(Math.max(2f, sideGapDp * presetScale(widthPx, density)) * density);
        int top = bandPx(STATUS_TOP_DP, heightPx, density);
        int bottom = heightPx - top - bandPx(STATUS_HEIGHT_DP, heightPx, density);
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
            ? Math.round(Math.max(1f, paneGapDp * presetScale(widthPx, density)) * density) : 0;
        int top = bandPx(TERMINAL_TOP_DP, heightPx, density) + margin;
        int bottomEdge = bottomSlabTopPx(heightPx, density)
            - bandPx(TERMINAL_BOTTOM_GAP_DP, heightPx, density) - margin;
        return new int[] {margin, top, margin, Math.max(0, heightPx - bottomEdge)};
    }

    /**
     * The dock/keyboard slab — one piece, because that is the unified material. Docked runs flush
     * to the card's bottom and sides; Floating pulls in by the side gap and leaves bottom air.
     */
    public static int[] bottomSlabInsets(int widthPx, int heightPx, float density,
                                         int sideGapDp, boolean floating) {
        int side = floating
            ? Math.round(Math.max(2f, sideGapDp * presetScale(widthPx, density)) * density) : 0;
        int bottom = floating ? bandPx(FLOATING_BOTTOM_AIR_DP, heightPx, density) : 0;
        return new int[] {side, bottomSlabTopPx(heightPx, density) - bottom, side, bottom};
    }

    private static int bottomSlabTopPx(int heightPx, float density) {
        return heightPx - bandPx(BOTTOM_SLAB_HEIGHT_DP + FLOATING_BOTTOM_AIR_DP, heightPx, density);
    }

    /** A glass surface's corner on the mock: scaled in Floating, square where Docked is flush. */
    public static float surfaceRadiusPx(int widthPx, float density, int radiusDp,
                                        boolean floating) {
        if (!floating)
            return 0f;
        return Math.max(1.5f, radiusDp * presetScale(widthPx, density)) * density;
    }

    /** The terminal slab's corner on the mock; 0 keeps the full-bleed field square. */
    public static float terminalRadiusPx(int widthPx, float density, int terminalRadiusDp) {
        return Math.max(0, terminalRadiusDp) * presetScale(widthPx, density) * density;
    }
}
