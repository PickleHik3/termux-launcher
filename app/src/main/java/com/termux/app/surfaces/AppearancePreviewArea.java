package com.termux.app.surfaces;

/**
 * The display round the Appearance editor's frame, as pure arithmetic in pixels: where the frame's
 * top line starts under the status bar and the cutout, and how much of the status-bar band above
 * the launcher's container the frame shows, so the element at the top of the screen keeps the
 * air it has beside it. {@link EditorFramePose} decides the frame's size and place from these.
 */
final class AppearancePreviewArea {

    private AppearancePreviewArea() {}

    /**
     * How far the content stands below the top of the content view: the window's status bar and
     * cutout inset less where the content view already starts.
     */
    static int pageInsetTopPx(int barsAndCutoutTopPx, int contentTopInWindowPx) {
        return Math.max(0, barsAndCutoutTopPx - contentTopInWindowPx);
    }

    /**
     * How much of the display above the container the miniature shows, in the container's own
     * pixels: the frame wraps the element at the top of the screen (the status bar, or the
     * terminal where no bar stands there) with the same air above it as beside it, and with
     * enough that the display's corner arc never cuts into it. Never more than the display has
     * there ({@code displayInsetTopPx}). With no element to wrap, the display's own top edge.
     *
     * @param containerWidthPx the container's unscaled width
     * @param displayInsetLeftPx how far the display reaches past the container on the left
     * @param displayInsetTopPx  how far it reaches above the container
     * @param displayInsetRightPx how far it reaches past it on the right
     * @param cornerRadiusPx     the display's corner radius, in the container's pixels
     * @param element            the top element's {left, top, right} in the container, or null
     * @param elementRadiusPx    that element's own corner radius
     */
    static int topRevealPx(int containerWidthPx, int displayInsetLeftPx, int displayInsetTopPx,
                           int displayInsetRightPx, float cornerRadiusPx, int[] element,
                           float elementRadiusPx) {
        int most = Math.max(0, displayInsetTopPx);
        if (element == null || element.length < 3 || element[2] <= element[0])
            return most;
        int left = element[0];
        int top = element[1];
        int right = containerWidthPx - element[2];
        float radius = Math.max(0f, elementRadiusPx);
        // The same air above as beside it: the narrower side's, as the element shows it.
        float air = Math.min(left, right) - top;
        float arc = Math.max(
            arcClearance(left, top, radius, Math.max(0, displayInsetLeftPx), cornerRadiusPx),
            arcClearance(right, top, radius, Math.max(0, displayInsetRightPx), cornerRadiusPx));
        float reveal = Math.max(air, arc);
        return Math.max(0, Math.min(most, (int) Math.ceil(reveal)));
    }

    /**
     * The least the frame's top must stand above the container so that a corner arc of
     * {@code cornerRadiusPx}, whose rect reaches {@code insetSidePx} past the container's side,
     * holds an element corner standing {@code sidePx} in from that side and {@code topPx} down,
     * rounded by {@code radiusPx}. Measured along the element's corner arc; its straight edges
     * are never nearer the frame's arc than the arc's two ends.
     */
    static float arcClearance(int sidePx, int topPx, float radiusPx, int insetSidePx,
                              float cornerRadiusPx) {
        if (cornerRadiusPx <= 0f)
            return Float.NEGATIVE_INFINITY;
        float centreX = cornerRadiusPx - insetSidePx;
        float need = Float.NEGATIVE_INFINITY;
        final int steps = 16;
        for (int i = 0; i <= steps; i++) {
            double angle = Math.PI / 2 * i / steps;
            float x = sidePx + radiusPx - (float) (radiusPx * Math.cos(angle));
            float y = topPx + radiusPx - (float) (radiusPx * Math.sin(angle));
            float dx = centreX - x;
            if (dx <= 0f)
                continue;
            // On the frame's own side (or past it) the arc only meets the point at its foot.
            float rise = dx >= cornerRadiusPx ? 0f
                : (float) Math.sqrt(cornerRadiusPx * cornerRadiusPx - dx * dx);
            // The arc's centre stands cornerRadius below the frame's top: the point is inside
            // the arc while it is no higher than the centre less the rise.
            need = Math.max(need, cornerRadiusPx - rise - y);
        }
        return need;
    }
}
