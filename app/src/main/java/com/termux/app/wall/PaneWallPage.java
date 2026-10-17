package com.termux.app.wall;

/**
 * The kinds of page on the wall's row, in the order they stand (ADR 0008). Each is one view on
 * the wall: {@link #WIDGETS} carries every Home page and {@link #TERMINAL} every window of the
 * current session, each paging inside itself ({@link WallRow}); the wall slides between them.
 * The names are kept for {@code wall.go} and {@code launcherctl}.
 */
public enum PaneWallPage {
    /** Home: the widget grid, whose pages are the Home pages. The home press lands here. */
    WIDGETS,
    /** The Workspace's windows: the current session's, one page each. */
    TERMINAL,
    /** The display window: the embedded X11 display, last on the row while a display is up. */
    DISPLAY;

    /** The name {@code wall.go} and {@code launcherctl} use. */
    public String toolName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
