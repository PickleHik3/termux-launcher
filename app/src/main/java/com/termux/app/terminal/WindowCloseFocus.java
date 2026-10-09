package com.termux.app.terminal;

/**
 * Which window a session shows after one of its windows closes — the pure half of
 * {@code TermuxActivity#onWindowEmptied}, kept out of the activity so the rule can be tested.
 *
 * <p>A window closes because its last pane finished (a command run in its own window exiting, an
 * agent's {@code pane.close}), and that window is not necessarily the one on screen: a window
 * opened with {@code --no-focus} runs behind the strip. The rule keeps the person looking at the
 * same window whenever that window survives, and moves to a neighbour only when the window they
 * were looking at is the one that went.
 */
public final class WindowCloseFocus {

    private WindowCloseFocus() {}

    /**
     * The index to select once the window at {@code removed} has been taken out of a strip whose
     * selection was {@code current}, leaving {@code remaining} windows; -1 when none remain and the
     * session itself is over.
     *
     * <ul>
     * <li>A window before the selection closing shifts the selection down by one, so it still
     * points at the same window.</li>
     * <li>The selected window closing hands over to its right-hand neighbour, or to the left one
     * when it was last in the strip.</li>
     * <li>A window after the selection closing changes nothing.</li>
     * </ul>
     */
    public static int afterRemoval(int current, int removed, int remaining) {
        if (remaining <= 0) return -1;
        if (removed < 0) return Math.min(Math.max(current, 0), remaining - 1);
        if (removed < current) return Math.min(current - 1, remaining - 1);
        return Math.min(Math.max(current, 0), remaining - 1);
    }
}
