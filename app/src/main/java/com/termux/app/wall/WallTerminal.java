package com.termux.app.wall;

/**
 * The Terminal page as the wall asks it: how many windows the current row holds and which is in
 * front, turning to one, the border drag's step between windows, and the empty page's first
 * shell. {@link #NONE} is a terminal with one window and nothing to start. Main thread only.
 */
public interface WallTerminal {

    /** How many terminal windows the current session's row holds. */
    default int count() { return 1; }

    /** Which of them is in front, counting from 0. */
    default int index() { return 0; }

    /** Turn to the {@code index}-th window at once. */
    default void show(int index) { }

    /**
     * A border drag committed to the window {@code steps} away and the wall still stands where
     * the finger let go: picture what is leaving. {@link #step} follows.
     */
    default void stepBegin(int steps) { }

    /**
     * Show the window {@code steps} away, the wall at rest again: it comes in from
     * {@code fromPx}, where the drag had already brought it.
     */
    default void step(int steps, float fromPx) { }

    /** A step on another page than the Terminal's: the picture of a leaving window is dropped. */
    default void dropDeparture() { }

    /**
     * The Terminal page goes back on the wall ({@link TerminalPageReturn#restore}); false when
     * it could not.
     */
    default boolean restore() { return false; }

    /**
     * Whether the empty Terminal page's first shell can be made now. Asked only while the
     * Terminal page has no window; without one the page still stands as a stop, showing its card.
     */
    default boolean canStartShell() { return false; }

    /**
     * The wall has landed on the empty Terminal page (ADR 0016 section 6), by any route: start
     * its first shell, as the Terminal entry does into an empty row. The window takes the page
     * when it arrives. False when no shell could be made; the page keeps its card.
     */
    default boolean startShell() { return false; }

    /** A terminal that holds one window and starts none, for a wall whose host has none. */
    WallTerminal NONE = new WallTerminal() { };
}
