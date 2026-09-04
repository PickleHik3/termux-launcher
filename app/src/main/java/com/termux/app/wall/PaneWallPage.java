package com.termux.app.wall;

/**
 * The fixed places on the pane wall, in the order they sit side by side. The terminal is the
 * middle one and the one a cold start always shows; the others are reached by swiping the status
 * bar, tapping a tile, or {@code wall.go}.
 */
public enum PaneWallPage {
    /** The app-widget grid. */
    WIDGETS,
    /** Sessions, windows and pane trees — the launcher's home screen. */
    TERMINAL,
    /** The embedded X11 display. */
    DISPLAY;

    /** The name {@code wall.go} and {@code launcherctl} use. */
    public String toolName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
