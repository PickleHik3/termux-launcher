package com.termux.app.chrome;

/**
 * The look the window wears, as one number, read at every place a theme change can reach a live
 * activity (a configuration change, AppCompat's night-mode callback, a styling reload, a resume).
 * When the number moved since the last read, the activity drops its overlays with {@link
 * OverlayRegistry.CloseReason#THEME}: a surface built in the old look is closed cleanly (layers
 * under it restored, keyboard released) rather than left attached and focused over a page that
 * was restyled under it.
 *
 * <p>The system's own day or night is part of the number as well as the one the window wears: a
 * launcher pinned to one mode still sees its window re-laid by a system flip it does not follow.
 */
public final class ThemeFlip {

    /** Nothing read yet: the first read only records. */
    static final int UNREAD = -1;

    static final int WINDOW_NIGHT = 1;
    static final int SYSTEM_NIGHT = 1 << 1;
    static final int SCHEME_CHROME = 1 << 2;
    static final int WALLPAPER_THEME = 1 << 3;

    private int mLast = UNREAD;

    /** The number for a look; never {@link #UNREAD}. */
    public static int signature(boolean windowNight, boolean systemNight, boolean schemeChrome,
                                boolean wallpaperTheme) {
        return (windowNight ? WINDOW_NIGHT : 0)
            | (systemNight ? SYSTEM_NIGHT : 0)
            | (schemeChrome ? SCHEME_CHROME : 0)
            | (wallpaperTheme ? WALLPAPER_THEME : 0);
    }

    /**
     * Records {@code signature} and says whether it differs from the one read last. The first
     * read says false: there is no earlier look for an overlay to have been built in.
     */
    public boolean moved(int signature) {
        int last = mLast;
        mLast = signature;
        return last != UNREAD && last != signature;
    }
}
