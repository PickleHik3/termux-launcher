package com.termux.app.dock;

import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.termux.app.chrome.GlassRefraction;

/**
 * The numbers and view writes of one Floating capsule's glass, which the dock's host and the
 * Terminal's extra-keys card ({@link KeysCardDress}) both dress through, so the two cannot drift.
 * Each is the body the activity ran inline for the host, moved here with its inputs as
 * parameters; nothing in it reads a view of its own, so the host and the card, given the same
 * size, get the same answer ({@code CapsuleGlassTest}).
 */
public final class CapsuleGlass {

    private CapsuleGlass() {}

    /** How far the backdrop view reaches past each side of its surface, so the blur has ground to read. */
    public static int horizontalOverscanPx(float blurRadiusPx, float density) {
        return Math.max(0, Math.round((blurRadiusPx * 2f) + (density * 2f)));
    }

    /**
     * How far it reaches below, so the refraction's bottom edge band falls under the visible
     * seam with the strip under the pill: the sides' overscan when a foot abuts the dock, else 0.
     */
    public static int seamOverscanPx(int dockFootPx, int horizontalOverscanPx) {
        return dockFootPx > 0 ? horizontalOverscanPx : 0;
    }

    /** The Floating card's radius for a surface this tall, Corners capped at half of it. */
    public static float radiusPx(float layoutCornersPx, int surfaceHeightPx) {
        return Math.min(layoutCornersPx, Math.max(0, surfaceHeightPx) / 2f);
    }

    /**
     * Sizes {@code backdrop} to its surface plus the overscan: wider by {@code horizontalOverscanPx}
     * each side and taller by {@code bottomOverscanPx} (clipped by the surface), anchored top-start.
     * Written only when a number differs.
     */
    public static void applyOverscan(@NonNull ImageView backdrop, int surfaceWidthPx,
                                     int surfaceHeightPx, int horizontalOverscanPx,
                                     int bottomOverscanPx) {
        ViewGroup.LayoutParams layoutParams = backdrop.getLayoutParams();
        if (!(layoutParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) layoutParams;
        int targetWidth = Math.max(1, surfaceWidthPx + (horizontalOverscanPx * 2));
        // Grow the bitmap DOWN past the surface (clipped by the host) so the refraction's bottom edge
        // band falls below the visible seam with the under-pill strip, not on it.
        int targetHeight = Math.max(1, surfaceHeightPx + bottomOverscanPx);
        int targetLeftMargin = -horizontalOverscanPx;
        int targetTopMargin = 0;
        if (params.width != targetWidth || params.height != targetHeight ||
            params.leftMargin != targetLeftMargin || params.topMargin != targetTopMargin) {
            params.width = targetWidth;
            params.height = targetHeight;
            params.leftMargin = targetLeftMargin;
            params.topMargin = targetTopMargin;
            params.rightMargin = 0;
            params.bottomMargin = 0;
            params.gravity = Gravity.TOP | Gravity.START;
            backdrop.setLayoutParams(params);
        }
    }

    /** Where the overscanned backdrop stands on screen, given its surface's screen origin. */
    @NonNull
    public static Rect targetRect(@NonNull int[] surfaceOriginOnScreen, int surfaceWidthPx,
                                  int surfaceHeightPx, int horizontalOverscanPx,
                                  int bottomOverscanPx) {
        return new Rect(
            surfaceOriginOnScreen[0] - horizontalOverscanPx,
            surfaceOriginOnScreen[1],
            surfaceOriginOnScreen[0] + Math.max(1, surfaceWidthPx) + horizontalOverscanPx,
            surfaceOriginOnScreen[1] + Math.max(1, surfaceHeightPx) + bottomOverscanPx
        );
    }

    /**
     * The shader's rect for a capsule inside its overscanned backdrop: left and right inset by the
     * overscan, top to bottom spanning the full height, the bottom put at the overscanned
     * bitmap's bottom (below the visible seam) so the visible bottom reads as interior glass, then
     * the rim rect {@link GlassRefraction#rimRect} makes of it. {@code out} gets left, top, right,
     * bottom.
     */
    public static void capRect(@NonNull float[] out, int horizontalOverscanPx, int surfaceWidthPx,
                               int surfaceHeightPx, int seamOverscanPx, float seamReachPx,
                               int seamEdges) {
        float capLeft = horizontalOverscanPx;
        float capRight = horizontalOverscanPx + Math.max(1, surfaceWidthPx);
        float capBottom = Math.max(1, surfaceHeightPx) + seamOverscanPx;
        GlassRefraction.rimRect(out, capLeft, 0f, capRight, capBottom, seamReachPx, seamEdges);
    }

    /**
     * The faint shadow seam along the top of a glass card: the crisp glass top edge is the
     * refraction shader's, so no highlight band is drawn here (it read as a plastic sheen).
     */
    public static void dressTopEdge(@NonNull View edgeFx, boolean visible, float barAlpha,
                                    int outlineColor) {
        if (!visible) {
            edgeFx.setVisibility(View.GONE);
            edgeFx.setBackground(null);
            return;
        }

        // No white top highlight band — it read as a plastic sheen. The crisp glass top edge is now
        // produced by the AGSL refraction shader on the backdrop. Keep only a faint shadow seam.
        int highlight = Color.TRANSPARENT;
        int shadow = withAlpha(outlineColor, Math.round(18f * Math.max(0f, barAlpha)));
        GradientDrawable edge = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { highlight, shadow, Color.TRANSPARENT }
        );
        edgeFx.setBackground(edge);
        edgeFx.setVisibility(View.VISIBLE);
    }

    /** {@code color} with its alpha channel replaced by {@code alpha}, clamped to 0..255. */
    static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
