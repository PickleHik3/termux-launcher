package com.termux.app.surfaces;

/**
 * How much of the room above the accessory stack the surface editor's slider region gets: all of
 * it, but never less than a usable strip.
 *
 * <p>The card is a live editor pinned above the accessory stack — the dock, and the in-app
 * keyboard when it is up — and it spans from just under the launcher's status pills down to that
 * anchor. Earlier rules capped it at a fraction of the display and reserved a preview band of
 * terminal above it, which read as dead space: the surfaces being edited sit at the screen's
 * edges, not behind the card, and the peek-on-drag fade already clears the card whenever a slider
 * is actually being moved. So the card spends the whole span, and a short section simply has a
 * scroll region with nothing to scroll.
 *
 * <p>Spending the whole span used to mean spending nothing when the span was gone. Issue #20: with
 * a system IME up on a Samsung One UI phone the room between the anchors fell below the card's own
 * header and action row, the slider region clamped to zero, and the editor rendered as a title
 * with Reset and Done under it — no presets, no controls, nothing to scroll. The room can go that
 * small for reasons the editor cannot see or fix (the root view's visible-frame probe subtracting
 * a keyboard's height more than once, a cramped display, an anchor that is not laid out), so the
 * floor here is unconditional: below it the card stops honouring the anchors and overlaps whatever
 * is above it, because an editor that overlaps the terminal is usable and an editor with no body
 * is not. The ceiling keeps that overlap inside the parent, so the header can never be pushed off
 * the top of the screen — the failure the clamp-to-zero was avoiding in the first place.
 *
 * <p>Pure arithmetic on pixels, no views, so the cases that matter — keyboard up, keyboard down,
 * a cramped screen, an anchor that has collapsed — are testable without inflating the editor.
 */
public final class SurfaceEditorCardMetrics {

    private SurfaceEditorCardMetrics() {}

    /**
     * The height to give the editor's scrolling slider region.
     *
     * @param availablePx room between the launcher's own status bar and the accessory stack
     * @param chromePx    everything in the card outside the scroll region: padding, header and the
     *                    action row, as measured
     * @param minScrollPx the shortest slider region worth showing; the card overlaps what is above
     *                    it rather than going below this
     * @param maxScrollPx the tallest slider region the parent can hold with the chrome on top of
     *                    it; bounds the overlap, and wins over {@code minScrollPx} when even the
     *                    floor will not fit
     * @return the scroll height in pixels; {@code chromePx} plus this is the resulting card height
     */
    public static int scrollHeightPx(int availablePx, int chromePx, int minScrollPx,
                                     int maxScrollPx) {
        int ceiling = Math.max(0, maxScrollPx);
        int floor = Math.max(0, Math.min(minScrollPx, ceiling));
        int fitted = availablePx - chromePx;
        if (fitted < floor)
            return floor;
        return Math.min(fitted, ceiling);
    }
}
