package com.termux.app.terminal.extrakeys;

import com.termux.app.terminal.AccessoryStackLayoutPolicy;

/**
 * The bar's height as the front page changes. A key page is as tall as its rows; the text input
 * after the last key page is one row.
 *
 * <p>The height change is one layout per flip. Growing commits the layout first (the bar is laid out
 * at its new height with the extra part hidden) and then reveals it; shrinking conceals first and
 * commits the layout at the end.
 */
public final class ExtraKeysBarHeight {

    public static final int HEIGHT_MS = 160;

    public enum Step { NONE, GROW, SHRINK }

    private ExtraKeysBarHeight() {}

    public static int pageHeightPx(int baseRowPx, int rows, float scale) {
        return AccessoryStackLayoutPolicy.computeTerminalToolbarHeightPx(baseRowPx, rows, scale);
    }

    public static int rowsOf(int[] keyPageRows, int page) {
        if (keyPageRows == null || page < 0) return 0;
        if (page < keyPageRows.length) return Math.max(0, keyPageRows[page]);
        return page == keyPageRows.length ? 1 : 0;
    }

    public static Step step(int fromPx, int toPx) {
        if (toPx > fromPx) return Step.GROW;
        return toPx < fromPx ? Step.SHRINK : Step.NONE;
    }

    /** How much of the bar's content is hidden at fraction t (0..1) of {@link #HEIGHT_MS}. */
    public static int concealedPx(Step step, int fromPx, int toPx, float t) {
        float f = t < 0f ? 0f : Math.min(t, 1f);
        if (step == Step.GROW) return Math.round((toPx - fromPx) * (1f - f));
        if (step == Step.SHRINK) return Math.round((fromPx - toPx) * f);
        return 0;
    }
}
