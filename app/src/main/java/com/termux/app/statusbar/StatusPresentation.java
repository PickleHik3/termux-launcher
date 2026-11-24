package com.termux.app.statusbar;

import androidx.annotation.NonNull;

import com.termux.app.page.PageKind;

/**
 * How the one status host presents itself under the place the user chose ({@link StatusPlace}):
 * as the strip, or as the thin row laid over the top pane ({@link PaneRowPolicy}).
 *
 * <p>Home has no pane, so Home wears the strip. A slide that can show a Home page wears it too,
 * from the frame the wall leaves its rest until the page it lands on is committed: the strip then
 * stands where Home's page expects it for the whole slide, and the row comes back once the wall
 * rests on a page with a pane. A slide between two windows keeps the row. Neither swap moves a
 * page: each page's top is its own whichever way the host presents ({@code PageTopInsets}), so
 * the swap is chrome only, and it is decided by the same rest the canvas's reach reads, once per
 * change, never per frame.
 */
public enum StatusPresentation {
    STRIP,
    ROW;

    /**
     * @param rowStands  whether the row stands on the Terminal's pages at all
     *                   ({@link PaneRowPolicy#rowStands}): the place is the pane and the bar is
     *                   shown along the top
     * @param committed  the page the chrome is committed to
     * @param wallAtRest whether the wall rests on the committed page
     * @param homeInView whether a Home page can be on screen while the wall moves: the committed
     *                   page is Home, or the slide's other end is
     */
    @NonNull
    public static StatusPresentation of(boolean rowStands, @NonNull PageKind committed,
                                        boolean wallAtRest, boolean homeInView) {
        if (!rowStands || committed == PageKind.HOME) return STRIP;
        if (!wallAtRest && homeInView) return STRIP;
        return ROW;
    }

    public boolean isRow() {
        return this == ROW;
    }
}
