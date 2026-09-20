package com.termux.app;

/**
 * Standardized backdrop dim behind elevated glass surfaces (the app drawer plane). Each surface
 * feeds its own transition progress, so the tint breathes from transparent to dark and back with
 * the same spring that moves the surface — never a hard cut.
 */
public final class GlassBackdropTint {
    /** Dim strength with the surface fully open: ~33% black over the glass stack. */
    private static final int MAX_ALPHA = 0x54;

    private GlassBackdropTint() {}

    /** ARGB scrim color for a transition progress in [0, 1]. */
    public static int colorFor(float progress) {
        float p = Float.isFinite(progress) ? Math.max(0f, Math.min(1f, progress)) : 0f;
        return ((int) (MAX_ALPHA * p)) << 24;
    }
}
