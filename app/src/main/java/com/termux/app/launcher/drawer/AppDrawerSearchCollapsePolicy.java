package com.termux.app.launcher.drawer;

/**
 * When the drawer's search bar is the full bar and when it is the round button in the card's
 * corner, pure. The bar folds away once the user browses: a list they drag has scrolled down a full
 * row of its grid within that drag and the fling it throws ({@link #browsedARow}), or a page strip
 * or the A-Z index is dragged. It unfolds when the list is back at its top, when the button is
 * tapped, when a query is typed, when edit mode starts and whenever the drawer opens. A query
 * showing, or edit mode, always keeps the full bar.
 */
final class AppDrawerSearchCollapsePolicy {

    /** What just happened to the drawer. */
    enum Event {
        /**
         * The user is browsing: a dragged list scrolled a full row ({@link #browsedARow}), or a
         * page strip or the A-Z index was dragged.
         */
        SCROLL_STARTED,
        /** A list came back to its top, or the first page. */
        SCROLLED_TO_TOP,
        /** The round button was tapped. */
        BUTTON_TAPPED,
        /** The query changed. */
        QUERY_CHANGED,
        /** Edit mode started. */
        EDIT_STARTED,
        /** The drawer was opened. */
        DRAWER_OPENED
    }

    private AppDrawerSearchCollapsePolicy() {}

    /**
     * Whether a list drag has browsed far enough to fold the bar: the list has scrolled down
     * {@code scrolledDownPx} since the drag started (net, up counted back) and that reaches one full
     * row of the grid on screen, {@code rowHeightPx}. A list with no row laid out folds on any
     * scroll down.
     */
    static boolean browsedARow(int scrolledDownPx, int rowHeightPx) {
        return scrolledDownPx >= Math.max(1, rowHeightPx);
    }

    /**
     * Whether the bar is the round button after {@code event}. {@code scrollable} is whether the
     * list that was dragged has anywhere to go; a list that fits never folds the bar, because
     * nothing would bring it back.
     */
    static boolean collapsedAfter(boolean collapsed, Event event, boolean scrollable,
                                  boolean hasQuery, boolean editing) {
        if (hasQuery || editing) return false;
        if (event == Event.SCROLL_STARTED) return collapsed || scrollable;
        return false;
    }
}
