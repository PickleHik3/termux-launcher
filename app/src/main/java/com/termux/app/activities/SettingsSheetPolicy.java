package com.termux.app.activities;

/**
 * How tall and how wide Settings' sheet over Home is. Pure: window pixels and density in, pixels
 * out, so the rules are tested without a window.
 *
 * <ul>
 *   <li>It opens resting at {@link #RESTING_FRACTION} of the window, never less than
 *       {@link #MIN_RESTING_DP} (a phone on its side gets a usable page, which there is nearly the
 *       whole window), never more than its full height.</li>
 *   <li>Full height is the window below the status bar. Once raised there (dragged up, or the
 *       keyboard came up over it) it keeps that height until it is closed: the resting stop is
 *       gone, so a drag down either springs back to full height or, past the closing threshold,
 *       closes it.</li>
 *   <li>The closing threshold is the sheet behaviour's own: released lower than half its current
 *       height below where it stands, or flung down.</li>
 *   <li>On a wide window it is capped and centred: {@link #MAX_WIDTH_DP} for one page,
 *       {@link #MAX_WIDTH_TWO_PANE_DP} where the list stands beside the page.</li>
 * </ul>
 */
public final class SettingsSheetPolicy {

    /** The share of the window a freshly opened sheet covers. */
    static final float RESTING_FRACTION = 0.6f;
    /** The least a resting sheet covers, before it is capped at full height. */
    static final int MIN_RESTING_DP = 400;
    /** The widest a one-page sheet grows, Material's bottom-sheet width. */
    static final int MAX_WIDTH_DP = 640;
    /** The widest a sheet with the list beside the page grows. */
    static final int MAX_WIDTH_TWO_PANE_DP = 840;

    private SettingsSheetPolicy() {}

    /** The sheet at full height: the whole window below the status bar. */
    public static int fullHeight(int windowHeight, int topInset) {
        return Math.max(0, windowHeight - Math.max(0, topInset));
    }

    /** The sheet resting where it opens. */
    public static int restingHeight(int windowHeight, int topInset, float density) {
        int full = fullHeight(windowHeight, topInset);
        int rest = Math.max(Math.round(windowHeight * RESTING_FRACTION), Math.round(MIN_RESTING_DP * density));
        return Math.min(rest, full);
    }

    /**
     * The height the sheet settles to when it is not closed: resting until it has been raised,
     * then full height for good.
     */
    public static int settledHeight(int windowHeight, int topInset, float density, boolean raised) {
        return raised ? fullHeight(windowHeight, topInset) : restingHeight(windowHeight, topInset, density);
    }

    /** The widest the sheet may be in a window {@code windowWidth} wide. */
    public static int maxWidth(int windowWidth, float density, boolean twoPane) {
        int cap = Math.round((twoPane ? MAX_WIDTH_TWO_PANE_DP : MAX_WIDTH_DP) * density);
        return Math.max(0, Math.min(windowWidth, cap));
    }
}
