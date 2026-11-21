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

    /** Steps along a card's corner arc when {@link #cornerClearGapPx} checks it against the screen's. */
    private static final int ARC_STEPS = 32;

    /**
     * The gap a Floating card keeps from the screen edge it stands on (top or bottom): its own
     * outside Full screen; in Full screen, where that edge is the glass's own, the larger of its
     * own, its side gap (so it floats evenly) and what its corners need to clear the screen's
     * rounded corner ({@link #cornerClearGapPx}).
     */
    public static int floatingEdgeGapPx(boolean fullScreen, int ownGapPx, int sideGapPx,
                                        float cardRadiusPx, float screenRadiusPx) {
        int own = Math.max(0, ownGapPx);
        if (!fullScreen) return own;
        return Math.max(Math.max(own, Math.max(0, sideGapPx)),
            cornerClearGapPx(sideGapPx, cardRadiusPx, screenRadiusPx));
    }

    /**
     * The least gap from the screen's edge at which a card standing {@code sideGapPx} in from the
     * side, its corners at {@code cardRadiusPx}, keeps its whole corner inside a screen corner of
     * {@code screenRadiusPx}: the most any point of its corner arc sits outside the screen's
     * curve, rounded up. 0 with no screen radius.
     */
    public static int cornerClearGapPx(int sideGapPx, float cardRadiusPx, float screenRadiusPx) {
        float big = screenRadiusPx;
        if (!(big > 0f)) return 0;
        float side = Math.max(0, sideGapPx);
        float r = Math.max(0f, cardRadiusPx);
        double need = 0;
        for (int i = 0; i <= ARC_STEPS; i++) {
            double angle = Math.PI / 2 * i / ARC_STEPS;
            double x = side + r - r * Math.cos(angle);
            double arcOffset = r - r * Math.sin(angle);
            // The screen's curve is symmetric: as far down from the edge at x as it is in from
            // the side at that distance (StatusRowKeepOut owns the curve).
            double screenEdge = com.termux.app.statusbar.StatusRowKeepOut.cornerInsetPx(big,
                (float) x);
            need = Math.max(need, screenEdge - arcOffset);
        }
        return (int) Math.ceil(need - 1e-3);
    }

    /**
     * How far a pane's text starts below the pane's top so it clears the camera: what of the
     * camera's box ({@link CameraHoleGeometry#keepOutBox}) still reaches past the pane's top, when
     * Full screen leaves the pane's top inside the box's band and the box ({@code boxLeftPx..
     * boxRightPx}) overlaps the pane across. Zero otherwise: a pane below the box, beside it, or
     * with the system bar showing keeps its text where it was.
     */
    public static int paneTextTopInsetPx(boolean fullScreen, int paneLeftPx, int paneTopPx,
                                         int paneRightPx, int boxLeftPx, int boxRightPx,
                                         int boxBottomPx) {
        if (!fullScreen || boxBottomPx <= 0 || boxRightPx <= boxLeftPx) return 0;
        if (paneTopPx >= boxBottomPx) return 0;
        if (paneRightPx <= boxLeftPx || paneLeftPx >= boxRightPx) return 0;
        return boxBottomPx - Math.max(0, paneTopPx);
    }
}
