package com.termux.app.surfaces;

/**
 * How much of the room above the accessory stack the surface editor's slider region gets: all of
 * it.
 *
 * <p>The card is a live editor pinned above the accessory stack — the dock, and the in-app
 * keyboard when it is up — and it spans from just under the launcher's status pills down to that
 * anchor. Earlier rules capped it at a fraction of the display and reserved a preview band of
 * terminal above it, which read as dead space: the surfaces being edited sit at the screen's
 * edges, not behind the card, and the peek-on-drag fade already clears the card whenever a slider
 * is actually being moved. So the card spends the whole span, and a short section simply has a
 * scroll region with nothing to scroll.
 *
 * <p>Pure arithmetic on pixels, no views, so the cases that matter — keyboard up, keyboard down,
 * a cramped screen — are testable without inflating the editor.
 */
public final class SurfaceEditorCardMetrics {

    private SurfaceEditorCardMetrics() {}

    /**
     * The height to give the editor's scrolling slider region.
     *
     * @param availablePx room between the launcher's own status bar and the accessory stack
     * @param chromePx    everything in the card outside the scroll region: padding, header and the
     *                    action row, as measured
     * @return the scroll height in pixels; {@code chromePx} plus this is the resulting card height
     */
    public static int scrollHeightPx(int availablePx, int chromePx) {
        return Math.max(0, availablePx - chromePx);
    }
}
