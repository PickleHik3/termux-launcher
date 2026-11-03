package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The selection ring's shape, kept apart from the views so it is testable: which cards wear the
 * halo round the whole mini UI, and the per-corner radii of the elements' rings.
 *
 * <p>Every radius here is in the root container's unscaled space, the space the ring draws in:
 * the frame's scale shrinks it with the mini UI, so a screen radius stays the screen's own.</p>
 */
final class SelectionRingShape {

    /** How many concentric strokes the halo's glow is drawn with. */
    static final int HALO_LAYERS = 5;
    /** The glow's width on screen, dp; divided by the frame's scale like the ring's stroke. */
    static final float HALO_WIDTH_DP = 10f;

    private SelectionRingShape() {}

    /** Whether {@code card} is shown by the halo round the whole mini UI: a page card. */
    static boolean wearsHalo(@Nullable EditorCard card) {
        return card == EditorCard.HOME || card == EditorCard.WORKSPACE;
    }

    /**
     * The ring's corners as {@code GradientDrawable.setCornerRadii} takes them (top-left x,y,
     * top-right, bottom-right, bottom-left). In Docked the status bar's top corners and the
     * keyboard's bottom corners stand at the screen's own and take {@code screenRadiusPx}; every
     * other corner, and every corner in Floating, the element's own {@code ownRadiusPx}. Each is
     * grown by {@code growPx}, how far the ring stands outside the element, and never below 0.
     */
    @NonNull
    static float[] cornerRadii(@NonNull EditorCard card, boolean docked, float ownRadiusPx,
                               float screenRadiusPx, float growPx) {
        float own = Math.max(0f, ownRadiusPx + growPx);
        float screen = Math.max(0f, screenRadiusPx + growPx);
        float top = own;
        float bottom = own;
        if (docked && card == EditorCard.STATUS)
            top = screen;
        else if (docked && card == EditorCard.KEYBOARD)
            bottom = screen;
        return new float[] {top, top, top, top, bottom, bottom, bottom, bottom};
    }

    /**
     * Layer {@code index}'s alpha share (0..1) of the halo's colour: strongest at the outline,
     * falling quadratically inward, so the glow reads soft rather than a stroke.
     */
    static float haloLayerAlpha(int index) {
        if (index < 0 || index >= HALO_LAYERS)
            return 0f;
        float t = 1f - index / (float) HALO_LAYERS;
        return 0.85f * t * t;
    }

    /** Layer {@code index}'s corner radius: the outline's, less how far in the layer stands. */
    static float haloLayerRadius(float outerRadiusPx, float insetPx) {
        return Math.max(0f, outerRadiusPx - insetPx);
    }
}
