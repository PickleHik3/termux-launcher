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

    /** How many thin strokes the halo's glow is drawn with: enough that no band shows. */
    static final int HALO_LAYERS = 28;
    /** The glow's reach outside the mini UI on screen, dp; divided by the frame's scale. */
    static final float HALO_WIDTH_DP = 14f;
    /** The glow's alpha share right at the outline. */
    static final float HALO_PEAK_ALPHA = 0.55f;

    private SelectionRingShape() {}

    /** Whether {@code card} is shown by the halo round the whole mini UI: a page card. */
    static boolean wearsHalo(@Nullable EditorCard card) {
        return card == EditorCard.HOME || card == EditorCard.WORKSPACE;
    }

    /**
     * Whether {@code card}'s ring meets the screen's own edge: in Docked, the status bar (its top)
     * and the keyboard (its bottom) reach the physical screen, and the frame's clip would cut a
     * ring drawn outside them.
     */
    static boolean meetsScreen(@NonNull EditorCard card, boolean docked) {
        return docked && (card == EditorCard.STATUS || card == EditorCard.KEYBOARD);
    }

    /**
     * Pulls the ring's rect {@code ring} {left, top, right, bottom} inside the mini UI's outline
     * {@code clip} on every side it would cross, in place: there the stroke stands just inside
     * the screen's edge instead of under the frame's clip.
     */
    static void keepInside(@NonNull int[] ring, @NonNull int[] clip) {
        ring[0] = Math.max(ring[0], clip[0]);
        ring[1] = Math.max(ring[1], clip[1]);
        ring[2] = Math.min(ring[2], clip[2]);
        ring[3] = Math.min(ring[3], clip[3]);
    }

    /**
     * The ring's corners as {@code GradientDrawable.setCornerRadii} takes them (top-left x,y,
     * top-right, bottom-right, bottom-left). Where the ring meets the screen ({@link #meetsScreen}:
     * the Docked status bar's top corners, the Docked keyboard's bottom corners) a corner runs
     * parallel to the screen's: {@code screenRadiusPx} less how far that corner of {@code ring}
     * stands inside the same corner of {@code clip}, or the element's own once it stands past the
     * screen's arc. Every other corner, and every corner in Floating, takes {@code ownRadiusPx}
     * (already grown by how far the ring stands outside the element). Never below 0.
     */
    @NonNull
    static float[] cornerRadii(@NonNull EditorCard card, boolean docked, float ownRadiusPx,
                               float screenRadiusPx, @NonNull int[] ring, @NonNull int[] clip) {
        float own = Math.max(0f, ownRadiusPx);
        float tl = own;
        float tr = own;
        float br = own;
        float bl = own;
        if (meetsScreen(card, docked)) {
            int left = ring[0] - clip[0];
            int top = ring[1] - clip[1];
            int right = clip[2] - ring[2];
            int bottom = clip[3] - ring[3];
            if (card == EditorCard.STATUS) {
                tl = screenCorner(screenRadiusPx, left, top, own);
                tr = screenCorner(screenRadiusPx, right, top, own);
            } else {
                bl = screenCorner(screenRadiusPx, left, bottom, own);
                br = screenCorner(screenRadiusPx, right, bottom, own);
            }
        }
        return new float[] {tl, tl, tr, tr, br, br, bl, bl};
    }

    /** A corner {@code insetX}, {@code insetY} inside the screen's corner of {@code radius}. */
    static float screenCorner(float radiusPx, int insetX, int insetY, float ownPx) {
        float inset = Math.max(0, Math.max(insetX, insetY));
        if (radiusPx <= 0f || inset >= radiusPx)
            return ownPx;
        return radiusPx - inset;
    }

    /**
     * Layer {@code index}'s alpha share (0..1) of the halo's colour, layer 0 at the outline: a
     * smooth falloff outward (a Gaussian-like curve reaching 0 at the glow's far edge).
     */
    static float haloLayerAlpha(int index) {
        if (index < 0 || index >= HALO_LAYERS)
            return 0f;
        float t = (index + 0.5f) / HALO_LAYERS;
        float edge = 1f - t;
        return HALO_PEAK_ALPHA * (float) Math.exp(-4f * t * t) * edge * edge;
    }

    /** Layer {@code index}'s corner radius: the outline's, grown by how far out the layer stands. */
    static float haloLayerRadius(float outerRadiusPx, float outsetPx) {
        return Math.max(0f, outerRadiusPx + outsetPx);
    }
}
