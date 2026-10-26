package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

/**
 * The circle behind today's number: centred on the number's drawn ink and sized to contain it,
 * then held inside its cell's column and its row's pitch so it never touches a neighbour. Pure,
 * in whatever unit the caller measures in.
 */
final class CalendarTodayDisc {
    /** Breathing room around the ink, as a share of the ink's height. */
    static final float PAD_OF_INK_HEIGHT = 0.3f;

    private CalendarTodayDisc() {}

    /**
     * Writes {@code out = {centreX, centreY, radius}}. The ink is {@code inkW} by {@code inkH}
     * centred on ({@code inkCx}, {@code inkCy}). The disc is the circle round that rectangle plus
     * padding, no wider than {@code cellW - gap} nor taller than {@code pitch - gap}. Its centre
     * follows the ink, moving only as far as keeping the disc inside the cell's columns
     * ({@code cellLeft}..{@code cellLeft + cellW}) requires.
     */
    static void fit(float inkCx, float inkCy, float inkW, float inkH, float cellLeft,
                    float cellW, float pitch, float gap, @NonNull float[] out) {
        float pad = PAD_OF_INK_HEIGHT * inkH;
        float wanted = (float) Math.hypot(inkW, inkH) + 2f * pad;
        float limit = Math.max(0f, Math.min(cellW - gap, pitch - gap));
        float half = Math.min(wanted, limit) / 2f;
        float lo = cellLeft + half;
        float hi = cellLeft + cellW - half;
        out[0] = lo <= hi ? Math.max(lo, Math.min(hi, inkCx)) : inkCx;
        out[1] = inkCy;
        out[2] = half;
    }
}
