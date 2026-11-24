package com.termux.app.layouteditor;

import com.termux.app.place.PlaceKeyboard;

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
     * The readout's row count: the terminal's {@code startRows} less the whole rows
     * {@code keyboardDeltaPx} of added keyboard covers (more when the keyboard shrank), counted by
     * the policy the keyboard uses ({@link PlaceKeyboard#keyboardRows}). This is the one call
     * #262 swaps for its own source; the readout reads nothing else for rows. Zero for an
     * unmeasured terminal.
     */
    public static int terminalRows(int startRows, int paneHeightPx, int keyboardDeltaPx) {
        if (startRows <= 0)
            return 0;
        if (paneHeightPx <= 0 || keyboardDeltaPx == 0)
            return startRows;
        int covered = PlaceKeyboard.keyboardRows(Math.abs(keyboardDeltaPx), paneHeightPx,
            startRows);
        int rows = keyboardDeltaPx > 0 ? startRows - covered : startRows + covered;
        return Math.max(1, rows);
    }
}
