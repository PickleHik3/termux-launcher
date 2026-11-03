package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.wall.PaneWallPage;

/**
 * Whether the keyboard comes up on a page of the wall, which is not dress (the glossary's
 * <b>Dress</b>): each half keeps its own rule. A window remembers whether its keyboard was up,
 * and minimal mode puts it away ({@link MinimalMode#keyboardOnEnter}). A Home page remembers
 * nothing: the keyboard is up there only while <b>always on</b>, the Home layout's keyboard
 * element shown, which also gives it rows of the widget grid.
 *
 * <p>Pure, so the answers are read and tested without a window.
 */
public final class PlaceKeyboard {

    private PlaceKeyboard() {}

    /**
     * Whether the keyboard comes up as the wall lands on the page. A Home page remembers nothing:
     * the keyboard is up there only while the Home layout's keyboard element is shown, which is
     * always on. A window comes back as it was left, unless minimal mode puts it away.
     */
    public static boolean keyboardOnEnter(@NonNull PaneWallPage page, boolean remembered,
                                          boolean minimal, boolean alwaysOn) {
        if (page == PaneWallPage.WIDGETS) return alwaysOn;
        return MinimalMode.keyboardOnEnter(remembered, minimal);
    }

    /**
     * Whether a tap on a Home terminal widget's terminal takes the keyboard back from a stale
     * "external text field has the keyboard" flag. A widget's terminal is the typing target once
     * tapped; with the flag left set (by a sheet or field that never reported its end) the in-app
     * keyboard keeps its container hidden on Home however often it is asked to show. A tap in a
     * pane of the Workspace keeps the flag, as before.
     */
    public static boolean tapReclaimsFromExternalInput(boolean externalInputActive,
                                                       boolean homeTerminalWidgetFocused) {
        return externalInputActive && homeTerminalWidgetFocused;
    }

    /**
     * Whether the end of an editor session takes down the keyboard the editor raised for itself.
     * On Home with always on it stays: always on wants it up there, and turned on while the editor
     * was open, the arrangement pass found it already up (the editor's) and left it, so taking it
     * down at Done would leave Home without the keyboard until the next launch. Anywhere else, and
     * on Home without always on, the editor's keyboard goes as it came.
     */
    public static boolean editorEndKeepsKeyboard(@NonNull PaneWallPage page, boolean alwaysOn) {
        return page == PaneWallPage.WIDGETS && keyboardOnEnter(page, false, false, alwaysOn);
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
