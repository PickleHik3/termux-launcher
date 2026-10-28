package com.termux.app.wall;

/**
 * The page overview's small decisions that are plain arithmetic, kept out of the view: whether the
 * page frame stands posed by the surface editor, how a card's picture is taken, which card a tap
 * zooms, and the size a chip's text fits at.
 */
final class OverviewGeometry {

    private OverviewGeometry() {}

    /** How a card's picture is taken. */
    enum Capture {
        /** A copy of the window's pixels: only while nothing is drawn over the card's page. */
        WINDOW_COPY,
        /** A software draw of the page's own view subtree, which overlays outside it never enter. */
        SUBTREE_DRAW,
        /** The terminal's text drawn from its screen. */
        TEXT
    }

    /** The picture source for a live page card: {@code covered} is a sheet over the page. */
    static Capture captureFor(boolean covered, boolean terminal) {
        if (!covered) return Capture.WINDOW_COPY;
        return terminal ? Capture.TEXT : Capture.SUBTREE_DRAW;
    }

    /** Whether the page frame is off its natural place (the surface editor posed it). */
    static boolean framePosed(float scaleX, float scaleY, float translationX, float translationY) {
        return Math.abs(scaleX - 1f) > 0.001f || Math.abs(scaleY - 1f) > 0.001f
            || Math.abs(translationX) > 0.5f || Math.abs(translationY) > 0.5f;
    }

    /** Whether a tap on card {@code index} zooms in: only the centred card does; others centre. */
    static boolean tapZooms(int index, float focus) {
        return Math.round(focus) == index && Math.abs(focus - index) < 0.5f;
    }

    /** Measures a text's width at a size, in px. */
    interface Measure {
        float width(float sizePx);
    }

    /** The largest size in {@code [minPx, maxPx]} whose width fits {@code availablePx}, else the least. */
    static float fitTextSize(float maxPx, float minPx, float availablePx, Measure measure) {
        float size = maxPx;
        while (size > minPx && measure.width(size) > availablePx) size -= 0.5f;
        return Math.max(minPx, size);
    }
}
