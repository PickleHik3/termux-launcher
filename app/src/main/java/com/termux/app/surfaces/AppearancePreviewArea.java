package com.termux.app.surfaces;

/**
 * The one preview area every editor page of the Appearance surface shows the launcher in: Look,
 * Layout and Icon pack stand the miniature on the same rect, so switching pages never moves or
 * resizes it. Pure arithmetic in pixels; {@link SurfaceEditorController} measures, this decides.
 *
 * <p>The contract, in dp:</p>
 * <ul>
 *   <li><b>Top</b>: the page's top inset (the status bar and the cutout, exactly as
 *   {@link AppearanceSurfaceController} pads the page bar), then the {@value #BAR_DP}dp bar, then
 *   {@value #GAP_DP}dp. The miniature's visible top edge is that line, never above it, on every
 *   page.</li>
 *   <li><b>Bottom</b>: {@value #GAP_DP}dp above the sheet, which is the sheet reserve tall on
 *   every page and at every stop: the tallest sheet of the three pages (Look with its Custom row
 *   up, Layout, Icon pack), but never so tall that the preview would fall below
 *   {@link AppearanceEditorFrame#MIN_SCALE} ({@link #reservePx}). Its top edge is one line and the
 *   miniature one size everywhere; where the Custom row is taller than the reserve (a short phone,
 *   Large text) it scrolls inside the sheet instead of growing it.</li>
 *   <li><b>Size</b>: the visible miniature is the launcher's container plus the bands of the
 *   display the frame shows above and below it ({@link #topRevealPx}, and the navigation bar's),
 *   scaled to fit between the two lines, at most {@link AppearanceEditorFrame#MAX_SCALE}.</li>
 * </ul>
 *
 * <p>A tablet in landscape ({@link EditorFormFactor#tabletLandscape}) keeps the top line and
 * moves the sheet beside the preview: a side pane {@link #SIDE_PANE_DP} wide against the right
 * edge, from the top line to the foot, one height on every page. The bottom line is then
 * {@link #GAP_DP} above the navigation bar, and the miniature, fit to the column left of the pane
 * as well, is centred in that column ({@link #sideScale}, {@link #translationX(int, int, int, int,
 * int, int, float)}).</p>
 */
final class AppearancePreviewArea {

    private AppearancePreviewArea() {}

    /** The page bar's height: back, the page pill, Undo and Done. */
    static final int BAR_DP = 64;
    /** The air between the preview and the bar above it, and the sheet below it. */
    static final int GAP_DP = 8;

    /**
     * How far the page's content stands below the top of the content view: the window's status
     * bar and cutout inset less where the content view already starts. The page bar is padded by
     * exactly this, so the preview measured from it is measured from the bar.
     */
    static int pageInsetTopPx(int barsAndCutoutTopPx, int contentTopInWindowPx) {
        return Math.max(0, barsAndCutoutTopPx - contentTopInWindowPx);
    }

    /** The preview's top line in the content view: under the bar, {@link #GAP_DP} clear of it. */
    static int topPx(int pageInsetTopPx, float density) {
        return pageInsetTopPx + Math.round((BAR_DP + GAP_DP) * density);
    }

    /** The preview's bottom line: {@link #GAP_DP} above a sheet {@code sheetPx} tall. */
    static int bottomPx(int windowHeightPx, int sheetPx, float density) {
        return windowHeightPx - sheetPx - Math.round(GAP_DP * density);
    }

    /** A page's sheet: its content's height, but never shorter than the shared reserve. */
    static int sheetPx(int reservePx, int contentPx) {
        return Math.max(Math.max(0, reservePx), contentPx);
    }

    /**
     * The tallest sheet that still leaves the preview at {@link AppearanceEditorFrame#MIN_SCALE}:
     * the room under the preview's top line less the miniature at that scale and the gap.
     */
    static int sheetCapPx(int windowHeightPx, int topPx, int containerHeightPx, int revealTopPx,
                          int revealBottomPx, float density) {
        int visible = containerHeightPx + Math.max(0, revealTopPx) + Math.max(0, revealBottomPx);
        return windowHeightPx - Math.round(GAP_DP * density) - topPx
            - (int) Math.ceil(visible * AppearanceEditorFrame.MIN_SCALE);
    }

    /**
     * The sheet reserve: the Custom row's sheet ({@code customPx}, the tallest the Look page gets)
     * where it fits under {@code capPx}, else the cap, and never shorter than the other pages'
     * resting sheets ({@code restingPx}), which do not scroll.
     */
    static int reservePx(int restingPx, int customPx, int capPx) {
        return Math.max(Math.max(0, restingPx), Math.min(customPx, capPx));
    }

    /**
     * The scale that fits the visible miniature, the container and the display bands it shows
     * above ({@code revealTopPx}) and below ({@code revealBottomPx}), between the two lines.
     */
    static float scale(int containerHeightPx, int revealTopPx, int revealBottomPx, int topPx,
                       int bottomPx) {
        int visible = containerHeightPx + Math.max(0, revealTopPx) + Math.max(0, revealBottomPx);
        return AppearanceEditorFrame.fitScale(visible, topPx, bottomPx,
            AppearanceEditorFrame.MAX_SCALE);
    }

    // ---- a tablet in landscape: the preview on the left, the sheet as a side pane -------------

    /**
     * The side pane's content width on a tablet in landscape ({@link EditorFormFactor}): the
     * widest phone the sheet's width arithmetic is laid out and tested at
     * (appearance_editor_panel's header; AppearanceEditorPanelFit411Test and its Large text
     * twin), so every row stands in the pane exactly as it stands on that phone.
     */
    static final int SIDE_PANE_DP = 411;

    /**
     * The side pane's left edge in the content view: {@link #SIDE_PANE_DP} of content and the
     * display's right inset (a cutout or a side navigation bar) its content stands clear of,
     * against the content's right edge. The pane's fill runs to that edge.
     */
    static int sidePaneLeftPx(int contentWidthPx, int rightInsetPx, float density) {
        return contentWidthPx - Math.round(SIDE_PANE_DP * density) - Math.max(0, rightInsetPx);
    }

    /**
     * The side pane's height: from the preview's top line ({@link #topPx}), so the pane's top
     * edge and the miniature's stand level under the bar, to the content's foot. The controller
     * pads its bottom by the navigation inset, as it pads the bottom sheet.
     */
    static int sidePaneHeightPx(int windowHeightPx, int topPx) {
        return Math.max(0, windowHeightPx - topPx);
    }

    /**
     * The preview's column beside the side pane, {left, right} in the content view:
     * {@link #GAP_DP} clear of the display's left inset and of the pane.
     */
    static int[] sideColumnPx(int leftInsetPx, int paneLeftPx, float density) {
        int gap = Math.round(GAP_DP * density);
        int left = Math.max(0, leftInsetPx) + gap;
        return new int[] {left, Math.max(left, paneLeftPx - gap)};
    }

    /**
     * The scale in the side column: the fit between the two lines, as on a phone, held to the
     * column's width. The visible miniature is the container and the display's side bands
     * ({@code revealLeftPx}, {@code revealRightPx}). The width is never floored at
     * {@link AppearanceEditorFrame#MIN_SCALE}: a narrow tablet shows a smaller preview, never one
     * that runs under the pane.
     */
    static float sideScale(int containerWidthPx, int containerHeightPx, int revealTopPx,
                           int revealBottomPx, int revealLeftPx, int revealRightPx, int topPx,
                           int bottomPx, int columnLeftPx, int columnRightPx) {
        float byHeight = scale(containerHeightPx, revealTopPx, revealBottomPx, topPx, bottomPx);
        int visible = containerWidthPx + Math.max(0, revealLeftPx) + Math.max(0, revealRightPx);
        if (visible <= 0)
            return byHeight;
        return Math.min(byHeight, Math.max(0, columnRightPx - columnLeftPx) / (float) visible);
    }

    /**
     * How far down from the top line the miniature moves to stand centred between the two lines
     * when the column's width, not the lines, set its scale ({@link #sideScale}): half the height
     * left over, never negative. Phones never have any: their scale fills the lines.
     */
    static float sideCentreOffsetPx(int containerHeightPx, int revealTopPx, int revealBottomPx,
                                    int topPx, int bottomPx, float scale) {
        int visible = containerHeightPx + Math.max(0, revealTopPx) + Math.max(0, revealBottomPx);
        float spare = (bottomPx - topPx) - visible * scale;
        return Math.max(0f, spare / 2f);
    }

    /**
     * The container's horizontal translation (its pivot is its horizontal centre) that centres the
     * visible miniature, the container and the display's side bands, in the column
     * {@code columnLeftPx}..{@code columnRightPx}. Phones never translate it: 0 there.
     */
    static float translationX(int columnLeftPx, int columnRightPx, int containerLeftPx,
                              int containerWidthPx, int revealLeftPx, int revealRightPx,
                              float scale) {
        float centre = (columnLeftPx + columnRightPx) / 2f;
        return centre - containerLeftPx - containerWidthPx / 2f
            - scale * (Math.max(0, revealRightPx) - Math.max(0, revealLeftPx)) / 2f;
    }

    /**
     * The container's translation (its pivot is its top) that stands the visible top edge, the
     * band {@code revealTopPx} above the container, on the preview's top line.
     */
    static float translationY(int topPx, int containerTopPx, int revealTopPx, float scale) {
        return topPx + Math.max(0, revealTopPx) * scale - containerTopPx;
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
