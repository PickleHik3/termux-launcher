package com.termux.shared.termux.settings.preferences;

/**
 * The glass sliders as physical numbers, in one pure place: Depth (slab thickness and index of
 * refraction), Shine (how bright the light on the rim and lens is, with a little colour split at
 * the top) and the one-time mapping from the retired Rim and Edge rows.
 *
 * <p>Both sliders run 0 to 100 and sit at realism by default: Depth 60 is a 27 dp slab of
 * ordinary glass (index 1.5), Shine 50 the room light as it is. Past them the glass goes past
 * realism: Depth up to 45 dp and an index of 1.9, Shine up to two and a half times the light and
 * a measured colour split. Stored Depth stays the thickness in dp ({@code look.depth}); the
 * slider position is derived from it, so a stored value never changes meaning.</p>
 */
public final class GlassMaterial {

    private GlassMaterial() {}

    /** Both sliders' range. */
    public static final int MIN = 0;
    public static final int MAX = 100;

    /** Depth's realistic position, and the thickness it writes, dp. */
    public static final int DEPTH_DEFAULT = 60;
    public static final int REALISTIC_BEND_DP = 27;
    /** The thickest slab, dp: the stored bend's top. */
    public static final int MAX_BEND_DP = 45;
    /** Glass's index of refraction up to the realistic thickness, and at the top of Depth. */
    public static final float IOR_REALISTIC = 1.5f;
    public static final float IOR_TOP = 1.9f;

    /**
     * How much of its accent the Tinted Look mixes in, percent: the Material blend. One number for
     * v1's Tinted preset ({@code GlassLook.TINT_LOOK_PERCENT}) and Terl's Tint colours.
     */
    public static final int TINT_LOOK_PERCENT = 35;

    /** Shine's realistic position. */
    public static final int SHINE_DEFAULT = 50;
    /** Where the colour split starts, and the most it reaches (a Look.dispersion percentage). */
    static final int DISPERSION_FROM = 60;
    static final int DISPERSION_TOP = 60;
    /** The light's top multiple at Shine 100. */
    static final float SHINE_TOP = 2.5f;

    // ---------------------------------------------------------------------------------- Depth

    /** The thickness, dp, a Depth position writes: 0 to 27 up to the default, then 27 to 45. */
    public static int bendForDepth(int depth) {
        int t = clamp(depth, MIN, MAX);
        if (t <= DEPTH_DEFAULT) return Math.round(REALISTIC_BEND_DP * t / (float) DEPTH_DEFAULT);
        return REALISTIC_BEND_DP + Math.round((MAX_BEND_DP - REALISTIC_BEND_DP)
            * (t - DEPTH_DEFAULT) / (float) (MAX - DEPTH_DEFAULT));
    }

    /** The lowest Depth position whose thickness is nearest {@code bendDp}. */
    public static int depthForBend(int bendDp) {
        int bend = clamp(bendDp, 0, MAX_BEND_DP);
        // The realistic slab reads as the default position, not the first one that writes it.
        if (bendForDepth(DEPTH_DEFAULT) == bend) return DEPTH_DEFAULT;
        int best = MIN;
        int bestError = Integer.MAX_VALUE;
        for (int t = MIN; t <= MAX; t++) {
            int error = Math.abs(bendForDepth(t) - bend);
            if (error < bestError) {
                bestError = error;
                best = t;
            }
        }
        return best;
    }

    /** The glass's index at a thickness: 1.5 up to the realistic slab, rising to 1.9 at the thickest. */
    public static float iorForBend(int bendDp) {
        if (bendDp <= REALISTIC_BEND_DP) return IOR_REALISTIC;
        float u = Math.min(1f, (bendDp - REALISTIC_BEND_DP) / (float) (MAX_BEND_DP - REALISTIC_BEND_DP));
        return IOR_REALISTIC + (IOR_TOP - IOR_REALISTIC) * u;
    }

    /**
     * How much further a slab at {@code bendDp} bends than glass of index 1.5: the deepest bend
     * is {@code T sqrt(n^2 - 1)}, so a thicker slab's higher index is folded into its thickness
     * as {@code sqrt(n^2 - 1) / sqrt(1.5^2 - 1)}: 1 up to the realistic slab, 1.45 at the top.
     */
    public static float thicknessBoost(int bendDp) {
        float n = iorForBend(bendDp);
        float k0 = (float) Math.sqrt(IOR_REALISTIC * IOR_REALISTIC - 1f);
        return (float) Math.sqrt(n * n - 1f) / k0;
    }

    // ---------------------------------------------------------------------------------- Shine

    /** The light's multiple of the room light at a Shine: 0 dark, 1 at the default, 2.5 at the top. */
    public static float shineScale(int shine) {
        int s = clamp(shine, MIN, MAX);
        if (s <= SHINE_DEFAULT) return s / (float) SHINE_DEFAULT;
        return 1f + (SHINE_TOP - 1f) * (s - SHINE_DEFAULT) / (float) (MAX - SHINE_DEFAULT);
    }

    /** The colour split a Shine asks for, a percentage for {@code Look.dispersion}: none up to 60, 60 % at the top. */
    public static int dispersionPercent(int shine) {
        int s = clamp(shine, MIN, MAX);
        if (s <= DISPERSION_FROM) return 0;
        return Math.round(DISPERSION_TOP * (s - DISPERSION_FROM) / (float) (MAX - DISPERSION_FROM));
    }

    // ---------------------------------------------------------------------------- the old rows

    /** Shine for a rim that was off, a hairline and a gradient. */
    public static final int SHINE_RIM_OFF = 10;
    public static final int SHINE_RIM_HAIRLINE = SHINE_DEFAULT;
    public static final int SHINE_RIM_GRADIENT = 72;
    /** The most Edge any shipped Look carried (Clear's glint): at or under it adds nothing. */
    static final int EDGE_SHIPPED = 40;

    /**
     * The Shine nearest a stored Rim ({@link LookGrid#RIM_OFF}, hairline, gradient) and Edge:
     * the rim's stop sets the base, an Edge above the shipped glint raises it by half the
     * excess, never lowers it.
     */
    public static int shineForRimAndEdge(int rim, int edge) {
        int base;
        switch (rim) {
            case LookGrid.RIM_OFF: base = SHINE_RIM_OFF; break;
            case LookGrid.RIM_GRADIENT: base = SHINE_RIM_GRADIENT; break;
            default: base = SHINE_RIM_HAIRLINE;
        }
        int extra = Math.max(0, edge - EDGE_SHIPPED) / 2;
        return clamp(base + extra, MIN, MAX);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
