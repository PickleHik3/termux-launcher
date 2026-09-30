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
    /**
     * True while the border preference is on: every place's frame wears a line — the slab's lit
     * rim on glass, the plain stroke otherwise — in every mode, so the border drag that pages the
     * wall ({@code wall/BorderDrag}) has the same target on the Widgets and Display pages as on
     * the terminal. Off, the pages wear a rim only as part of their glass.
     */
    default boolean paneBorderEnabled() { return false; }
    /** The shared pre-blurred wallpaper frame at the configured radius, or null for none. */
    @Nullable android.graphics.Bitmap paneGlassBlurFrame();
    /** That frame's rect in screen coordinates. */
    @NonNull android.graphics.Rect paneGlassBlurFrameRect();
    /** Vibrancy filter applied to the frost, shared with every other glass surface. */
    @Nullable android.graphics.ColorFilter paneGlassFrostFilter();
    /** The terminal tint painted over the frost. */
    int paneGlassTintColor();
    /**
     * The veil a terminal pane laid out at {@code rootRect} is drawn with, over its tint: the
     * terminal's background at the smallest alpha that keeps the palette's worst foreground at the
     * legibility target over whatever wallpaper is under that rect ({@code ChromeInk#terminalPane}).
     * Transparent when nothing is needed or nobody measures.
     *
     * @param rootRect the pane's laid-out rect on screen, every transform ignored — the root
     *     container's own space
     */
    default int paneGlassVeil(@NonNull android.graphics.Rect rootRect) {
        return android.graphics.Color.TRANSPARENT;
    }
    /** Film grain layer for one pane, or null while grain is off. */
    @Nullable android.graphics.drawable.Drawable paneGlassGrainLayer();
    /**
     * The grain strength {@link #paneGlassGrainLayer()} is built from, and a cheap identity for
     * it: the layer is a fresh drawable per call, so a pane comparing what it is already wearing
     * against what it is being handed can only tell the two apart by this.
     */
    int paneGlassGrainStrength();
    /**
     * The preset's rim for a slab at {@code radiusPx}, or null for none. Panes wear no rim of
     * their own under the hairline look (their edge is the shared rim {@link PaneRim} draws), so
     * only a gradient rim answers; it comes from the same factory as every other glass surface.
     */
    @Nullable default android.graphics.drawable.Drawable paneGlassRim(float radiusPx) { return null; }
    /**
     * The shared rim every chrome surface wears (status bar, dock, keyboard), cut at {@code
     * radiusPx}: the preset's hairline or gradient. What a lone pane, and the unfocused panes of a
     * split, draw as their border. Null lets {@link PaneRim} fall back to its own hairline.
     */
    @Nullable default android.graphics.drawable.Drawable paneRimDrawable(float radiusPx) { return null; }
    /** Whether an attention border may pulse: false under Lazy mode and reduced motion (a static glow). */
    default boolean paneAttentionPulses() { return true; }
    /** True while {@link #paneGlassRim} would answer, so a re-dress asks for a drawable only then. */
    default boolean paneGlassRimWanted() { return false; }
    /** Corner radius of a pane slab, in px. */
    float paneGlassCornerRadiusPx();
    /**
     * The terminal's own corner radius knob in dp, which is the shape every pane wears — glass or
     * not, docked or floating, alone or split. Below 0 is the shared "follow the style" sentinel,
     * and the pane falls back to {@link #paneGlassCornerRadiusPx()}; a style with no knob to
     * report says so by leaving it there.
     */
    default int paneCornerRadiusDp() { return -1; }
    /** Gap between tiled panes, in dp — the surface editor's Inner padding. */
    int paneGapDp();

    /**
     * True while {@link #paneGlassBlurFrame()} just arrived to replace one a wallpaper change
     * displaced, so the pane should crossfade into it rather than swap outright — see
     * {@code WallpaperBlurCache.isCrossfadedRadius}. False for a rotation, a radius change, or
     * reduced motion, all of which still want the swap to land on the next frame.
     */
    default boolean paneGlassCrossfade() { return false; }

    /**
     * What sits behind a page where no pane is: the wallpaper as the wall shows it, unblurred,
     * or null when a flat colour is behind. A page whose content cannot be clipped paints its
     * corner arcs with this.
     */
    @Nullable default android.graphics.Bitmap wallBehindFrame() { return null; }

    /**
     * The colour laid over {@link #wallBehindFrame()} - the wall's dim over the wallpaper - or
     * standing alone, opaque, when there is no frame.
     */
    default int wallBehindColor() { return android.graphics.Color.TRANSPARENT; }

    /**
     * The wallpaper's live x-offset, shared with every other glass surface, which a slab samples
     * {@link #paneGlassBlurFrame()} further along by on every draw; null while nothing pans. The
     * frame rect is wider than the screen by the offset's whole travel, so the slab never runs
     * off the picture.
     */
    @Nullable default com.termux.app.chrome.WallpaperParallax wallpaperParallax() { return null; }

    /**
     * Fancier Glass: the refraction every slab draws the frame through — bent under its rim and
     * lit along it — or null for the plain frost, which is the default mode and every phone
     * below API 33. One look for every surface; a slab only applies it at its own corner radius.
     */
    @Nullable default com.termux.app.chrome.GlassRefraction.Look paneGlassRefraction() { return null; }
}
