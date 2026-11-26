package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

/**
 * How the one status host presents itself under the place the user chose ({@link StatusPlace}):
 * as the strip, or as the thin row laid over the top pane ({@link PaneRowPolicy}).
 *
 * <p>Home has no pane. A Home that shows the bar wears the strip, at rest and through any slide
 * that can show it, from the frame the wall leaves its rest until the page it lands on is
 * committed: the strip then stands where Home's page expects it for the whole slide, and the row
 * comes back once the wall rests on a page with a pane. A Home that shows no bar has nothing for
 * the strip to stand on: the host stays the row there, and through a slide the row rides in and
 * out with the pane it belongs to ({@link #rowPage}), with no swap at either end. A slide between
 * two windows keeps the row. No swap moves a page: each page's top is its own whichever way the
 * host presents ({@code PageTopInsets}), so the swap is chrome only, and it is decided by the same
 * rest the canvas's reach reads, once per change, never per frame.
 */
public enum StatusPresentation {
    STRIP,
    ROW;

    /**
     * @param rowStands    whether the row stands on the Terminal's pages at all
     *                     ({@link PaneRowPolicy#rowStands}): the place is the pane and the bar is
     *                     shown along the top
     * @param committed    the page the chrome is committed to
     * @param wallAtRest   whether the wall rests on the committed page
     * @param homeInView   whether a Home page can be on screen while the wall moves: the committed
     *                     page is Home, or the slide's other end is
     * @param homeShowsBar whether a Home page that can be on screen shows the status bar
     *                     ({@link #homeShowsBar}); read only where {@code homeInView}
     */
    @NonNull
    public static StatusPresentation of(boolean rowStands, @NonNull PageKind committed,
                                        boolean wallAtRest, boolean homeInView,
                                        boolean homeShowsBar) {
        if (!rowStands) return STRIP;
        // A Home with no bar of its own lends the strip nothing to stand on: the row stays.
        if (!homeShowsBar) return ROW;
        if (committed == PageKind.HOME) return STRIP;
        if (!wallAtRest && homeInView) return STRIP;
        return ROW;
    }

    /**
     * Whether a Home page shows the status bar: the bar is shown at all ({@code barShown}, not in
     * compatibility mode) and Home's layout does not put it away. A Home that stands it on any
     * edge keeps the strip, as it always did; only a Home with no bar lets the row ride.
     */
    public static boolean homeShowsBar(boolean barShown, boolean homeStatusHidden) {
        return barShown && !homeStatusHidden;
    }

    /**
     * The page whose pane the row lies over: the committed page where it has one, and on Home the
     * slide's other end (the page the row rides in with), else the Terminal, the page next to
     * Home, so a row at rest on Home already stands where the first frame of a slide wants it.
     */
    @NonNull
    public static PageKind rowPage(@NonNull PageKind committed, @Nullable PageKind otherEnd) {
        if (committed != PageKind.HOME) return committed;
        return otherEnd != null && otherEnd != PageKind.HOME ? otherEnd : PageKind.TERMINAL;
    }

    public boolean isRow() {
        return this == ROW;
    }
}
