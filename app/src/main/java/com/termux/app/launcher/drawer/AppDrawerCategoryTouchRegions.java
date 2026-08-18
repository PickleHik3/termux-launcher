package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

/** Half-open category hit testing from final laid-out bounds. */
public final class AppDrawerCategoryTouchRegions {
    public enum Part {
        OVERVIEW_LIST, EXPAND_ACTION, DETAIL_LIST, COLLAPSE_ACTION,
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
        return Part.EMPTY_CHROME;
    }

    public static boolean isContentOwned(@NonNull Part part) {
        switch (part) {
            case OVERVIEW_LIST:
            case EXPAND_ACTION:
            case DETAIL_LIST:
            case COLLAPSE_ACTION:
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
