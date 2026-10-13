package com.termux.app.terminal;

/**
 * Where everything a window chip draws behind and around its title goes: the watermark glyph's
 * strength, the ring that runs along the chip's own outline while a shell works, the tint a
 * marked or lazily working chip takes, and the width of the × segment as it opens.
 *
 * <p>Free of Android imports, so every number here is unit-testable and {@link
 * ChipWatermarkDrawable} stays a thin sheet of paint calls. Distances are in pixels and the caller
 * owns the density; fractions are of one turn around the outline, counted clockwise from where the
 * path starts — the top-leading corner.
 */
public final class ChipWatermarkGeometry {

    /**
     * The watermark glyph's size, whatever the title beside it is set in. 25% smaller than the
     * chip originally wore — the one place that scales it, so every caller shrinks together.
     */
    public static final float GLYPH_SIZE_DP = 21f * 0.75f;
    /** How far inside the chip's leading edge — trailing, in RTL — the glyph's box starts. */
    public static final float GLYPH_LEADING_INSET_DP = 2f;
    /** How much further in the title starts than it used to, so its first letters clear the glyph. */
    public static final float TITLE_NUDGE_DP = 5f;
    /** The title's halo: a soft shadow in the chip's own fill, so the letters cut out of the glyph. */
    public static final float TITLE_HALO_DP = 1.5f;
    /**
     * How opaque the colour behind the halo is made — a wash at its own alpha would hide nothing.
     *
     * <p>Kept short of opaque, and the label's legibility is deliberately not allowed to depend on
     * the difference. {@link WindowChipInk#worstGround} measures a label against the chip's
     * surface <em>with the watermark printed on it</em>, and this halo only ever pulls that ground
     * back toward the plain surface; whatever fraction of it actually lands under a blurred
     * shadow, the label was already promised its tier without it.</p>
     */
    public static final int TITLE_HALO_ALPHA = 200;
    /** The chip's outline, which is also the ring a working window draws. */
    public static final float OUTLINE_WIDTH_DP = 1f;

    /**
     * The watermark's strength, as alpha: 15% at rest, 26% selected. It is drawn in the place
     * accent rather than the title's colour — sharing the text's colour is exactly what buried it.
     *
     * <p>These two survived the light-mode round unchanged, and the reason is arithmetic rather
     * than affection: the watermark is the one piece of a chip that is given no contrast floor of
     * its own. A floor for it would have to be taken out of the label's, which is the tier that
     * matters — see {@link WindowChipInk}. What changed instead is the colour they are spent on,
     * which now stands on the chrome's own side of the band, and the fact that the label's ground
     * is measured with the glyph already printed at these alphas.</p>
     */
    public static final int GLYPH_ALPHA = 38;
    public static final int SELECTED_GLYPH_ALPHA = 66;

    /**
     * The faint full outline a reported percentage fills over. A track, not a mark: it says where
     * the arc will go, and the arc itself — drawn at full strength in the same colour, which
     * {@link WindowChipInk} holds to {@code TARGET_LARGE_TEXT} — is what carries the fact.
     */
    public static final int RING_TRACK_ALPHA = 56;
    /**
     * How strongly lazy mode tints a working chip's fill with the ring colour. Lazy mode draws a
     * working window as a still colour instead of a breath, so nothing redraws while it works;
     * strong enough to read across the strip at a glance, faint enough that the label stays legible.
     * A filled mark — attention or done — tints the chip in its own colour at the same strength,
     * for the same two reasons.
     */
    public static final int BUSY_TINT_ALPHA = 72;

    /** The × the selected chip grows on its trailing side, and how long it takes to open. */
    public static final float CLOSE_SEGMENT_DP = 24f;
    public static final long CLOSE_REVEAL_MS = 180L;
    /** The hairline between the title and the × it shares a chip with. */
    public static final float CLOSE_DIVIDER_DP = 1f;

    private ChipWatermarkGeometry() {}

    /**
     * The watermark's alpha at {@code selection}, which runs 0 → 1 across a selection slide so the
     * glyph brightens with the title rather than popping at the end of it.
     */
    public static int glyphAlpha(float selection) {
        float fraction = clamp01(selection);
        return Math.round(GLYPH_ALPHA + (SELECTED_GLYPH_ALPHA - GLYPH_ALPHA) * fraction);
    }

    /**
     * Where the glyph's centre sits across the chip: its box hugs the leading edge, {@code insetPx}
     * inside it, whichever way the row reads. The glyph is drawn centred on that point, so this is
     * the inset plus half the box — the outline clips whatever falls outside the chip.
     */
    public static float glyphCentreOnAxis(float leadingEdge, float trailingEdge, float insetPx,
                                          float sizePx) {
        float direction = trailingEdge >= leadingEdge ? 1f : -1f;
        return leadingEdge + direction * (insetPx + sizePx / 2f);
    }

    /**
     * The colour the title's halo is drawn in: what the chip's body is, raised to near-opaque. The
     * washes are faint tints meant to be seen over the bar's ground, and a halo at alpha 16 would
     * separate nothing; only the colour is wanted, so the alpha is ours.
     *
     * <p>{@code chipColor} is the chip's <em>effective surface</em> — its wash already composited
     * onto the measured band — wherever the chrome can measure what it is standing on. That is the
     * colour the title was promised its contrast against, so a halo in it can only ever help. With
     * nothing measured (a preview, a bar with no chrome attached) the caller passes the authored
     * wash instead and this behaves exactly as it always did.</p>
     */
    public static int haloColor(int chipColor) {
        return (TITLE_HALO_ALPHA << 24) | (chipColor & 0x00FFFFFF);
    }

    /** How much of the outline a reported percentage has filled. Clamped, like the ring's sweep. */
    public static float determinateFraction(int percent) {
        return Math.max(0, Math.min(100, percent)) / 100f;
    }

    /** How long a segment covering {@code sweepFraction} of the outline runs. */
    public static float segmentSweepPx(float lengthPx, float sweepFraction) {
        if (lengthPx <= 0f) return 0f;
        return lengthPx * clamp01(sweepFraction);
    }

    /**
     * The part of a segment that runs off the end of the path and continues from its start, or 0
     * when it fits.
     */
    public static float segmentTailPx(float lengthPx, float startPx, float sweepPx) {
        if (lengthPx <= 0f) return 0f;
        float overrun = startPx + sweepPx - lengthPx;
        return overrun <= 0f ? 0f : Math.min(overrun, lengthPx);
    }

    /** How wide the × segment stands at {@code fraction} of its opening. */
    public static int closeSegmentWidthPx(float fraction, float fullWidthPx) {
        return Math.round(Math.max(0f, fullWidthPx) * clamp01(fraction));
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }
}
