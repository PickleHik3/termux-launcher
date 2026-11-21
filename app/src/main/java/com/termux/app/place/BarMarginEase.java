package com.termux.app.place;

/**
 * How a still status bar draws a Margin it eases to across a crossing
 * ({@link SeamCrossing.Answers#barMarginShare}) without being laid out again: its glass layers
 * stretch to the eased width about the bar's centre, its outline grows or shrinks by the same
 * amount on each side, and its content keeps its size while each end's cluster moves with its
 * edge. Pure, so the activity only applies the numbers.
 */
public final class BarMarginEase {

    private BarMarginEase() {}

    /**
     * How far each side of the bar stands out past where it is laid out, in px: positive while
     * the eased inset is smaller than the laid-out one (the bar is drawn wider), negative while
     * it is larger.
     */
    public static float growPx(float laidInsetPx, float easedInsetPx) {
        return laidInsetPx - easedInsetPx;
    }

    /**
     * The horizontal scale a glass layer is drawn at, about the bar's centre, so its side
     * {@code halfWidthPx} from that centre stands {@code growPx} further out; 1 for a layer with
     * no width. Never below a hair, so a layer is never mirrored.
     */
    public static float layerScale(float halfWidthPx, float growPx) {
        if (!(halfWidthPx > 0f)) return 1f;
        return Math.max(0.01f, (halfWidthPx + growPx) / halfWidthPx);
    }

    /**
     * Where one piece of the bar's content is drawn across, as an x offset: a piece nearer the
     * start edge moves out with that edge ({@code -growPx}), one nearer the end edge with that one
     * ({@code +growPx}), and one centred stays put.
     *
     * @param left  the piece's left in its row, px
     * @param right the piece's right in its row, px
     * @param rowWidth the row's width, px
     */
    public static float contentShiftPx(int left, int right, int rowWidth, float growPx) {
        int startGap = left;
        int endGap = rowWidth - right;
        if (startGap < endGap) return -growPx;
        if (startGap > endGap) return growPx;
        return 0f;
    }
}
