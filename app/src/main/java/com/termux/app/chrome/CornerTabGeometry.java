package com.termux.app.chrome;

import android.graphics.RectF;

import androidx.annotation.NonNull;

/**
 * Where a corner tab goes. Every frame on the wall answers a corner tap with the same small tab of
 * buttons — the Widgets page, the Display page and a terminal pane — and this is the one place that
 * decides where it lands, so all three agree to the pixel and none of them can drift out of its
 * own frame again.
 *
 * <p>The rule, in one paragraph. A frame's border is a stroke of its own width painted just inside
 * its bounding box, and it is that stroke's <em>inner</em> edge — the visible line the eye reads as
 * the frame — the tab lines up against, never the bounding box. So the tab is laid out inside
 * {@code bounds} shrunk by {@code borderStrokePx} on all four sides, and inside that it starts
 * {@link #cornerInsetPx} in from the side its corner is on: far enough that its outer corner clears
 * the arc the border turns there, whatever radius the user has set, and far enough again that the
 * tab's own outline — which is centred on that edge — falls inside the line rather than across it.
 * It grows towards the middle and slides out of the edge its corner is on — down from a top corner,
 * up from a bottom one — so at {@code progress == 0} it is entirely outside the frame and at
 * {@code progress == 1} entirely inside it. It never crosses the far side: a frame too narrow for
 * the buttons at the size they asked for gets a tab that stops {@code edgeMarginPx} short of that
 * edge, with the buttons sharing the room left in proportion rather than walking off the end.
 * Nothing this returns ever lies outside {@code bounds} horizontally.
 *
 * <p>Pure geometry in the caller's own coordinate space: hand it pane-local bounds and the rects
 * come back pane-local; hand it host coordinates and they come back in host coordinates. Nothing
 * here knows what a view is, and no caller may mix the two.
 */
public final class CornerTabGeometry {

    /** Fuzz for the containment tests; a tab is not outside its frame by a thousandth of a pixel. */
    private static final float EPSILON = 0.001f;

    /**
     * The stroke a tab paints its own outline with, in dp. Centred on the tab's edges, so half of
     * it lies outside them — which is why {@link #cornerInsetPx} adds that half to the inset.
     */
    public static final float TAB_OUTLINE_DP = 1f;

    /**
     * How far past its outer edge a tab would like to flare its outline along the frame's edge —
     * the small "ears" that tie the tab into the border it comes out of. What it actually gets is
     * {@link #earReachPx}, which is this or the room there is, whichever is less.
     */
    public static final float TAB_EAR_DP = 5f;

    private CornerTabGeometry() {
    }

    /**
     * The width a tab wants before the frame gets a say: its padding, the gaps between buttons and
     * the buttons themselves.
     */
    public static float naturalWidth(@NonNull float[] widths, int count, float gapPx, float padPx) {
        if (count <= 0) return 0f;
        float width = padPx * 2f + gapPx * (count - 1);
        for (int i = 0; i < count; i++) width += widths[i];
        return width;
    }

    /**
     * The radius of the border's <em>inner</em> edge — the line the tab lines up against. A stroke
     * of width {@code w} painted just inside a box of radius {@code r} turns an arc of
     * {@code r - w} on its inside; a stroke thicker than the radius leaves a square inner corner.
     */
    public static float innerRadiusPx(float radiusPx, float borderStrokePx) {
        return Math.max(0f, Math.max(0f, radiusPx) - Math.max(0f, borderStrokePx));
    }

    /** The frame the tab is laid out inside: {@code bounds} shrunk by the border it lines up on. */
    public static void innerBounds(@NonNull RectF bounds, float borderStrokePx,
                                   @NonNull RectF out) {
        float border = Math.max(0f, borderStrokePx);
        out.set(bounds.left + border, bounds.top + border,
            bounds.right - border, bounds.bottom - border);
    }

    /**
     * Where an arc of {@code radiusPx} has curved to {@code depthPx} below the edge it starts
     * from: {@code r - sqrt(r² - (r - d)²)}, the horizontal distance from the side to the arc at
     * that depth. Zero at the edge itself, the full {@code r} once the arc has run out.
     *
     * <p>This is the tangent a tab's far corner has to clear. It is not, on its own, where the tab
     * starts — see {@link #arcClearancePx}, which also has to answer for the rest of the tab's
     * outer edge, nearer the edge, where the arc is wider.
     */
    public static float arcTangentPx(float radiusPx, float depthPx) {
        float radius = Math.max(0f, radiusPx);
        if (radius <= 0f) return 0f;
        float depth = Math.max(0f, depthPx);
        if (depth >= radius) return radius;
        float rise = radius - depth;
        return radius - (float) Math.sqrt(Math.max(0f, radius * radius - rise * rise));
    }

    /**
     * How much room a corner arc of {@code radiusPx} takes from a tab {@code depthPx} deep — how
     * far in from the side the tab has to start so the arc never cuts it.
     *
     * <p>A tab at least as deep as the arc crosses the whole of it, so it starts past the arc's
     * full depth, {@code r}: that is every tab at every radius the surface editor offers by
     * default, and it is what puts the tab's outer edge exactly where the border's straight run
     * begins. A tab shallower than the arc cannot do that without being pushed most of the way
     * across its own frame, so it starts at its own depth instead — the corner's diagonal, which
     * clears {@link #arcTangentPx} with room to spare and meets the {@code r} rule continuously as
     * the radius comes back down. A square corner takes nothing.
     */
    public static float arcClearancePx(float radiusPx, float depthPx) {
        return Math.min(Math.max(0f, radiusPx), Math.max(0f, depthPx));
    }

    /**
     * How far in from the <em>inner</em> frame ({@link #innerBounds}) a tab's outer edge sits: past
     * the corner arc the border turns, and half the tab's own outline again so that outline —
     * which is centred on the edge — lands inside the border's line rather than across it.
     *
     * @param radiusPx the radius the frame is drawn at, as the user set it; 0 for a square frame
     * @param borderStrokePx the frame's own border stroke, 0 when it paints no border
     * @param tabHeightPx how deep the tab is once it is fully out
     * @param outlineStrokePx the stroke the tab outlines itself with
     */
    public static float cornerInsetPx(float radiusPx, float borderStrokePx, float tabHeightPx,
                                      float outlineStrokePx) {
        return arcClearancePx(innerRadiusPx(radiusPx, borderStrokePx), tabHeightPx)
            + Math.max(0f, outlineStrokePx) / 2f;
    }

    /**
     * How far a tab may actually flare its ears past its outer edge. They run along the frame's
     * edge, where the arc is at its widest, so on a rounded frame there is less room for them than
     * the tab itself got — and none at all on a frame whose border the tab is already flush
     * against. Shortening them is what keeps them off the border instead of across it.
     *
     * @param wantPx the flare the tab would like, {@link #TAB_EAR_DP} in pixels
     */
    public static float earReachPx(float wantPx, float radiusPx, float borderStrokePx,
                                   float tabHeightPx, float outlineStrokePx) {
        float want = Math.max(0f, wantPx);
        if (want <= 0f) return 0f;
        float outline = Math.max(0f, outlineStrokePx);
        float inner = innerRadiusPx(radiusPx, borderStrokePx);
        // The ear is a stroke lying on the frame's inner edge, so its own far half is the shallowest
        // thing the tab paints, and that is the depth the arc has to be measured at.
        float room = cornerInsetPx(radiusPx, borderStrokePx, tabHeightPx, outline)
            - outline / 2f - arcTangentPx(inner, outline / 2f);
        return Math.max(0f, Math.min(want, room));
    }

    /**
     * Whether a point lies on or inside the frame's border — the rounded rectangle the border's
     * inner edge traces. This is the shape everything a tab paints has to stay within, and the
     * shape the callers clip to.
     */
    public static boolean insideBorder(@NonNull RectF bounds, float radiusPx, float borderStrokePx,
                                       float x, float y) {
        float border = Math.max(0f, borderStrokePx);
        float left = bounds.left + border;
        float top = bounds.top + border;
        float right = bounds.right - border;
        float bottom = bounds.bottom - border;
        if (x < left - EPSILON || x > right + EPSILON
            || y < top - EPSILON || y > bottom + EPSILON) {
            return false;
        }
        float radius = Math.min(innerRadiusPx(radiusPx, borderStrokePx),
            Math.min((right - left) / 2f, (bottom - top) / 2f));
        if (radius <= 0f) return true;
        // The nearest point of the rectangle the arcs are centred on; a point outside that
        // rectangle is inside the shape only while it is within one radius of that centre.
        float cx = Math.max(left + radius, Math.min(right - radius, x));
        float cy = Math.max(top + radius, Math.min(bottom - radius, y));
        float dx = x - cx;
        float dy = y - cy;
        // A point on the arc is on the arc: the slack is a hair of radius, not of radius squared,
        // or a wide corner would fail its own tangent to floating-point noise.
        float slack = radius + EPSILON;
        return dx * dx + dy * dy <= slack * slack;
    }

    /**
     * Lay one corner tab out.
     *
     * @param corner which corner it comes out of, a {@link CornerZones} constant
     * @param bounds the frame it belongs to, in the caller's coordinate space
     * @param widths each button's width, leading to trailing; only the first {@code count} are read
     * @param count how many buttons the tab carries
     * @param gapPx the space between two buttons
     * @param padPx the tab's own padding inside its leading and trailing edges
     * @param heightPx how deep the tab is once it is fully out
     * @param borderStrokePx the frame's own border stroke; the tab is laid out inside its inner
     *     edge, not against the bounding box, so it lines up with the line the eye reads
     * @param cornerInsetPx how far in from that inner edge the tab starts, from
     *     {@link #cornerInsetPx}
     * @param edgeMarginPx the least the tab leaves between itself and the far edge
     * @param progress 0 fully retracted, 1 fully out
     * @param outTab filled with the tab
     * @param outButtons filled with one hit rectangle per button; must hold at least {@code count}
     */
    public static void layout(int corner, @NonNull RectF bounds, @NonNull float[] widths, int count,
                              float gapPx, float padPx, float heightPx, float borderStrokePx,
                              float cornerInsetPx, float edgeMarginPx, float progress,
                              @NonNull RectF outTab, @NonNull RectF[] outButtons) {
        for (int i = 0; i < outButtons.length; i++) outButtons[i].setEmpty();
        // The frame the tab actually lives in: inside the border, which is what it lines up on.
        float border = Math.max(0f, borderStrokePx);
        float frameLeft = bounds.left + border;
        float frameTop = bounds.top + border;
        float frameRight = bounds.right - border;
        float frameBottom = bounds.bottom - border;
        if (count <= 0 || frameRight <= frameLeft || frameBottom <= frameTop) {
            outTab.setEmpty();
            return;
        }
        float margin = Math.max(0f, edgeMarginPx);
        float inset = Math.max(margin, cornerInsetPx);
        float natural = naturalWidth(widths, count, gapPx, padPx);
        // What is left of the frame once both edges have taken their keep. A tab wider than this
        // is cut down to it rather than hanging over the far side.
        float room = Math.max(0f, (frameRight - frameLeft) - inset - margin);
        float width = Math.min(natural, room);
        float left;
        float right;
        if (CornerZones.isLeft(corner)) {
            left = frameLeft + inset;
            right = left + width;
        } else {
            right = frameRight - inset;
            left = right - width;
        }
        float height = Math.min(heightPx, frameBottom - frameTop);
        // Out of the edge its corner is on: down from a top corner, up from a bottom one.
        float top = CornerZones.isTop(corner)
            ? frameTop - height * (1f - progress)
            : frameBottom - height * progress;
        outTab.set(left, top, right, top + height);

        // The buttons at the size they asked for, or shrunk in proportion when the tab had to be.
        float fixed = padPx * 2f + gapPx * (count - 1);
        float asked = natural - fixed;
        float scale = asked <= 0f ? 0f
            : Math.max(0f, Math.min(1f, (width - fixed) / asked));
        // Each button's hit rectangle takes half the gap to either side and the full tab height, so
        // a thumb that lands between or just past the glyphs still counts — and the outermost two
        // reach the tab's own edges.
        float edge = left + padPx;
        for (int i = 0; i < count; i++) {
            float start = i == 0 ? left : edge - gapPx / 2f;
            edge += widths[i] * scale;
            float end = i == count - 1 ? right : edge + gapPx / 2f;
            outButtons[i].set(clamp(start, left, right), top, clamp(end, left, right), top + height);
            edge += gapPx;
        }
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }
}
