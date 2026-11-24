package com.termux.app.terminal;

/**
 * Which pane of a window holds the status bar's thin row: the one on screen at the top-left. Pure.
 *
 * <p>A split lays its first child out first: on top when stacked, and at the start when side by
 * side, which is the left in a left-to-right layout and the right in a right-to-left one (a
 * horizontal LinearLayout mirrors under RTL). So the walk down the tree takes the first child of
 * every split but a side-by-side one in RTL, where the left pane is the second.
 */
public final class StatusRowPane {

    private StatusRowPane() {}

    /**
     * Whether the top-left pane lies under a split's first child.
     *
     * @param sideBySide whether the split lays its children side by side (horizontal)
     * @param rtl        whether the window lays out right to left
     */
    public static boolean takesFirst(boolean sideBySide, boolean rtl) {
        return !(sideBySide && rtl);
    }
}
