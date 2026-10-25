package com.termux.app.surfaces;

/**
 * Whether an editor row stands on one line or two, as one measured rule: a row goes on one line
 * when its label (single line) plus its control's minimum width fit the row's width; otherwise it
 * keeps the two-line layout, label over control. Pure arithmetic in pixels, so the views only
 * apply the answer and a test holds the rule.
 */
final class EditorRowFit {

    private EditorRowFit() {}

    /**
     * The rule: the label, the gap and the control's least width all fit {@code rowWidthPx}, the
     * width the label and the control share.
     */
    static boolean oneLine(int rowWidthPx, float labelPx, float controlMinPx, int gapPx) {
        return labelPx + gapPx + controlMinPx <= rowWidthPx;
    }

    /** What a segmented control needs at {@code scale} of its text size. */
    static float segmentsMinPx(float textSumPx, int count, int segmentPaddingPx, int segmentGapPx,
                               float scale) {
        return textSumPx * scale + count * 2f * segmentPaddingPx
            + Math.max(0, count - 1) * segmentGapPx;
    }

    /**
     * The largest share of its text size, from 1 down to {@code minScale} in steps of
     * {@code step}, at which a segmented control stands beside its label ({@link #oneLine}), or 0
     * when even the smallest does not.
     */
    static float segmentsScale(int rowWidthPx, float labelPx, int gapPx, float textSumPx,
                               int count, int segmentPaddingPx, int segmentGapPx,
                               float minScale, float step) {
        int steps = Math.round((1f - minScale) / step);
        for (int i = 0; i <= steps; i++) {
            float scale = Math.max(minScale, 1f - i * step);
            if (oneLine(rowWidthPx, labelPx,
                segmentsMinPx(textSumPx, count, segmentPaddingPx, segmentGapPx, scale), gapPx))
                return scale;
        }
        return 0f;
    }

    /**
     * The gap between a strip's columns: what {@code perLine} columns of at least
     * {@code columnPx} leave of {@code widthPx}, shared between the gaps and capped at
     * {@code maxGapPx}; never negative.
     */
    static int stripGapPx(int widthPx, int columnPx, int perLine, int maxGapPx) {
        if (perLine <= 1)
            return maxGapPx;
        int spare = widthPx - perLine * columnPx;
        return Math.max(0, Math.min(maxGapPx, spare / (perLine - 1)));
    }
}
