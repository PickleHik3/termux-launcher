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
 *   <li><b>Bottom</b>: {@value #GAP_DP}dp above the sheet. A page's sheet is at least the sheet
 *   reserve tall, the tallest resting sheet of the three pages (Look at a stop, Layout, Icon
 *   pack), so its top edge is one line on every page and the miniature one size. Only the Look
 *   page's Custom row, raised on request, stands taller and takes its room from the preview,
 *   which it gives back when it goes.</li>
 *   <li><b>Size</b>: the visible miniature is the launcher's container plus the bands of the
 *   display the frame shows above and below it (the status bar's and the navigation bar's),
 *   scaled to fit between the two lines, at most {@link AppearanceEditorFrame#MAX_SCALE}.</li>
 * </ul>
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
     * The scale that fits the visible miniature, the container and the display bands it shows
     * above ({@code revealTopPx}) and below ({@code revealBottomPx}), between the two lines.
     */
    static float scale(int containerHeightPx, int revealTopPx, int revealBottomPx, int topPx,
                       int bottomPx) {
        int visible = containerHeightPx + Math.max(0, revealTopPx) + Math.max(0, revealBottomPx);
        return AppearanceEditorFrame.fitScale(visible, topPx, bottomPx,
            AppearanceEditorFrame.MAX_SCALE);
    }

    /**
     * The container's translation (its pivot is its top) that stands the visible top edge, the
     * band {@code revealTopPx} above the container, on the preview's top line.
     */
    static float translationY(int topPx, int containerTopPx, int revealTopPx, float scale) {
        return topPx + Math.max(0, revealTopPx) * scale - containerTopPx;
    }
}
