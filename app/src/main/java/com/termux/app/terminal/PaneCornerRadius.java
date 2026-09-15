package com.termux.app.terminal;

/**
 * The one radius a pane's corners are drawn with.
 *
 * <p>A pane used to pick its shape three ways — the glass slab's radius with glass on, a hardcoded
 * 6dp for a float, a split or a maximised pane without it, and a straight edge for a lone docked
 * pane — so the number the user turns in the Appearance editor reached some panes and not others,
 * and the same window changed shape when it was split. There is one answer now: the terminal's own
 * corner radius, whatever the pane is doing.
 *
 * <p>Pure arithmetic, so what every mode wears can be asserted without a window to split.
 */
public final class PaneCornerRadius {

    private PaneCornerRadius() {}

    /**
     * The radius a pane is drawn at, in px.
     *
     * @param settingDp the terminal corner radius knob in dp. 0 is a square pane and is the user's
     *     answer, not the absence of one — the fallbacks that used to read it as "unset" are what
     *     made a pane at 0 still pay the arc clearance. Below 0 is the shared "follow the style"
     *     sentinel.
     * @param styleRadiusPx what the style rounds a pane by, which is what the sentinel follows.
     */
    public static float radiusPx(int settingDp, float styleRadiusPx, float density) {
        float radiusPx = settingDp >= 0 ? settingDp * density : styleRadiusPx;
        return Math.max(0f, radiusPx);
    }
}
