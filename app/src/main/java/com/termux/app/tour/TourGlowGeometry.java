package com.termux.app.tour;

import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Where the run's two marks — the glow around a control and the finger cue on it — are allowed to
 * land, and the corner square a pane answers a hold in.
 *
 * <p>Pure arithmetic in overlay pixels, so a control against the screen's own edge is a unit test
 * rather than a phone. Two of the three bugs this exists for only ever show up there: the first key
 * of the keys row starts at the very edge of the screen, and a pane's corner square deliberately
 * reaches outside the pane, so both of them drew a soft halo off the side of the display.
 *
 * <p>The rule for both marks is the same one: they are kept inside the overlay by shrinking on the
 * side that is short, never by sliding the whole mark along — a glow that slid would be a glow
 * around the control next to the one the card is about.
 */
public final class TourGlowGeometry {

    /** How far from the overlay's own edges a mark must stay, in dp. */
    public static final float EDGE_MARGIN_DP = 2f;

    private TourGlowGeometry() {}

    /**
     * The square a frame answers a corner hold in, as the panes themselves measure it: the held
     * square at the frame's top-left, grown outward by the slop the frame keeps beyond its own
     * edge, so the square straddles the border rather than sitting inside the content.
     *
     * @param frame the pane's frame in overlay pixels — the frame, not the terminal inside it,
     *     which is held clear of the frame's corner arcs
     * @param sizePx the square's side, already clamped to the frame
     * @param slopPx how far outside the frame the square reaches
     * @return false when the frame is too small to have a corner at all
     */
    public static boolean cornerZone(@NonNull Rect frame, float sizePx, float slopPx,
                                     @NonNull Rect out) {
        int size = Math.round(sizePx);
        if (frame.isEmpty() || size <= 0) return false;
        int slop = Math.max(0, Math.round(slopPx));
        out.set(frame.left - slop, frame.top - slop, frame.left + size, frame.top + size);
        return true;
    }

    /**
     * The glow's own rect: the control padded outward, then held inside the overlay.
     *
     * <p>{@code reachPx} is everything the stroke draws beyond that rect — half its width and the
     * whole of its blurred halo — because that, and not the rect, is what the user sees cross the
     * edge of the screen.
     *
     * @param target the control in overlay pixels
     * @param paddingPx the gap the glow keeps outside the control
     * @param reachPx how far the stroke and its halo draw beyond the glow's rect
     * @param marginPx how far inside the overlay the outermost pixel must stay
     */
    public static void glowRect(@NonNull Rect target, float paddingPx, float reachPx,
                                int overlayWidth, int overlayHeight, float marginPx,
                                @NonNull RectF out) {
        float limit = Math.max(0f, marginPx) + Math.max(0f, reachPx);
        float left = Math.max(target.left - paddingPx, limit);
        float top = Math.max(target.top - paddingPx, limit);
        float right = Math.min(target.right + paddingPx, overlayWidth - limit);
        float bottom = Math.min(target.bottom + paddingPx, overlayHeight - limit);
        // An overlay too narrow to hold the mark at all: keep it centred rather than inside out.
        if (right < left) {
            float middle = (overlayWidth) / 2f;
            left = middle;
            right = middle;
        }
        if (bottom < top) {
            float middle = (overlayHeight) / 2f;
            top = middle;
            bottom = middle;
        }
        out.set(left, top, right, bottom);
    }

    /**
     * The area the finger cue's own circle may have its centre in: the overlay, brought in by the
     * margin and by the widest ring the cue ever draws.
     *
     * @param reachPx the cue's widest radius, halo included
     */
    public static void cueBounds(int overlayWidth, int overlayHeight, float reachPx,
                                 float marginPx, @NonNull RectF out) {
        float limit = Math.max(0f, marginPx) + Math.max(0f, reachPx);
        float left = limit;
        float top = limit;
        float right = overlayWidth - limit;
        float bottom = overlayHeight - limit;
        if (right < left) {
            float middle = overlayWidth / 2f;
            left = middle;
            right = middle;
        }
        if (bottom < top) {
            float middle = overlayHeight / 2f;
            top = middle;
            bottom = middle;
        }
        out.set(left, top, right, bottom);
    }

    /** Holds a point the trace worked out inside {@code bounds}; a null bounds leaves it alone. */
    public static void clampPoint(@NonNull float[] point, @Nullable RectF bounds) {
        if (bounds == null) return;
        point[0] = clamp(point[0], bounds.left, bounds.right);
        point[1] = clamp(point[1], bounds.top, bounds.bottom);
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }
}
