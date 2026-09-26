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

    /** Radius a pane takes while no style is attached to ask. */
    private static final int DEFAULT_RADIUS_DP = 10;

    private PaneGlass() {}

    /** True while frames should carry a slab at all. */
    public static boolean isActive(@Nullable PaneSurfaceStyle style) {
        return style != null && style.isPaneGlassActive();
    }

    /**
     * The radius a pane is drawn at, in px — the slab's and the frame's alike, so a page of the
     * wall rounds exactly as a terminal pane does.
     *
     * <p>The built-in default answers one thing only: no style attached yet. It used to answer a
     * style reporting 0 as well, which read the user's square corners as no answer at all and
     * charged a pane at radius 0 the arc clearance anyway.
     */
    public static float radiusPx(@Nullable PaneSurfaceStyle style, float density) {
        if (style == null)
            return density * DEFAULT_RADIUS_DP;
        return PaneCornerRadius.radiusPx(style.paneCornerRadiusDp(), style.paneGlassCornerRadiusPx(), density);
    }

    /** The gap between tiled panes in dp, or {@code fallbackDp} while no style is attached. */
    public static int gapDp(@Nullable PaneSurfaceStyle style, int fallbackDp) {
        return style != null ? Math.max(0, style.paneGapDp()) : fallbackDp;
    }

    /**
     * Feed one frame's backdrop, or hide it. Idempotent and cheap — the backdrop view is created
     * once per frame, only re-fed here, and a re-feed with the glass it is already wearing costs
     * nothing at all — so this can run on every editor slider tick and on every frost refresh.
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
            style.paneGlassTintColor(), style.paneGlassGrainLayer(),
            style.paneGlassGrainStrength(), radiusPx, style.paneGlassFrostFilter(),
            style.paneGlassCrossfade());
        backdrop.setVisibility(View.VISIBLE);
        return true;
    }

    /**
     * Hand a frame's corner tab the app's wallpaper blur, so the tab is glass wherever it comes
     * out. The tab's material is its own fixed recipe — the blur under a panel scrim — and
     * follows none of the frame's tint, grain or radius; this passes only the shared frame and
     * its filter, or nothing while the app has no frame. Runs wherever {@link #apply} runs, so a
     * frost refresh reaches the tab in the same pass as the slab.
     */
    public static void dressTab(@Nullable PaneSurfaceStyle style,
                                @Nullable com.termux.app.wall.PaneControlsView tab) {
        if (tab == null) return;
        if (style == null) {
            tab.setPaneGlass(null, EMPTY_RECT, null);
            return;
        }
        tab.setPaneGlass(style.paneGlassBlurFrame(), style.paneGlassBlurFrameRect(),
            style.paneGlassFrostFilter());
    }

    private static final android.graphics.Rect EMPTY_RECT = new android.graphics.Rect();

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
