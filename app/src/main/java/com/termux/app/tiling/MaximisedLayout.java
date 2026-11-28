package com.termux.app.tiling;

import androidx.annotation.NonNull;

import java.util.Collections;

/**
 * A maximised window laid out (#425): while it is its Workspace's focused window it alone fills
 * the Workspace's room, and the others are not shown. Pure; the layouts' own answers are kept and
 * only replaced here.
 */
public final class MaximisedLayout {

    private MaximisedLayout() {}

    /**
     * A Scrolling layout with window {@code windowId} filling the screen, which starts
     * {@code scroll} along the strip and is {@code width}×{@code height}; {@code layout} itself
     * when the window is not in it.
     */
    @NonNull
    public static ScrollingLayoutPolicy.Layout over(@NonNull ScrollingLayoutPolicy.Layout layout, long windowId,
                                                    float scroll, float width, float height) {
        ScrollingLayoutPolicy.Placement found = null;
        for (ScrollingLayoutPolicy.Placement p : layout.placements) if (p.windowId == windowId) found = p;
        if (found == null) return layout;
        return new ScrollingLayoutPolicy.Layout(Collections.singletonList(new ScrollingLayoutPolicy.Placement(
            windowId, found.column, 0, scroll, 0f, scroll + width, height)), layout.stripWidth);
    }

    /**
     * A Dwindle layout with window {@code windowId} filling the room {@code width}×{@code height},
     * with no tab strips; {@code tiles} itself when the window is not shown in it.
     */
    @NonNull
    public static DwindleTree.Tiles over(@NonNull DwindleTree.Tiles tiles, long windowId, float width,
                                         float height) {
        ScrollingLayoutPolicy.Layout layout = over(tiles.layout, windowId, 0f, width, height);
        if (layout == tiles.layout) return tiles;
        return new DwindleTree.Tiles(layout, Collections.<DwindleTree.TabStrip>emptyList());
    }
}
