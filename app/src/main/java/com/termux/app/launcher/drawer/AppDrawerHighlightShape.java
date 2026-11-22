package com.termux.app.launcher.drawer;

/**
 * Where the keyboard-highlight bead sits inside an app cell: the union of the icon and the label's
 * text, grown by one even padding, kept inside the cell with a little spare so its edge is never
 * clipped. Pure arithmetic over pixel boxes; the cell reads the answer in {@code onLayout}.
 */
final class AppDrawerHighlightShape {

    /** One padding on all four sides of the icon-and-label content. */
    static final float PADDING_DP = 6f;
    /** Room kept between the bead's edge and the cell's edge. */
    static final float SPARE_DP = 1f;
    /**
     * The icon's corner radius as a fraction of its edge: near the squircle most adaptive masks
     * use (the same figure the icon placeholder tile draws with). No numeric radius exists for the
     * active mask, so this stands for the default one.
     */
    static final float ICON_CORNER_FRACTION = 0.30f;

    private AppDrawerHighlightShape() {}

    /**
     * Writes {@code {left, top, right, bottom}} of the bead into {@code out}.
     *
     * @param content {@code {left, top, right, bottom}} of icon and label text, in cell pixels
     * @param centerX the icon's centre line; used when {@code centerOnIcon}
     * @param centerOnIcon true for a stacked cell: the bead is symmetric about the icon's centre
     *     line, so a short name still gets an even ring; false for a row, which hugs the content
     */
    static void resolve(int cellWidth, int cellHeight, int[] content, int centerX,
                        boolean centerOnIcon, int paddingPx, int sparePx, int[] out) {
        int minX = sparePx;
        int maxX = cellWidth - sparePx;
        int minY = sparePx;
        int maxY = cellHeight - sparePx;
        if (maxX <= minX || maxY <= minY) {
            out[0] = 0;
            out[1] = 0;
            out[2] = Math.max(0, cellWidth);
            out[3] = Math.max(0, cellHeight);
            return;
        }
        int left;
        int right;
        if (centerOnIcon) {
            int half = Math.max(centerX - content[0], content[2] - centerX) + paddingPx;
            half = Math.min(half, Math.min(centerX - minX, maxX - centerX));
            half = Math.max(half, 0);
            left = centerX - half;
            right = centerX + half;
        } else {
            left = Math.max(minX, content[0] - paddingPx);
            right = Math.min(maxX, content[2] + paddingPx);
        }
        out[0] = left;
        out[1] = Math.max(minY, content[1] - paddingPx);
        out[2] = right;
        out[3] = Math.min(maxY, content[3] + paddingPx);
    }

    /** Concentric with the icon, never past a pill. */
    static float radiusPx(int iconSizePx, int paddingPx, int width, int height) {
        float radius = iconSizePx * ICON_CORNER_FRACTION + paddingPx;
        return Math.max(0f, Math.min(radius, Math.min(width, height) / 2f));
    }
}
