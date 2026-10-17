package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.wall.PaneWallPage;

/**
 * What a page of the wall wears, by the kind of page it is (ADR 0008). A <b>Home page</b> wears
 * only the dock's rows (the pinned apps and the A–Z index) and the keyboard, both as overlays that
 * take no room from the widget grid, arranged by the Home layout ({@link LayoutVariant#HOME});
 * no borders, no launcher status bar, no extra keys. A <b>window</b> wears the Workspace's whole
 * chrome, arranged by the Workspace layout: the normal one, or the minimal one while minimal mode
 * is on.
 *
 * <p>Every per-page chrome decision asks here, so what Home is spelled once rather than as a flag
 * per surface: the activity reads the variant, the page frame its border, the corner tab its
 * buttons, the geometry pass whether the dock floats, and the keyboard whether it comes up.
 *
 * <p>Pure, so the answers are read and tested without a window.
 */
public final class HomeChromePolicy {

    private HomeChromePolicy() {}

    /** Whether the page is a Home page. */
    public static boolean isHome(@NonNull PaneWallPage page) {
        return page == PaneWallPage.WIDGETS;
    }

    /**
     * The layout a page's chrome is arranged by: the Home layout on a Home page, whatever minimal
     * mode says, and the Workspace's on a window.
     */
    @NonNull
    public static LayoutVariant variant(@NonNull PaneWallPage page, boolean minimal) {
        return isHome(page) ? LayoutVariant.HOME : LayoutVariant.of(minimal);
    }

    /**
     * Whether minimal mode means anything on this page. Home has no minimal mode: the flag is the
     * Workspace's, kept untouched while Home shows, and the corner tab offers no door to it there.
     */
    public static boolean minimalModeApplies(@NonNull PaneWallPage page) {
        return !isHome(page);
    }

    /** Whether the page wears a frame: a window's border and slab. A Home page has none. */
    public static boolean bordersShown(@NonNull PaneWallPage page) {
        return !isHome(page);
    }

    /**
     * Whether the dock's rows float over the page's content rather than taking room from it. On a
     * Home page they do, keyboard and all, so the widget grid keeps its whole height; a bottom-row
     * dock rides up on the keyboard as it rises (unless it stands under it) and settles back.
     */
    public static boolean dockOverlays(@NonNull PaneWallPage page) {
        return isHome(page);
    }

    /**
     * Whether the status bar floats over the page's content rather than taking room from it. On a
     * Home page it does, as the dock does: the bar the first float brings down slides over the
     * widget grid, which keeps its size whether the bar is there or not, and the floats' room
     * starts at the bar's edge ({@link EdgeStackPolicy#topOverlapPx}). On a window the bar takes
     * its band as always.
     */
    public static boolean statusOverlays(@NonNull PaneWallPage page) {
        return isHome(page);
    }

    /**
     * Whether the status bar's content shows while the wall rests on the page. On a Home page the
     * Home layout has no status bar, so a slide toward Home fades the window's bar out on the way
     * and a slide toward a window brings it with the window.
     */
    public static boolean statusShownAtRest(@NonNull PaneWallPage page) {
        return statusShownAtRest(page, false);
    }

    /**
     * The same, once Home floats are counted: on a Home page the status bar is there exactly while
     * a Home float is open or minimised, in its compact form, and on a window always. The first
     * float brings it down from the top and the last one closing takes it away.
     */
    public static boolean statusShownAtRest(@NonNull PaneWallPage page, boolean homeFloatsExist) {
        return !isHome(page) || homeFloatsExist;
    }

    /**
     * Whether the status bar's window chips show on the page. On a window they are the row's
     * windows; on a Home page they are the Home floats, of every page, so they show exactly while
     * a Home float exists, open or minimised: the bar comes onto Home only to carry them.
     */
    public static boolean windowChipsShown(@NonNull PaneWallPage page, boolean homeFloatsExist) {
        return !isHome(page) || homeFloatsExist;
    }

    /**
     * Whether the status bar rests compact on the page whatever the orientation's memory says:
     * on a Home page it only ever comes in compact, to carry the floats' chips.
     */
    public static boolean statusForcedCompact(@NonNull PaneWallPage page) {
        return isHome(page);
    }

    /**
     * The Home layout with the status bar brought in while Home floats exist: on the edge it
     * stands on in the Workspace, so it reads as the same bar. Unchanged while there is no float,
     * or while the layout already shows one.
     */
    @NonNull
    public static PlaceLayout withFloatStatusBar(@NonNull PlaceLayout home,
                                                 @NonNull PlaceLayout.Edge edge,
                                                 boolean homeFloatsExist) {
        if (!homeFloatsExist || !home.slot(Element.STATUS).hidden) return home;
        return home.withSlot(Element.STATUS, Slot.on(edge, Element.STATUS));
    }

    /**
     * Whether the keyboard comes up as the wall lands on the page. A Home page remembers nothing:
     * the keyboard is up there only while the Home layout's keyboard element is shown, which is
     * always on. A window comes back as it was left, unless minimal mode puts it away.
     */
    public static boolean keyboardOnEnter(@NonNull PaneWallPage page, boolean remembered,
                                          boolean minimal, boolean alwaysOn) {
        if (isHome(page)) return alwaysOn;
        return MinimalMode.keyboardOnEnter(remembered, minimal);
    }

    /**
     * Whether the end of an editor session takes down the keyboard the editor raised for itself.
     * On Home with always on it stays: always on wants it up there, and turned on while the editor
     * was open, the arrangement pass found it already up (the editor's) and left it, so taking it
     * down at Done would leave Home without the keyboard until the next launch. Anywhere else, and
     * on Home without always on, the editor's keyboard goes as it came.
     */
    public static boolean editorEndKeepsKeyboard(@NonNull PaneWallPage page, boolean alwaysOn) {
        return isHome(page) && keyboardOnEnter(page, false, false, alwaysOn);
    }

    /**
     * How many of the grid's rows always on gives the keyboard: as many whole rows as the keyboard
     * covers, from a grid {@code gridHeightPx} tall cut into {@code gridRows} rows. Zero when the
     * grid or the keyboard is not measured yet, or when nothing would be left of the grid.
     */
    public static int keyboardRows(int keyboardPx, int gridHeightPx, int gridRows) {
        if (keyboardPx <= 0 || gridHeightPx <= 0 || gridRows <= 1) return 0;
        float rowPx = gridHeightPx / (float) gridRows;
        int rows = (int) Math.ceil(keyboardPx / rowPx - 1e-3f);
        return Math.max(0, Math.min(gridRows - 1, rows));
    }
}
