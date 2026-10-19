package com.termux.app.launcher.drawer;

/**
 * Where a reopened drawer's list starts. Off, it starts at the top as it always did; on, it comes
 * back to where the last open left it, if the list still reaches that far.
 */
public final class AppDrawerReopenPolicy {

    private AppDrawerReopenPolicy() {}

    /**
     * @param saved     the first visible position (or page) when the drawer last closed on its full
     *                  list; 0 when it closed on a search, whose order says nothing about the list
     * @param itemCount how many positions (or pages) the list has now
     * @return the position to start at, always inside the list
     */
    public static int startPosition(boolean remember, int saved, int itemCount) {
        if (!remember || itemCount <= 0 || saved <= 0) return 0;
        return Math.min(saved, itemCount - 1);
    }
}
