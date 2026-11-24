package com.termux.app.surfaces.rows;

/**
 * Whether two cells (two sliders, two steppers) stand side by side on one row or one under the
 * other, decided per row while it measures. They share the line only where each gets at least its
 * least width; a narrow card or a wide separator stacks them, so nothing is cut. Pure.
 */
public final class PairFit {

    private PairFit() {}

    /** The gap between two cells on one line. */
    public static final int GAP_DP = 16;
    /** The least a slider cell takes: a name, its value and a track worth dragging. */
    public static final int MIN_SLIDER_CELL_DP = 128;

    /**
     * The width each cell gets on one line: what is left of {@code innerPx} after the gap between
     * them (one on each side of a {@code separatorPx} wide mark, when there is one), shared
     * equally.
     */
    public static int cellPx(int innerPx, int gapPx, int separatorPx) {
        int gaps = separatorPx > 0 ? 2 : 1;
        return Math.max(0, (innerPx - gaps * gapPx - separatorPx) / 2);
    }

    /** Whether both cells hold {@code minCellPx} on one line. */
    public static boolean sideBySide(int innerPx, int gapPx, int separatorPx, int minCellPx) {
        return innerPx > 0 && cellPx(innerPx, gapPx, separatorPx) >= minCellPx;
    }
}
