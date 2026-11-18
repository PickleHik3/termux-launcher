package com.termux.app.wall;

/**
 * The keyboard swipe off the page's bottom border: what the wall asks the keyboard, and the
 * finger's progress it hands over. {@link #NONE} is a keyboard that follows nothing. Main thread
 * only.
 */
public interface WallKeyboardSwipe {

    /**
     * Whether the keyboard swipe is armed on the page in view. Always on the Workspace; on a
     * Home page it is the palette gesture, which Settings can turn off.
     */
    default boolean isEnabled() { return true; }

    /**
     * A vertical swipe off the page's bottom border asked for the keyboard: {@code open} up,
     * closed down. On every place and in every mode, and it turns a keyboard switched off
     * back on, as the keyboard key does: it is the one way back that is always there. Only
     * for a swipe the keyboard could not follow ({@link #onRevealBegin}).
     */
    default void onSwipe(boolean open) { }

    /**
     * The keyboard swipe was claimed, going up ({@code opening}) or down, and the keyboard is
     * to follow the finger: the keyboard's height in px the finger drives it over, or 0 where
     * it cannot follow, and the swipe then asks through {@link #onSwipe} on
     * release. See {@link PaneWallLayout.Listener#onKeyboardRevealBegin}.
     */
    default int onRevealBegin(boolean opening) { return 0; }

    /** How much of the keyboard a swipe it follows shows this frame, 0 down to 1 up. */
    default void onRevealProgress(float reveal) { }

    /** The keyboard a swipe carried came to rest, up ({@code open}) or down. */
    default void onRevealEnd(boolean open) { }

    /** A keyboard swipe that does nothing, for a wall whose host has none. */
    WallKeyboardSwipe NONE = new WallKeyboardSwipe() { };
}
