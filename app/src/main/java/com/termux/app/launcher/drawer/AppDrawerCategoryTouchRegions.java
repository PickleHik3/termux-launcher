package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

/** Half-open category hit testing from final laid-out bounds. */
public final class AppDrawerCategoryTouchRegions {
    public enum Part {
        OVERVIEW_LIST, EXPAND_ACTION, DETAIL_LIST, COLLAPSE_ACTION,
        /**
         * An open folder's pane outside its header and grid: the band above a short folder's
         * bottom-anchored rows. It is the folder's, not the plane's, so a pull down here collapses
         * the folder instead of closing the drawer.
         */
        DETAIL_EMPTY,
        TRANSITION_BODY, EMPTY_CHROME, OUTSIDE
    }

    public enum Presentation { OVERVIEW, EXPANDING, EXPANDED, COLLAPSE_DRAGGING, COLLAPSING }

    private AppDrawerCategoryTouchRegions() {}

    @NonNull
    public static Part resolve(float x, float y, @Nullable Frame body,
        @NonNull Presentation presentation, @Nullable Frame overviewList,
        @Nullable Frame expandAction, @Nullable Frame detailHeader, @Nullable Frame detailList) {
        if (!contains(body, x, y)) return Part.OUTSIDE;
        if (presentation == Presentation.EXPANDING || presentation == Presentation.COLLAPSING
            || presentation == Presentation.COLLAPSE_DRAGGING) return Part.TRANSITION_BODY;
        if (presentation == Presentation.OVERVIEW) {
            if (contains(expandAction, x, y)) return Part.EXPAND_ACTION;
            return contains(overviewList, x, y) ? Part.OVERVIEW_LIST : Part.EMPTY_CHROME;
        }
        if (contains(detailHeader, x, y)) return Part.COLLAPSE_ACTION;
        if (contains(detailList, x, y)) return Part.DETAIL_LIST;
        // The rest of an open folder's pane is the folder's, as the view resolves it
        // (AppDrawerCategoryView#touchPart): a pull there collapses the folder.
        return Part.DETAIL_EMPTY;
    }

    public static boolean isContentOwned(@NonNull Part part) {
        switch (part) {
            case OVERVIEW_LIST:
            case EXPAND_ACTION:
            case DETAIL_LIST:
            case COLLAPSE_ACTION:
            case DETAIL_EMPTY:
            case TRANSITION_BODY:
                return true;
            default:
                return false;
        }
    }

    private static boolean contains(@Nullable Frame frame, float x, float y) {
        return frame != null && x >= frame.left && x < frame.right && y >= frame.top && y < frame.bottom;
    }
}
