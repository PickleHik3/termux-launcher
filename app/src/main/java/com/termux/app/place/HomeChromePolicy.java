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
     * Whether the status bar's content shows while the wall rests on the page. On a Home page the
     * Home layout has no status bar, so a slide toward Home fades the window's bar out on the way
     * and a slide toward a window brings it with the window.
     */
    public static boolean statusShownAtRest(@NonNull PaneWallPage page) {
        return !isHome(page);
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
