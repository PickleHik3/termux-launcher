package com.termux.app.terminal;

/**
 * What the Home palette asks of the wall: the Home actions no registry tool runs, and what it
 * needs to know about Home floats. Every method defaults to doing nothing, so the palette builds,
 * runs and tests without the activity; the wall installs a real one with
 * {@link TerminalCommandPaletteController#setHomeHost}.
 */
public interface CommandPaletteHomeHost {

    /** Returned by {@link #homeFloatPage} when the host cannot say which page holds the float. */
    int UNKNOWN_PAGE = -1;

    /** The host that does nothing and knows of no float. */
    CommandPaletteHomeHost NONE = new CommandPaletteHomeHost() { };

    /** Opens a Home float on the Home page in view. */
    default void openTerminalHere() { }

    /** Slides the wall to the Workspace. */
    default void goToWorkspace() { }

    /** Minimises every Home float into its chip. */
    default void minimiseAllFloats() { }

    /** Restores every minimised Home float. */
    default void restoreFloats() { }

    /** Starts adding a widget to the Home page in view. */
    default void addWidget() { }

    /** Pages Home to the float's page and restores it there. */
    default void restoreFloat(long windowId) { }

    /** Whether the current session's window with this id is a Home float. */
    default boolean isHomeFloat(long windowId) {
        return false;
    }

    /**
     * The row place of the current session's terminal window {@code window} (its index in the
     * session's list), as {@code window.select} counts places: one more when the display window
     * stands before it in the row.
     */
    default int rowIndexOf(int window) {
        return window;
    }

    /** The zero-based Home page the float is pinned to, or {@link #UNKNOWN_PAGE}. */
    default int homeFloatPage(long windowId) {
        return UNKNOWN_PAGE;
    }

    /** Whether a Home page is in view. */
    default boolean isOnHome() {
        return false;
    }

    /** Whether the Home layout keeps the keyboard up (always on). */
    default boolean isKeyboardAlwaysOn() {
        return false;
    }

    /** Puts away the keyboard the Home palette raised, as its overlay closes. */
    default void lowerKeyboard() { }

    /** Whether the in-app keyboard stands up on the place in view. */
    default boolean isKeyboardUp() {
        return false;
    }

    /**
     * Raises the in-app keyboard for the Home palette to open on.
     *
     * @return false when there is no keyboard to raise; the palette then opens alone
     */
    default boolean raiseKeyboard() {
        return false;
    }
}
