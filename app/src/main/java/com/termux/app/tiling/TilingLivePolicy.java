package com.termux.app.tiling;

import androidx.annotation.NonNull;

/**
 * Where each place of the tiling desktop stands, and how far a column is from the screen (#418).
 * Which places and windows hold views is live window residency's ({@code glass.GlassResidency},
 * the desktop's {@code WindowResidency}): one owner of the rings and the navigation centre.
 *
 * <p>Places stand one above the other by <em>level</em>: Home is 0, Workspace n is n. The view
 * moves by one number, the position (a level, fractional while moving), and every place stands
 * {@link #offsetPx} from it, so motion is derived from positions and nothing is laid out per frame.
 *
 * <p>Pure: no Android views.
 */
public final class TilingLivePolicy {

    private TilingLivePolicy() {}

    /** Home's level: 0, whichever of Home's pages is in front. A Workspace's is its number. */
    public static int level(@NonNull Place place) {
        return place.isHome() ? 0 : place.workspaceNumber();
    }

    /** Where the place at {@code level} stands, in px below the screen's top, at {@code position}. */
    public static float offsetPx(int level, float position, int heightPx) {
        return (level - position) * heightPx;
    }

    /**
     * How far v1's Home has gone up at {@code position}, 0 (at rest) to 1 (wholly away): it rides
     * above Workspace 1 as the stack slides, so the Workspaces' see-through air never shows it.
     * Home moves by its own height times this, so nothing of it jumps at the end of the slide
     * (review of round 3). Without Home (Use Terl as Terminal) it is always away.
     */
    public static float homeTravel(boolean hasHome, float position) {
        if (!hasHome) return 1f;
        return Math.max(0f, Math.min(1f, position));
    }

    /** Whether any of the place at {@code level} shows at {@code position}. */
    public static boolean onScreen(int level, float position) {
        return Math.abs(level - position) < 1f;
    }

    /**
     * How many screens a column from {@code left} to {@code right} stands from a screen
     * {@code width} wide scrolled to {@code scroll}: 0 while any of it is on screen (live window
     * residency's hot ring), else the gap to the nearer screen edge in screen widths.
     */
    public static float screensAway(float left, float right, float scroll, float width) {
        if (width <= 0f) return 0f;
        if (right <= scroll) return (scroll - right) / width;
        if (left >= scroll + width) return (left - (scroll + width)) / width;
        return 0f;
    }
}
