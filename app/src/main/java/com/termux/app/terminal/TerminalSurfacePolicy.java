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

    /**
     * The colour the window root is painted with in wallpaper mode: all the wallpaper dim when each
     * pane carries the terminal tint on a glass slab of its own (both Styles, the Docked insert
     * included), and the tint folded into the dim when the panes have no slab. It reads the
     * glass switch and two colours and nothing else, so no Style change can leave a dim behind
     * that a fresh start would not have painted.
     *
     * @param glassPane whether every pane is a glass slab
     * @param terminalSurfaceColor the terminal tint, ARGB
     * @param wallpaperDim the wallpaper's black dim, ARGB
     */
    public static int wallGround(boolean glassPane, int terminalSurfaceColor, int wallpaperDim) {
        return glassPane ? wallpaperDim : over(terminalSurfaceColor, wallpaperDim);
    }

    /** {@code top} laid over {@code bottom}, both ARGB, straight alpha. */
    static int over(int top, int bottom) {
        int ta = top >>> 24;
        int ba = bottom >>> 24;
        int a = ta + ba * (255 - ta) / 255;
        if (a == 0) return 0;
        int out = a << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int t = (top >> shift) & 0xFF;
            int b = (bottom >> shift) & 0xFF;
            int c = (t * ta * 255 + b * ba * (255 - ta)) / (a * 255);
            out |= Math.min(255, c) << shift;
        }
        return out;
    }
}
