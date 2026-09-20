package com.termux.app.chrome;

/**
 * What the glass can blur, and how sure the launcher is that the picture it can read is the
 * picture on the screen.
 *
 * <p>Android only says whether a wallpaper <em>service</em> is running. Some ROMs (One UI, issue
 * #37) run one to show a plain still, so that alone decides nothing; a real live wallpaper, on the
 * other hand, leaves {@code WallpaperManager.getDrawable()} handing back either the still the user
 * once set or the built-in default. The three cases the launcher can actually tell apart:
 */
public enum WallpaperPicture {
    /** No service is running, or the wallpaper on screen is the one the launcher itself set. */
    MATCHES_SCREEN,
    /**
     * A service is running, but a still the user once set is readable. Blur it on every glass, and
     * let the editor say why the result may not match the screen.
     */
    BEST_EFFORT,
    /**
     * A service is running and no still exists: the readable picture is the ROM's default, one the
     * user never chose. Every glass wears its plain tint instead.
     */
    NO_STILL;

    /** Whether any glass should blur the readable picture at all. */
    public boolean blurs() {
        return this != NO_STILL;
    }

    /** Whether the surface editor should say the blur may not match, with the picker as the way out. */
    public boolean showsHint() {
        return this != MATCHES_SCREEN;
    }

    /** Whether the launcher may draw the wallpaper itself behind everything (the aligned-glass mode). */
    public boolean allowsSelfDrawnBackdrop() {
        return this == MATCHES_SCREEN;
    }
}
