package com.termux.app.terminal;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Dresses one pane-shaped frame as a glass slab: the shared pre-blurred wallpaper frame drawn
 * through the frame's own rect, with the surface tint and film grain over it.
 *
 * <p>Shared by the terminal panes and by the pane wall's non-terminal pages, so a page cannot
 * drift from the terminal's treatment. Everything comes out of {@link PaneSurfaceStyle}; nothing
 * here reads preferences or knows what the frame contains.
 */
public final class PaneGlass {

    /** Radius a slab takes when the style has no opinion. */
    private static final int DEFAULT_RADIUS_DP = 10;

    private PaneGlass() {}

    /** True while frames should carry a slab at all. */
    public static boolean isActive(@Nullable PaneSurfaceStyle style) {
        return style != null && style.isPaneGlassActive();
    }

    /** The slab radius the style asks for, in px, falling back to the built-in default. */
    public static float radiusPx(@Nullable PaneSurfaceStyle style, float density) {
        float radius = style != null ? style.paneGlassCornerRadiusPx() : 0f;
        return radius > 0f ? radius : density * DEFAULT_RADIUS_DP;
    }

    /** The gap between tiled panes in dp, or {@code fallbackDp} while no style is attached. */
    public static int gapDp(@Nullable PaneSurfaceStyle style, int fallbackDp) {
        return style != null ? Math.max(0, style.paneGapDp()) : fallbackDp;
    }

    /**
     * Feed one frame's backdrop, or hide it. Idempotent and cheap — the backdrop view is created
     * once per frame and only re-fed here — so this can run on every editor slider tick and on
     * every frost refresh.
     *
     * @param requestedRadiusPx the slab radius before it is capped against this frame's own size
     * @return true when the slab is showing
     */
    public static boolean apply(@Nullable PaneSurfaceStyle style, @NonNull View frame,
                                @Nullable PaneGlassBackdropView backdrop,
                                float requestedRadiusPx) {
        if (backdrop == null) return false;
        if (!isActive(style)) {
            backdrop.setVisibility(View.GONE);
            return false;
        }
        // Against the frame's own size, not the window's: after four or five splits a pane is a
        // few rows tall and the window's radius would be half of it.
        float radiusPx = PaneShape.radiusForBounds(requestedRadiusPx,
            frame.getWidth(), frame.getHeight());
        backdrop.setGlass(style.paneGlassBlurFrame(), style.paneGlassBlurFrameRect(),
            style.paneGlassTintColor(), style.paneGlassGrainLayer(), radiusPx,
            style.paneGlassFrostFilter());
        backdrop.setVisibility(View.VISIBLE);
        return true;
    }

    /**
     * Keep a slab aimed at the wallpaper as its frame moves. A frame moves for reasons that never
     * redraw it (a sibling's divider drag, a float being dragged, the host resizing under the
     * keyboard, a wall page sliding), and the frost is positioned in screen space, so every move
     * has to re-aim the matrix.
     */
    public static void followLayout(@Nullable PaneGlassBackdropView backdrop) {
        if (backdrop == null) return;
        backdrop.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (l != ol || t != ot || r != or || b != ob)
                ((PaneGlassBackdropView) v).invalidateGlassPosition();
        });
    }
}
