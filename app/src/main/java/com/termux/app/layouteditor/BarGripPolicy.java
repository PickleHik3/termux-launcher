package com.termux.app.layouteditor;

import com.termux.view.TerminalView;

/**
 * The grip on a bar's inner edge (#340), pure: how far it was pulled becomes the stored height
 * scale, and the readout's numbers come from it. The scale is the one the Height row writes, so
 * the grip is a second hand on the same value, not a second value.
 */
public final class BarGripPolicy {

    /** How near the default a pull snaps to it, in scale (a few percent of the bar's height). */
    static final float SNAP_BAND = 0.03f;

    private BarGripPolicy() {}

    /**
     * The scale after the grip was pulled {@code growthPx} outward (positive grows the bar) from a
     * bar {@code startHeightPx} tall at {@code startScale}: the scale follows the bar's own
     * height, clamped to {@code [min, max]}, and lands exactly on {@code defaultScale} inside
     * {@link #SNAP_BAND} of it. An unmeasured bar stays where it was.
     */
    public static float scaleFor(float startScale, float startHeightPx, float growthPx,
                                 float defaultScale, float min, float max) {
        if (!(startHeightPx > 0f) || Float.isNaN(growthPx))
            return startScale;
        float raw = startScale * (startHeightPx + growthPx) / startHeightPx;
        float clamped = Math.max(min, Math.min(max, raw));
        return Math.abs(clamped - defaultScale) <= SNAP_BAND ? defaultScale : clamped;
    }

    /** The size the readout names: the percent the Height row shows for this scale. */
    public static int percent(float scale) {
        return Math.round(scale * 100f);
    }

    /**
     * The readout's row count: the rows the terminal view holds once the keyboard has grown
     * {@code keyboardDeltaPx} (negative when it shrank) and the view is that much shorter (taller).
     * It is {@link TerminalView#rowsFor}, the expression {@code updateSize} sizes the session with,
     * so the readout and the terminal cannot drift. The view's height, text inset and the
     * renderer's two spacings are the caller's. This is the one call #262 swaps for its own
     * source. Zero for an unmeasured terminal.
     */
    public static int terminalRows(int viewHeightPx, int keyboardDeltaPx, int topInsetPx,
                                   int spacingAndAscentPx, int lineSpacingPx) {
        if (viewHeightPx <= 0 || lineSpacingPx <= 0)
            return 0;
        return TerminalView.rowsFor(Math.max(0, viewHeightPx - keyboardDeltaPx), topInsetPx,
            spacingAndAscentPx, lineSpacingPx);
    }
}
