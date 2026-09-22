package com.termux.app.chrome;

import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * The four squares a frame keeps for itself. Every frame on the wall — the Widgets page, the
 * Display page, a terminal pane, a floating pane, the floating keyboard's card — answers touches
 * at its corners and hands its edges to whatever it is holding, so a maximised X window, a widget
 * that reaches the rim and a terminal's own last column are all touchable to the pixel.
 *
 * <p>Pure geometry: nothing here knows what a view is, and the corners are physical rather than
 * leading and trailing. A frame that wants the leading corner resolves it from its own layout
 * direction and asks for {@link #TOP_LEFT} or {@link #TOP_RIGHT}.
 */
public final class CornerZones {

    /** No corner: the touch belongs to the content. */
    public static final int NONE = -1;
    public static final int TOP_LEFT = 0;
    public static final int TOP_RIGHT = 1;
    public static final int BOTTOM_RIGHT = 2;
    public static final int BOTTOM_LEFT = 3;

    /**
     * The square's side where a corner is taken on the touch itself: the floating keyboard's card,
     * which is dragged from its top corners. A thumb, not a cursor — and no bigger, because every
     * pixel of it is taken from whatever is underneath.
     */
    public static final float SIZE_DP = 32f;

    /**
     * The square's side on a pane or a page, which is <em>held</em> rather than tapped. Bigger,
     * because the content underneath keeps every touch that does not rest there, so the square
     * costs it nothing and can afford the room a slow thumb needs.
     */
    public static final float PANE_SIZE_DP = 40f;

    private CornerZones() {
    }

    public static float sizePx(float density) {
        return SIZE_DP * density;
    }

    /** {@link #PANE_SIZE_DP} in pixels: the held square a pane or a page keeps at each corner. */
    public static float paneSizePx(float density) {
        return PANE_SIZE_DP * density;
    }

    /**
     * The side a square actually gets on a frame this big: never more than half of either
     * dimension, so two corners never overlap and the middle of an edge is never a corner. A
     * pane squeezed down to a few rows still has four corners, just smaller ones.
     */
    public static float clampSize(float sizePx, float width, float height) {
        if (width <= 0f || height <= 0f) return 0f;
        return Math.max(0f, Math.min(sizePx, Math.min(width, height) / 2f));
    }

    /** The corner a touch lands in on a frame of this size, or {@link #NONE}. */
    public static int cornerAt(float x, float y, float width, float height, float sizePx) {
        return cornerAt(x, y, 0f, 0f, width, height, sizePx, 0f);
    }

    /** The same, for a frame that sits somewhere in a larger coordinate system. */
    public static int cornerAt(float x, float y, @NonNull RectF bounds, float sizePx,
                               float slopPx) {
        return cornerAt(x, y, bounds.left, bounds.top, bounds.right, bounds.bottom, sizePx,
            slopPx);
    }

    /**
     * The corner of {@code (left, top, right, bottom)} a touch lands in.
     *
     * @param slopPx how far outside the frame still counts. Panes are separated by a divider,
     *     and the empty pixels in it belong to the corners on either side of it.
     */
    public static int cornerAt(float x, float y, float left, float top, float right, float bottom,
                               float sizePx, float slopPx) {
        float size = clampSize(sizePx, right - left, bottom - top);
        if (size <= 0f) return NONE;
        if (x < left - slopPx || x > right + slopPx || y < top - slopPx || y > bottom + slopPx) {
            return NONE;
        }
        boolean nearLeft = x <= left + size;
        boolean nearRight = x >= right - size;
        boolean nearTop = y <= top + size;
        boolean nearBottom = y >= bottom - size;
        if (nearTop && nearLeft) return TOP_LEFT;
        if (nearTop && nearRight) return TOP_RIGHT;
        if (nearBottom && nearRight) return BOTTOM_RIGHT;
        if (nearBottom && nearLeft) return BOTTOM_LEFT;
        return NONE;
    }

    /** Fills {@code out} with one corner's square, in the frame's own coordinates. */
    public static void cornerRect(int corner, @NonNull RectF bounds, float sizePx,
                                  @NonNull RectF out) {
        float size = clampSize(sizePx, bounds.width(), bounds.height());
        float left = isLeft(corner) ? bounds.left : bounds.right - size;
        float top = isTop(corner) ? bounds.top : bounds.bottom - size;
        out.set(left, top, left + size, top + size);
    }

    public static boolean isTop(int corner) {
        return corner == TOP_LEFT || corner == TOP_RIGHT;
    }

    public static boolean isLeft(int corner) {
        return corner == TOP_LEFT || corner == BOTTOM_LEFT;
    }

    /**
     * The corner on the side a reader starts from, or the side they end on, in this layout
     * direction — which is the only place leading and trailing turn into left and right.
     */
    public static int corner(boolean top, boolean leading, boolean rtl) {
        boolean left = leading != rtl;
        if (top) return left ? TOP_LEFT : TOP_RIGHT;
        return left ? BOTTOM_LEFT : BOTTOM_RIGHT;
    }

    /** Which frame's corner a touch landed in, when several frames are laid out side by side. */
    public static final class Hit {
        /** The frame's index in the list it was picked from. */
        public final int index;
        /** Its corner, one of the constants above. */
        public final int corner;

        Hit(int index, int corner) {
            this.index = index;
            this.corner = corner;
        }
    }

    /**
     * The frame whose corner a touch landed in, or null when it landed on none of them.
     *
     * <p>A frame that actually contains the point wins over one that only reaches it across the
     * slop; among equals the {@code preferredIndex} frame wins, and otherwise the nearest corner
     * does. That order is what keeps the first pane of a split as reachable as every pane created
     * after it: the empty pixels in a shared divider would otherwise always resolve to the
     * newcomer.
     */
    @Nullable
    public static Hit pick(@NonNull List<RectF> frames, int preferredIndex, float x, float y,
                           float sizePx, float slopPx) {
        Hit contained = null;
        Hit preferred = null;
        Hit nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < frames.size(); i++) {
            RectF rect = frames.get(i);
            if (rect == null) continue;
            int corner = cornerAt(x, y, rect, sizePx, slopPx);
            if (corner == NONE) continue;
            if (contained == null && rect.contains(x, y)) contained = new Hit(i, corner);
            if (preferred == null && i == preferredIndex) preferred = new Hit(i, corner);
            float dx = x - (isLeft(corner) ? rect.left : rect.right);
            float dy = y - (isTop(corner) ? rect.top : rect.bottom);
            float distance = dx * dx + dy * dy;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = new Hit(i, corner);
            }
        }
        if (contained != null) return contained;
        return preferred != null ? preferred : nearest;
    }
}
