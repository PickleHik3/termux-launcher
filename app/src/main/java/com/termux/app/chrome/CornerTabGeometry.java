package com.termux.app.chrome;

import android.graphics.Path;
import android.graphics.RectF;

import androidx.annotation.NonNull;

/**
 * Where a corner tab goes, and what shape it is. Every frame on the wall answers a corner tap with
 * the same small tab of buttons — the Widgets page, the Display page and a terminal pane — and this
 * is the one place that decides where it lands, so all three agree to the pixel and none of them
 * can drift out of its own frame again.
 *
 * <p>The rule, in one paragraph. A frame's border is a stroke of its own width painted just inside
 * its bounding box, and it is that stroke's <em>inner</em> edge — the visible line the eye reads as
 * the frame — the tab lines up against, never the bounding box. The tab is laid out inside
 * {@code bounds} shrunk by {@code borderStrokePx} on all four sides, and it sits <em>flush</em> in
 * the corner it came out of: its outer edge is the frame's own side, and its outer corner is the
 * frame's own corner, arc and all. So the frame's stroke is the tab's outer outline — there is one
 * line there, not two — and the only boundary the tab draws for itself is the one it shares with
 * the pane's interior ({@link #tabPathPoints}). It grows towards the middle and slides out of the
 * edge its corner is on — down from a top corner, up from a bottom one — so at
 * {@code progress == 0} it is entirely outside the frame and at {@code progress == 1} entirely
 * inside it. It never crosses the far side: a frame too narrow for the buttons at the size they
 * asked for gets a tab that stops {@code edgeMarginPx} short of that edge, with the buttons sharing
 * the room left in proportion rather than walking off the end. Nothing this returns ever lies
 * outside {@code bounds} horizontally.
 *
 * <p>Pure geometry in the caller's own coordinate space: hand it pane-local bounds and the rects
 * come back pane-local; hand it host coordinates and they come back in host coordinates. Nothing
 * here knows what a view is, and no caller may mix the two.
 */
public final class CornerTabGeometry {

    /** Fuzz for the containment tests; a tab is not outside its frame by a thousandth of a pixel. */
    private static final float EPSILON = 0.001f;

    /**
     * The stroke a tab paints its own outline with, in dp. Centred on the line it draws, which is
     * the one boundary the tab has of its own; the frame's own stroke is the rest of its outline.
     */
    public static final float TAB_OUTLINE_DP = 1f;

    /** The points {@link #tabPathPoints} answers with, x and y each. */
    public static final int PATH_POINTS = 5;

    /**
     * How much of the tab's outer end the frame's corner keeps for itself, in dp. The tab is flush
     * in its corner now, so without this its outermost button would sit exactly where the hold that
     * opened it landed, and holding that corner again would run the button instead of putting the
     * tab away. The glyphs are centred well inside their slots, so nothing visible is lost.
     */
    public static final float TAB_CORNER_HOLD_DP = 8f;

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
     * The radius the tab's own two corners turn — the pair on the edge it shares with the pane's
     * interior, the only corners it has that the frame does not already own.
     *
     * <p>It is the frame's own inner radius, so the tab reads as the frame grown rather than as a
     * box tacked onto it, held back only where the tab is too small to turn it: never more than
     * half the tab's width, and never so deep that the corner would reach back into the frame's own
     * arc, where the clip would cut it. A frame rounded almost as deep as the tab is tall gets what
     * is left of the tab past that arc.
     *
     * @param innerRadiusPx the frame's inner radius, from {@link #innerRadiusPx}
     * @param tabDepthPx how deep the tab is, out of the edge it came from
     * @param tabWidthPx how far it runs along that edge
     */
    public static float tabCornerRadiusPx(float innerRadiusPx, float tabDepthPx, float tabWidthPx) {
        float radius = Math.max(0f, innerRadiusPx);
        float clear = Math.max(0f, tabDepthPx) - radius;
        return Math.max(0f, Math.min(Math.min(radius, clear), Math.max(0f, tabWidthPx) / 2f));
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
     * @param edgeMarginPx the least the tab leaves between itself and the far edge
     * @param cornerHoldPx how much of the tab's outer end answers as the frame's corner rather than
     *     as a button, {@link #TAB_CORNER_HOLD_DP} in pixels
     * @param progress 0 fully retracted, 1 fully out
     * @param outTab filled with the tab
     * @param outButtons filled with one hit rectangle per button; must hold at least {@code count}
     */
    public static void layout(int corner, @NonNull RectF bounds, @NonNull float[] widths, int count,
                              float gapPx, float padPx, float heightPx, float borderStrokePx,
                              float edgeMarginPx, float cornerHoldPx, float progress,
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
        float natural = naturalWidth(widths, count, gapPx, padPx);
        // What is left of the frame once the far edge has taken its keep. A tab wider than this is
        // cut down to it rather than hanging over that edge. The corner side keeps nothing: the tab
        // starts on the frame's own side, and its outer corner is the frame's own corner.
        float room = Math.max(0f, (frameRight - frameLeft) - margin);
        float width = Math.min(natural, room);
        float left;
        float right;
        if (CornerZones.isLeft(corner)) {
            left = frameLeft;
            right = left + width;
        } else {
            right = frameRight;
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
        // reach the tab's own edges, bar the sliver the frame's corner keeps.
        float hold = Math.max(0f, Math.min(cornerHoldPx, width / 2f));
        float buttonLeft = CornerZones.isLeft(corner) ? left + hold : left;
        float buttonRight = CornerZones.isLeft(corner) ? right : right - hold;
        float edge = left + padPx;
        for (int i = 0; i < count; i++) {
            float start = i == 0 ? buttonLeft : edge - gapPx / 2f;
            edge += widths[i] * scale;
            float end = i == count - 1 ? buttonRight : edge + gapPx / 2f;
            outButtons[i].set(clamp(start, buttonLeft, buttonRight), top,
                clamp(end, buttonLeft, buttonRight), top + height);
            edge += gapPx;
        }
    }

    /**
     * The tab's <em>free</em> boundary: the line it shares with the pane's interior, from the
     * frame's own side to the frame's own edge. Five points, x then y, in the order the outline is
     * drawn — the frame's side, the two ends of the straight run across the tab, the far end of the
     * second corner, and the point on the edge the tab slid out of. The two turns between them are
     * quadratics through the sharp corners the points bracket, {@code (outer, top)} and
     * {@code (inner, top)}.
     *
     * <p>Everything else of the tab's outline is the frame's own stroke: the outer side and the
     * outer corner are the frame's, which is why the frame's line may never be drawn again under
     * the tab.
     *
     * @param inner the frame the tab lives in, from {@link #innerBounds}
     * @param tab where the tab is, from {@link #layout}
     * @param radiusPx the radius its two corners turn, from {@link #tabCornerRadiusPx}
     * @param out at least {@code PATH_POINTS * 2} floats
     */
    public static void tabPathPoints(int corner, @NonNull RectF inner, @NonNull RectF tab,
                                     float radiusPx, @NonNull float[] out) {
        float radius = Math.max(0f, radiusPx);
        boolean left = CornerZones.isLeft(corner);
        boolean top = CornerZones.isTop(corner);
        // The frame's own side, and the tab's far one; the edge it grew out of, and its own.
        float outerX = left ? inner.left : inner.right;
        float innerX = left ? tab.right : tab.left;
        float dirX = left ? 1f : -1f;
        float topY = top ? tab.bottom : tab.top;
        float dirY = top ? 1f : -1f;
        float slideY = top ? tab.top : tab.bottom;
        out[0] = outerX;
        out[1] = topY - dirY * radius;
        out[2] = outerX + dirX * radius;
        out[3] = topY;
        out[4] = innerX - dirX * radius;
        out[5] = topY;
        out[6] = innerX;
        out[7] = topY - dirY * radius;
        out[8] = innerX;
        out[9] = slideY;
    }

    /**
     * The stroke a tab paints: {@link #tabPathPoints}, and nothing else. The frame draws the rest.
     *
     * @param scratch at least {@code PATH_POINTS * 2} floats, so drawing allocates nothing
     */
    public static void buildTabOutline(int corner, @NonNull RectF inner, @NonNull RectF tab,
                                       float radiusPx, @NonNull float[] scratch,
                                       @NonNull Path out) {
        tabPathPoints(corner, inner, tab, radiusPx, scratch);
        float topY = CornerZones.isTop(corner) ? tab.bottom : tab.top;
        out.reset();
        out.moveTo(scratch[0], scratch[1]);
        out.quadTo(scratch[0], topY, scratch[2], scratch[3]);
        out.lineTo(scratch[4], scratch[5]);
        out.quadTo(scratch[6], topY, scratch[6], scratch[7]);
        out.lineTo(scratch[8], scratch[9]);
    }

    /**
     * The shape a tab fills: the same boundary, closed along the frame's own side and past the edge
     * it came out of. The overshoot is trimmed by the clip the caller sets to the frame's inner
     * shape — which is also what rounds the tab's outer corner to the frame's own arc — so no
     * anti-aliased seam can open between the tab and the line it grew out of.
     *
     * @param overshootPx how far past that edge to run the fill before the clip takes it
     */
    public static void buildTabFill(int corner, @NonNull RectF inner, @NonNull RectF tab,
                                    float radiusPx, float overshootPx, @NonNull float[] scratch,
                                    @NonNull Path out) {
        tabPathPoints(corner, inner, tab, radiusPx, scratch);
        boolean top = CornerZones.isTop(corner);
        float topY = top ? tab.bottom : tab.top;
        float overshoot = Math.max(0f, overshootPx);
        float farY = top ? Math.min(scratch[9], inner.top - overshoot)
            : Math.max(scratch[9], inner.bottom + overshoot);
        out.reset();
        out.moveTo(scratch[0], scratch[1]);
        out.quadTo(scratch[0], topY, scratch[2], scratch[3]);
        out.lineTo(scratch[4], scratch[5]);
        out.quadTo(scratch[6], topY, scratch[6], scratch[7]);
        out.lineTo(scratch[8], farY);
        out.lineTo(scratch[0], farY);
        out.close();
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }
}
