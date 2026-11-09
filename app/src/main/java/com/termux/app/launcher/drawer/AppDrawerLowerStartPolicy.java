package com.termux.app.launcher.drawer;

/**
 * How far the drawer's list is lowered so its first row starts in the lower half of the screen,
 * within thumb reach. It applies to the vertical list on a portrait phone, and steps aside while a
 * search is typed or about to be, when the keyboard needs the room.
 */
public final class AppDrawerLowerStartPolicy {

    private AppDrawerLowerStartPolicy() {}

    /**
     * @param contentHeightPx the drawer content's height, 0 before it has been laid out
     * @param baseTopPx       where the list starts when not lowered
     * @return extra top margin for the list, 0 when it should stay where it is
     */
    public static int offsetPx(boolean enabled, boolean portrait, boolean searching,
                               int contentHeightPx, int baseTopPx) {
        if (!enabled || !portrait || searching || contentHeightPx <= 0) return 0;
        return Math.max(0, contentHeightPx / 2 - baseTopPx);
    }
}
