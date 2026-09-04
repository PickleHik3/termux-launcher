package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How a pane dresses itself as glass. Supplied by the activity, which owns the shared
 * pre-blurred wallpaper frame, the terminal tint and the surface-editor preferences; a pane only
 * asks what to paint and how far apart to sit.
 *
 * <p>Every page of the pane wall reads the same style, so the Widgets page follows the Canvas
 * surface exactly as a terminal pane does. {@link PaneGlass} and {@link PaneRim} apply it.
 */
public interface PaneSurfaceStyle {
    /** True while each pane should carry its own glass slab (frost, tint, grain, rim). */
    boolean isPaneGlassActive();
    /** The shared pre-blurred wallpaper frame at the configured radius, or null for none. */
    @Nullable android.graphics.Bitmap paneGlassBlurFrame();
    /** That frame's rect in screen coordinates. */
    @NonNull android.graphics.Rect paneGlassBlurFrameRect();
    /** Vibrancy filter applied to the frost, shared with every other glass surface. */
    @Nullable android.graphics.ColorFilter paneGlassFrostFilter();
    /** The terminal tint painted over the frost. */
    int paneGlassTintColor();
    /** Film grain layer for one pane, or null while grain is off. */
    @Nullable android.graphics.drawable.Drawable paneGlassGrainLayer();
    /** Corner radius of a pane slab, in px. */
    float paneGlassCornerRadiusPx();
    /** Gap between tiled panes, in dp — the surface editor's Inner padding. */
    int paneGapDp();
}
