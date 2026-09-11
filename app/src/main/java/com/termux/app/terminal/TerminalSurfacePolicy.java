package com.termux.app.terminal;

/**
 * Whether the terminal paints its own surface — the "terminal opacity" tint — over what is behind
 * it. Pure, so the rule can be tested without an activity.
 *
 * <p>In opaque mode the surface is always there: it is the terminal's background. In wallpaper mode
 * it is the tint the opacity slider asks for, painted on the window root as one uniform dim, and it
 * goes away only when the slider is at zero.
 *
 * <p>Fullscreen has no say. It used to switch the surface off, back when the tint was a bounded
 * overlay view that fought the hidden system bars; with the tint on the root that guard only ever
 * did one thing — strip the terminal's darkness the moment fullscreen came on in wallpaper mode
 * (issue #27).
 */
public final class TerminalSurfacePolicy {

    private TerminalSurfacePolicy() {}

    /**
     * @param wallpaperMode whether the system wallpaper shows through the terminal
     * @param terminalOpacity the terminal opacity slider, 0–100
     */
    public static boolean showsTerminalSurface(boolean wallpaperMode, int terminalOpacity) {
        if (!wallpaperMode) return true;
        return terminalOpacity > 0;
    }
}
