package com.termux.app.terminal;

import android.graphics.Path;
import android.view.View;

import com.termux.R;

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

    /** Marks "no rim" in {@link #rimRadiusPx}. */
    static final float NO_RIM = -1f;

    /**
     * The radius a pane's preset rim is cut at, or {@link #NO_RIM}: the hairline look draws none
     * on a pane (as before), the gradient look draws the slab's own corner.
     */
    static float rimRadiusPx(boolean rimWanted, float slabRadiusPx) {
        return rimWanted ? Math.max(0f, slabRadiusPx) : NO_RIM;
    }

    /** The gap between tiled panes in dp, or {@code fallbackDp} while no style is attached. */
    public static int gapDp(@Nullable PaneSurfaceStyle style, int fallbackDp) {
        return style != null ? Math.max(0, style.paneGapDp()) : fallbackDp;
    }

    /**
     * The shapes the glass is actually drawn in: one rounded rect per frame that wears a visible
     * slab, cut at the radius {@link #apply} gives it, in {@code origin}'s coordinates. Whatever
     * copies the glass somewhere else (the window-switch card) clips to this, never to the
     * window's rectangle, so the gaps and corners around the slabs keep showing the live
     * wallpaper.
     *
     * @return {@code out}, emptied first
     */
    @NonNull
    public static Path slabOutline(@NonNull Iterable<? extends View> frames, @NonNull View origin,
                                   float requestedRadiusPx, @NonNull Path out) {
        out.rewind();
        int[] at = new int[2];
        int[] base = new int[2];
        origin.getLocationOnScreen(base);
        for (View frame : frames) {
            View backdrop = frame.findViewById(R.id.terminal_pane_glass);
            if (!(backdrop instanceof PaneGlassBackdropView)
                    || backdrop.getVisibility() != View.VISIBLE
                    || frame.getParent() == null || frame.getWidth() <= 0 || frame.getHeight() <= 0)
                continue;
            frame.getLocationOnScreen(at);
            float radius = PaneShape.radiusForBounds(requestedRadiusPx,
                frame.getWidth(), frame.getHeight());
            float left = at[0] - base[0];
            float top = at[1] - base[1];
            out.addRoundRect(left, top, left + frame.getWidth(), top + frame.getHeight(),
                radius, radius, Path.Direction.CW);
        }
        return out;
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
        backdrop.setRim(rimRadiusPx(style.paneGlassRimWanted(), radiusPx), style);
        backdrop.setParallax(style.wallpaperParallax());
        backdrop.setRefraction(style.paneGlassRefraction());
        backdrop.setVisibility(View.VISIBLE);
        return true;
    }

    /**
     * Hand a frame's corner tab the app's wallpaper blur, so the tab is glass wherever it comes
     * out. The tab's material is its own fixed recipe — the blur under a panel scrim — and
     * follows none of the frame's tint, grain or radius; this passes only the shared frame, its
     * filter and Fancier Glass's look — the slab's own refraction, which the tab rims along its
     * own shape — or nothing while the app has no frame. Runs wherever {@link #apply} runs, so a
     * frost refresh or a new look reaches the tab in the same pass as the slab.
     */
    public static void dressTab(@Nullable PaneSurfaceStyle style,
                                @Nullable com.termux.app.wall.PaneControlsView tab) {
        if (tab == null) return;
        if (style == null) {
            tab.setPaneGlass(null, EMPTY_RECT, null);
            return;
        }
        tab.setPaneGlass(style.paneGlassBlurFrame(), style.paneGlassBlurFrameRect(),
            style.paneGlassFrostFilter(), style.wallpaperParallax(), style.paneGlassRefraction());
    }

    private static final android.graphics.Rect EMPTY_RECT = new android.graphics.Rect();

    /**
     * Keep a slab aimed at the wallpaper as its frame is laid out somewhere else. A frame moves in
     * layout for reasons that never redraw it (a sibling's divider drag, a float being dragged,
     * the host resizing under the keyboard), and the frost is positioned in screen space, so every
     * such move has to re-aim the matrix.
     *
     * <p>A wall page sliding is not a layout move — the wall translates its pages — and this
     * listener never sees it. The wall's owner re-aims every slab per frame of a slide instead
     * ({@code onWallMoved} on the pages, {@code invalidatePaneGlassPositions} on the terminal),
     * and the slab reads the page's translation off the wall itself, so the frost stays glued to
     * the wallpaper while the page travels over it.</p>
     */
    public static void followLayout(@Nullable PaneGlassBackdropView backdrop) {
        if (backdrop == null) return;
        backdrop.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (l != ol || t != ot || r != or || b != ob)
                ((PaneGlassBackdropView) v).invalidateGlassPosition();
        });
    }
}
