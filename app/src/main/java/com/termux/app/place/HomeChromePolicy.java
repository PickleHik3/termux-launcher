package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.PaneWallPage;

/**
 * What a page of the wall wears, by the kind of page it is (ADR 0008). A <b>Home page</b> wears
 * only the dock's rows (the pinned apps and the A–Z index) and the keyboard, both as overlays that
 * take no room from the widget grid, arranged by the Home layout ({@link LayoutVariant#HOME});
 * no borders, no launcher status bar, no extra keys, shaped by Home's own Style, Corners and
 * Margin. A <b>window</b> wears the Workspace's whole chrome, arranged by the Workspace layout (the
 * normal one, or the minimal one while minimal mode is on) and shaped by the Workspace's Style,
 * Corners and Margin.
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

    /**
     * Whether the page wears Home's own Style, Corners and Margin rather than the Workspace's. A
     * Home page does, whatever minimal mode says; a window wears the Workspace's. Home's are
     * seeded from the Workspace's (they read them while unset), so the two differ only once the
     * user has set Home apart. The chrome on screen switches at settle, with the layout variant.
     */
    public static boolean wearsHomeShape(@NonNull PaneWallPage page) {
        return isHome(page);
    }

    /** The Style, Corners and Margin the page wears, of the two halves' {@code workspace} and {@code home}. */
    @NonNull
    public static WallHalfShape shape(@NonNull PaneWallPage page, @NonNull WallHalfShape workspace,
                                      @NonNull WallHalfShape home) {
        return wearsHomeShape(page) ? home : workspace;
    }

    /**
     * Whether the wall settling on {@code to} from {@code from} changes what the chrome wears, so
     * it has to be dressed again as a Style change dresses it. Only a crossing between the halves
     * can, and only while their values differ.
     */
    public static boolean shapeChanges(@NonNull PaneWallPage from, @NonNull PaneWallPage to,
                                       @NonNull WallHalfShape workspace,
                                       @NonNull WallHalfShape home) {
        return !shape(from, workspace, home).equals(shape(to, workspace, home));
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
     * Whether the status bar floats over the page's content rather than taking room from it. A
     * Home page wears no status bar of any kind (2026-10-19), so this only says what a bar there
     * would do: overlay the page as the dock does, leaving the widget grid its whole height. On a
     * window the bar takes its band as always ({@link EdgeStackPolicy#topOverlapPx}).
     */
    public static boolean statusOverlays(@NonNull PaneWallPage page) {
        return isHome(page);
    }

    /**
     * Whether the status bar's content shows while the wall rests on the page. On a Home page it
     * never does: Home wears no status bar, so a slide toward Home fades the window's bar out on
     * the way and a slide toward a window brings it with the window.
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
     * The place a Layout page the Appearance surface brings back (where the person last left it)
     * opens on, with the wall resting on {@code onScreen}: the remembered place only while it is on
     * the same half, else null, the place on screen. Opening it moves the wall there and the
     * editor edits that half's Style, Corners and Margin, so a Layout page left over Home and
     * brought back from a window would carry the wall off to Home and edit Home's (2026-10-18:
     * "Margin · Home" from a window's palette). Between two windows the remembered one stands,
     * as it always has.
     */
    @Nullable
    public static PaneWallPage restoredEditorPlace(@Nullable PaneWallPage remembered,
                                                   @NonNull PaneWallPage onScreen) {
        if (remembered == null || wearsHomeShape(remembered) != wearsHomeShape(onScreen))
            return null;
        return remembered;
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
