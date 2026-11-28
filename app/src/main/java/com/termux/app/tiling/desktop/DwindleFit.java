package com.termux.app.tiling.desktop;

import androidx.annotation.NonNull;

import com.termux.app.terminal.DwindleTilingPolicy;
import com.termux.app.tiling.DwindleTree;

/**
 * Whether a Dwindle tile may split to take another (#420): the tile's whole room, tab strip
 * included, goes to {@link DwindleTilingPolicy#placeNewWindow}, which halves it along its longer
 * side as the tree does. Each side's minimum is the largest of its windows', and a side that is
 * tabbed also keeps its tab strip. Pure.
 */
final class DwindleFit implements DwindleTree.Fit {

    /** The smallest a window may be, in px. */
    interface Minimums {
        float width(long windowId);

        float height(long windowId);
    }

    @NonNull private final Minimums mMinimums;
    private final float mGapPx;
    private final float mTabStripPx;

    DwindleFit(@NonNull Minimums minimums, float gapPx, float tabStripPx) {
        mMinimums = minimums;
        mGapPx = gapPx;
        mTabStripPx = tabStripPx;
    }

    @Override
    public boolean splits(float width, float height, @NonNull DwindleTree.Tile focused,
                          @NonNull DwindleTree.Tile incoming) {
        return !DwindleTilingPolicy.placeNewWindow(width, height, minWidth(focused), minHeight(focused),
            minWidth(incoming), minHeight(incoming), mGapPx).isTab();
    }

    /**
     * Whether a tile filling {@code width}×{@code height} may split {@code sideBySide} (else top
     * over bottom) to take {@code incoming}, whichever its longer side: both halves keep the larger
     * of the two tiles' minimums that way, and the whole room across (leader+Shift+J, review of
     * terl-window-ops).
     */
    boolean splitsAcross(float width, float height, @NonNull DwindleTree.Tile focused,
                         @NonNull DwindleTree.Tile incoming, boolean sideBySide) {
        float minW = Math.max(minWidth(focused), minWidth(incoming));
        float minH = Math.max(minHeight(focused), minHeight(incoming));
        if (sideBySide) return (width - mGapPx) / 2f >= minW && height >= minH;
        return (height - mGapPx) / 2f >= minH && width >= minW;
    }

    private float minWidth(@NonNull DwindleTree.Tile tile) {
        float min = 0f;
        for (long id : tile.windows) min = Math.max(min, mMinimums.width(id));
        return min;
    }

    private float minHeight(@NonNull DwindleTree.Tile tile) {
        float min = 0f;
        for (long id : tile.windows) min = Math.max(min, mMinimums.height(id));
        return min + (tile.windows.length > 1 ? mTabStripPx : 0f);
    }
}
