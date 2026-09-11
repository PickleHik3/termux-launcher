package com.termux.app.terminal;

import com.termux.app.statusbar.WindowActivityRing;

/**
 * Where everything a window chip draws behind and around its title goes: the watermark glyph's
 * strength, the arc that runs along the chip's own outline while a shell works, the corner dots
 * that carry the marks and the agent reading, and the width of the × segment as it opens.
 *
 * <p>Free of Android imports, so every number here is unit-testable and {@link
 * ChipWatermarkDrawable} stays a thin sheet of paint calls. Distances are in pixels and the caller
 * owns the density; fractions are of one turn around the outline, counted clockwise from where the
 * path starts — the top-leading corner.
 */
public final class ChipWatermarkGeometry {

    /**
     * The watermark glyph's size, whatever the title beside it is set in. Taller than the 20 dp
     * chip on purpose: the rounded outline crops it top and bottom, so it reads as a mark printed
     * on the chip rather than a small icon floating in it.
     */
    public static final float GLYPH_SIZE_DP = 21f;
    /** How far inside the chip's leading edge — trailing, in RTL — the glyph's box starts. */
    public static final float GLYPH_LEADING_INSET_DP = 2f;
    /** How much further in the title starts than it used to, so its first letters clear the glyph. */
    public static final float TITLE_NUDGE_DP = 5f;
    /** The title's halo: a soft shadow in the chip's own fill, so the letters cut out of the glyph. */
    public static final float TITLE_HALO_DP = 1.5f;
    /** How opaque that fill is made for the halo — a fill at alpha 16 would hide nothing. */
    public static final int TITLE_HALO_ALPHA = 200;
    /** A corner dot — a mark, or the agent reading — across. */
    public static final float DOT_DIAMETER_DP = 5f;
    /** How far past the outline a corner dot sits, where the chip's corner leaves room for it. */
    public static final float DOT_GAP_DP = 2f;
    /** The ring of ground colour around a corner dot, so it reads against the glyph underneath. */
    public static final float DOT_HALO_DP = 1f;
    /** The chip's outline, which is also the ring a working window draws. */
    public static final float OUTLINE_WIDTH_DP = 1f;

    /**
     * The watermark's strength, as alpha: 15% at rest, 26% selected. It is drawn in the place
     * accent rather than the title's colour — sharing the text's colour is exactly what buried it.
     */
    public static final int GLYPH_ALPHA = 38;
    public static final int SELECTED_GLYPH_ALPHA = 66;

    /** The faint full outline a reported percentage fills over. */
    public static final int RING_TRACK_ALPHA = 56;

    /** How much of the outline the indeterminate arc covers: the ring's 270°, as a fraction. */
    public static final float RING_SWEEP_FRACTION =
        WindowActivityRing.INDETERMINATE_SWEEP_DEG / 360f;

    /** How faint an idle agent's dot sits, and the ends of a working one's breath. */
    public static final int AGENT_IDLE_ALPHA = 110;
    public static final int AGENT_PULSE_MIN_ALPHA = 96;
    public static final int AGENT_PULSE_MAX_ALPHA = 255;

    /** The × the selected chip grows on its trailing side, and how long it takes to open. */
    public static final float CLOSE_SEGMENT_DP = 24f;
    public static final long CLOSE_REVEAL_MS = 180L;
    /** The hairline between the title and the × it shares a chip with. */
    public static final float CLOSE_DIVIDER_DP = 1f;

    /** cos 45°: a corner dot leaves the chip along the diagonal, not along one edge. */
    private static final float DIAGONAL = (float) (1d / Math.sqrt(2d));

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
     * The colour the title's halo is drawn in: the chip's own fill, raised to near-opaque. The
     * fills are faint tints meant to be seen over the bar's ground, and a halo at alpha 16 would
     * separate nothing; only the hue is wanted, so the alpha is ours.
     */
    public static int haloColor(int fillColor) {
        return (TITLE_HALO_ALPHA << 24) | (fillColor & 0x00FFFFFF);
    }

    /**
     * Where a corner dot's centre sits on one axis, given the edge it hugs ({@code near}) and the
     * opposite one.
     *
     * <p>The dot leaves the outline along the corner's diagonal, which is the only direction a
     * rounded chip has room in: the arc is {@code radius} inside the bounding box's corner, so a
     * dot pushed {@code gapPx} past it is still inside the chip the drawable is allowed to paint.
     * A square chip has no such room, so the last term clamps the dot — halo included — back inside
     * the edge rather than letting it be clipped away.
     */
    public static float dotCentreOnAxis(float near, float far, float radiusPx, float gapPx,
                                        float dotRadiusPx, float haloPx) {
        float direction = near >= far ? 1f : -1f;
        float half = Math.abs(near - far) / 2f;
        float radius = Math.max(0f, Math.min(radiusPx, half));
        float arcCentre = near - direction * radius;
        float centre = arcCentre + direction * (radius + gapPx + dotRadiusPx) * DIAGONAL;
        float limit = near - direction * (dotRadiusPx + haloPx);
        return direction > 0f ? Math.min(centre, limit) : Math.max(centre, limit);
    }

    /**
     * Where the turning arc starts on the outline at {@code phase} of the turn. Lazy mode quantises
     * it to {@link WindowActivityRing#LAZY_STEPS} stops, exactly as the ring in the label did.
     */
    public static float ringStartFraction(float phase, boolean stepped) {
        float turned = stepped
            ? WindowActivityRing.steppedPhase(phase, WindowActivityRing.LAZY_STEPS) : phase;
        return turned - (float) Math.floor(turned);
    }

    /** How much of the outline a reported percentage has filled. Clamped, like the ring's sweep. */
    public static float determinateFraction(int percent) {
        return Math.max(0, Math.min(100, percent)) / 100f;
    }

    /** Where a fraction of the way round falls, in pixels along a path {@code lengthPx} long. */
    public static float segmentStartPx(float lengthPx, float startFraction) {
        if (lengthPx <= 0f) return 0f;
        float wrapped = startFraction - (float) Math.floor(startFraction);
        return lengthPx * wrapped;
    }

    /** How long a segment covering {@code sweepFraction} of the outline runs. */
    public static float segmentSweepPx(float lengthPx, float sweepFraction) {
        if (lengthPx <= 0f) return 0f;
        return lengthPx * clamp01(sweepFraction);
    }

    /**
     * The part of a segment that runs off the end of the path and continues from its start, or 0
     * when it fits. The arc travels, so it spends most of the turn straddling the seam.
     */
    public static float segmentTailPx(float lengthPx, float startPx, float sweepPx) {
        if (lengthPx <= 0f) return 0f;
        float overrun = startPx + sweepPx - lengthPx;
        return overrun <= 0f ? 0f : Math.min(overrun, lengthPx);
    }

    /** One breath per turn of the shared clock: up for the first half, down for the second. */
    public static int breathAlpha(float phase) {
        float wrapped = clamp01(phase - (float) Math.floor(phase));
        float triangle = wrapped < 0.5f ? wrapped * 2f : (1f - wrapped) * 2f;
        return Math.round(
            AGENT_PULSE_MIN_ALPHA + (AGENT_PULSE_MAX_ALPHA - AGENT_PULSE_MIN_ALPHA) * triangle);
    }

    /** How wide the × segment stands at {@code fraction} of its opening. */
    public static int closeSegmentWidthPx(float fraction, float fullWidthPx) {
        return Math.round(Math.max(0f, fullWidthPx) * clamp01(fraction));
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }
}
