package com.termux.app.tiling.navigation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Where a pan in navigation mode comes to rest (#433; spec "Moving around": letting go settles on
 * the nearest window). The view lists every window it can show, and every place with no window
 * (Home, an empty Workspace) as a whole, each where it stands on screen at the lift; the nearest
 * to the view's centre wins, across places: the one holding the centre, else the one with the
 * nearest edge, and between two that hold it the one whose middle is nearer.
 *
 * <p>Pure: no Android views.
 */
public final class NavigationSettle {

    private NavigationSettle() {}

    /** A candidate that is a place, not a window. */
    public static final long NO_WINDOW = -1L;

    /** A window, or a place with none, where it stands on screen, in px. */
    public static final class Candidate {
        /** Its place's level: Home 0, Workspace n n. */
        public final int level;
        /** The window, or {@link #NO_WINDOW} for the place itself. */
        public final long windowId;
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public Candidate(int level, long windowId, float left, float top, float right, float bottom) {
            this.level = level;
            this.windowId = windowId;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    /** Where the view settles: a place, and the window in it to focus ({@link #NO_WINDOW} for none). */
    public static final class Target {
        public final int level;
        public final long windowId;

        Target(int level, long windowId) {
            this.level = level;
            this.windowId = windowId;
        }
    }

    /** The candidate nearest ({@code x}, {@code y}), the view's centre; null when there is none. */
    @Nullable
    public static Target nearest(@NonNull List<Candidate> candidates, float x, float y) {
        Candidate best = null;
        float bestEdge = Float.MAX_VALUE;
        float bestMiddle = Float.MAX_VALUE;
        for (Candidate c : candidates) {
            float dx = Math.max(0f, Math.max(c.left - x, x - c.right));
            float dy = Math.max(0f, Math.max(c.top - y, y - c.bottom));
            float edge = dx * dx + dy * dy;
            float mx = (c.left + c.right) / 2f - x;
            float my = (c.top + c.bottom) / 2f - y;
            float middle = mx * mx + my * my;
            if (best == null || edge < bestEdge || (edge == bestEdge && middle < bestMiddle)) {
                best = c;
                bestEdge = edge;
                bestMiddle = middle;
            }
        }
        return best == null ? null : new Target(best.level, best.windowId);
    }
}
