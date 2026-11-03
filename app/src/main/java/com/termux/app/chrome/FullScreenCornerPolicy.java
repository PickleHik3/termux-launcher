package com.termux.app.chrome;

/**
 * What Full screen asks of the surfaces that now reach the screen's own edges: with the system
 * bars hidden, a frame that stands in a rounded corner's band is cut by the curve, and a pane
 * whose top stands in the camera's band has its text run under the lens. Pure arithmetic in
 * window pixels; outside Full screen every answer is the surface's own, so nothing moves.
 *
 * <p>A frame's corner in a screen corner's band (its edge less than the screen radius from the
 * screen's edge) takes the larger of its own radius and the screen's, so its outline curves
 * with the glass of the phone. Keys, chin and height are not this policy's: only the outline.
 */
public final class FullScreenCornerPolicy {

    private FullScreenCornerPolicy() {}

    /**
     * The radius a frame's bottom corners wear: the screen's bottom radius when Full screen puts
     * the frame's bottom edge inside that corner's band, else its own.
     */
    public static float bottomCornerRadiusPx(boolean fullScreen, float ownRadiusPx,
                                             int frameBottomPx, int windowHeightPx,
                                             float screenBottomRadiusPx) {
        float own = Math.max(0f, ownRadiusPx);
        if (!fullScreen || screenBottomRadiusPx <= 0f || windowHeightPx <= 0) return own;
        if (windowHeightPx - frameBottomPx >= screenBottomRadiusPx) return own;
        return Math.max(own, screenBottomRadiusPx);
    }

    /**
     * The radius a frame's top corners wear: the screen's top radius when Full screen puts the
     * frame's top edge inside that corner's band, else its own.
     */
    public static float topCornerRadiusPx(boolean fullScreen, float ownRadiusPx, int frameTopPx,
                                          float screenTopRadiusPx) {
        float own = Math.max(0f, ownRadiusPx);
        if (!fullScreen || screenTopRadiusPx <= 0f) return own;
        if (frameTopPx >= screenTopRadiusPx) return own;
        return Math.max(own, screenTopRadiusPx);
    }

    /**
     * How far a pane's text starts below the pane's top so it clears the camera: what of the
     * cutout's safe top inset ({@code safeTopPx}) still reaches past the pane's top, when Full
     * screen leaves the pane's top inside the cutout's band and the cutout's box
     * ({@code holeLeft..holeRight}) overlaps the pane across. Zero otherwise: a pane below the
     * band, beside the hole, or with the system bar showing keeps its text where it was.
     */
    public static int paneTextTopInsetPx(boolean fullScreen, int paneLeftPx, int paneTopPx,
                                         int paneRightPx, int holeLeftPx, int holeRightPx,
                                         int safeTopPx) {
        if (!fullScreen || safeTopPx <= 0 || holeRightPx <= holeLeftPx) return 0;
        if (paneTopPx >= safeTopPx) return 0;
        if (paneRightPx <= holeLeftPx || paneLeftPx >= holeRightPx) return 0;
        return safeTopPx - Math.max(0, paneTopPx);
    }
}
