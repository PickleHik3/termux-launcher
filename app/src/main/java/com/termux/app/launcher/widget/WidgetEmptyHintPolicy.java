package com.termux.app.launcher.widget;

/**
 * Whether an empty Home page's hint ("Long-press to add widgets") shows. What lies over Home is
 * see-through: a Home float's terminal has no body of its own, and the app drawer and the command
 * palette are tinted glass. A hint left drawn under any of them reads through it as if it were
 * the overlay's own text, so it steps out while one is up and comes back when Home is bare again.
 */
public final class WidgetEmptyHintPolicy {

    private WidgetEmptyHintPolicy() {}

    /**
     * @param floatOnPage a Home float shows on the page in view (minimised ones are chips in the
     *     bar and do not count)
     * @param drawerUp the app drawer's plane is engaged: open, or on its way in or out
     * @param paletteUp the command palette is open
     */
    public static boolean shown(boolean floatOnPage, boolean drawerUp, boolean paletteUp) {
        return !floatOnPage && !drawerUp && !paletteUp;
    }
}
