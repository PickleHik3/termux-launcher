package com.termux.app.tiling;

import androidx.annotation.NonNull;

import com.termux.app.wall.LiveNeighbourPolicy;

import java.util.HashSet;
import java.util.Set;

/**
 * Which places and windows of the tiling desktop hold views, and where each place stands (#418;
 * spec "What can be built from what exists"). It is {@link LiveNeighbourPolicy} in two
 * directions: up and down, only the place in front and its neighbours above and below are live;
 * sideways, within a live Workspace, only the windows on screen and one screen either side.
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

    /**
     * Whether the place at {@code level} holds views while {@code currentLevel} is in front: the
     * place itself or a neighbour, in a stack of Home (when {@code hasHome}) and
     * {@code workspaceCount} Workspaces.
     */
    public static boolean isLiveLevel(int level, int currentLevel, boolean hasHome, int workspaceCount) {
        int first = hasHome ? 0 : 1;
        int count = workspaceCount + (hasHome ? 1 : 0);
        int index = level - first;
        int current = currentLevel - first;
        if (index < 0 || index >= count || current < 0 || current >= count) return false;
        return index == current
            || LiveNeighbourPolicy.neighbourIndex(true, current, count, -1) == index
            || LiveNeighbourPolicy.neighbourIndex(true, current, count, 1) == index;
    }

    /**
     * The level navigation mode keeps live around (#433; spec: navigation mode keeps the Workspaces
     * above and below ready while it is on): the place nearest the view's {@code position}, so as
     * the view slides the places on either side of it are ready before they show.
     */
    public static int navigationCentre(float position, boolean hasHome, int workspaceCount) {
        int first = hasHome ? 0 : 1;
        int last = Math.max(first, workspaceCount);
        return Math.max(first, Math.min(last, Math.round(position)));
    }

    /** Where the place at {@code level} stands, in px below the screen's top, at {@code position}. */
    public static float offsetPx(int level, float position, int heightPx) {
        return (level - position) * heightPx;
    }

    /** Whether any of the place at {@code level} shows at {@code position}. */
    public static boolean onScreen(int level, float position) {
        return Math.abs(level - position) < 1f;
    }

    /**
     * The windows of a Scrolling Workspace that hold views: every window whose column reaches into
     * the screen or one screen width either side of it.
     */
    @NonNull
    public static Set<Long> liveWindows(@NonNull ScrollingLayoutPolicy.State state,
                                        @NonNull ScrollingLayoutPolicy.Viewport viewport) {
        Set<Long> live = new HashSet<>();
        float from = state.scroll - viewport.width;
        float to = state.scroll + 2f * viewport.width;
        for (ScrollingLayoutPolicy.Placement placement
                : ScrollingLayoutPolicy.layout(state, viewport).placements) {
            if (placement.right > from && placement.left < to) live.add(placement.windowId);
        }
        return live;
    }
}
