package com.termux.app.wall;

/**
 * The kinds of page on the wall's row, in the order they stand (ADR 0008), and the places the
 * chrome is dressed for. The row has two: {@link #WIDGETS} carries every Home page and
 * {@link #TERMINAL} every window of the Workspace, the display window included, each paging
 * inside itself ({@link WallRow}); the wall slides between them. {@link #DISPLAY} is never a page
 * of the row: it names the place while the display window is the Workspace's window in front, for
 * what that window does differently (its keyboard form, its keys, its help).
 * The names are kept for {@code wall.go} and {@code launcherctl}.
 */
public enum PaneWallPage {
    /** Home: the widget grid, whose pages are the Home pages. The home press lands here. */
    WIDGETS,
    /** The Workspace's windows: the current session's, one page each. */
    TERMINAL,
    /**
     * Not a page of the row (amended 2026-10-19): the place while the display window — a window
     * of the Workspace, on {@link #TERMINAL}'s stretch (ADR 0008; since ADR 0015 A3b the Display pages) — is the one in front.
     */
    DISPLAY;

    /** The name {@code wall.go} and {@code launcherctl} use. */
    public String toolName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
