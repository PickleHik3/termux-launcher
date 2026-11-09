package com.termux.app.terminal;

import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeInk;

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

    /**
     * One wall page's own ground (page-chrome spec C): its wallpaper dim, and on the Terminal page
     * only, {@link #wallGround}'s rule for the canvas tint (folded in without glass, left on the
     * slab with it). Any other page is its dim alone, so the terminal's tint never shows on Home.
     *
     * @param terminalPage whether the page is the Terminal page (or a window of it)
     * @param glassPane whether every pane is a glass slab
     * @param terminalSurfaceColor the canvas tint, ARGB (ignored off the Terminal page)
     * @param wallpaperDim the page's own wallpaper dim, ARGB
     */
    public static int pageGround(boolean terminalPage, boolean glassPane, int terminalSurfaceColor,
                                 int wallpaperDim) {
        return terminalPage ? wallGround(glassPane, terminalSurfaceColor, wallpaperDim) : wallpaperDim;
    }

    /**
     * The terminal opacity below which the pane is more wallpaper than glass, and the theme's dark
     * text cannot be trusted to read on it. A light pane needs about 46% of its tint over a black
     * wallpaper before black text reaches 4.5:1 (WCAG AA) on it; a Material surface is a little
     * darker than white, so the line sits at 50.
     */
    public static final int PALE_INK_BELOW_OPACITY = 50;

    /**
     * The ink the terminal's default text leans to before its ground is weighed: the glass's, not
     * the theme's. On a pane thinner than {@link #PALE_INK_BELOW_OPACITY} it is pale ink on dark,
     * whatever the theme and whatever the terminal wore before, so a light theme over a dark or
     * busy wallpaper no longer holds dark text on it. Thicker glass, and a terminal on its own
     * opaque background, keep {@code held}.
     *
     * <p>It is a leaning, not the verdict: the measured ground still turns it over where the other
     * ink reads clearly better, so a bright wallpaper behind clear glass keeps dark text.
     *
     * @param onGlass whether the text stands on glass (wallpaper mode), rather than on the
     *     palette's own opaque background
     * @param terminalOpacity the resolved terminal opacity, 0–100
     * @param held the ink the terminal wears now, or null for the theme's own
     */
    @Nullable
    public static ChromeInk.Polarity inkLeaning(boolean onGlass, int terminalOpacity,
                                                @Nullable ChromeInk.Polarity held) {
        if (onGlass && terminalOpacity < PALE_INK_BELOW_OPACITY)
            return ChromeInk.Polarity.PALE_INK;
        return held;
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
