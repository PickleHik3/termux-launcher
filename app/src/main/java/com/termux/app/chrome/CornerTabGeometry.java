package com.termux.app.chrome;

import android.graphics.RectF;

import androidx.annotation.NonNull;

/**
 * Where a corner tab goes. Every frame on the wall answers a corner tap with the same small tab of
 * buttons — the Widgets page, the Display page and a terminal pane — and this is the one place that
 * decides where it lands, so all three agree to the pixel and none of them can drift out of its
 * own frame again.
 *
 * <p>The rule, in one paragraph. The tab hangs off the corner that was tapped: its outer edge sits
 * {@code cornerInsetPx} in from that side (the depth of the frame's own corner arc, so a rounded
 * frame does not cut the tab's corner off), and it grows towards the middle. It slides out of the
 * edge that corner is on — down from a top corner, up from a bottom one — so at
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
     * Lay one corner tab out.
     *
     * @param corner which corner it comes out of, a {@link CornerZones} constant
     * @param bounds the frame it belongs to, in the caller's coordinate space
     * @param widths each button's width, leading to trailing; only the first {@code count} are read
     * @param count how many buttons the tab carries
     * @param gapPx the space between two buttons
     * @param padPx the tab's own padding inside its leading and trailing edges
     * @param heightPx how deep the tab is once it is fully out
     * @param cornerInsetPx how far in from the frame's own edge the tab starts — the corner arc's
     *     depth on a rounded frame, 0 on a square one
     * @param edgeMarginPx the least the tab leaves between itself and the far edge
     * @param progress 0 fully retracted, 1 fully out
     * @param outTab filled with the tab
     * @param outButtons filled with one hit rectangle per button; must hold at least {@code count}
     */
    public static void layout(int corner, @NonNull RectF bounds, @NonNull float[] widths, int count,
                              float gapPx, float padPx, float heightPx, float cornerInsetPx,
                              float edgeMarginPx, float progress, @NonNull RectF outTab,
                              @NonNull RectF[] outButtons) {
        for (int i = 0; i < outButtons.length; i++) outButtons[i].setEmpty();
        if (count <= 0 || bounds.width() <= 0f || bounds.height() <= 0f) {
            outTab.setEmpty();
            return;
        }
        float margin = Math.max(0f, edgeMarginPx);
        float inset = Math.max(margin, cornerInsetPx);
        float natural = naturalWidth(widths, count, gapPx, padPx);
        // What is left of the frame once both edges have taken their keep. A tab wider than this
        // is cut down to it rather than hanging over the far side.
        float room = Math.max(0f, bounds.width() - inset - margin);
        float width = Math.min(natural, room);
        float left;
        float right;
        if (CornerZones.isLeft(corner)) {
            left = bounds.left + inset;
            right = left + width;
        } else {
            right = bounds.right - inset;
            left = right - width;
        }
        float height = Math.min(heightPx, bounds.height());
        // Out of the edge its corner is on: down from a top corner, up from a bottom one.
        float top = CornerZones.isTop(corner)
            ? bounds.top - height * (1f - progress)
            : bounds.bottom - height * progress;
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
