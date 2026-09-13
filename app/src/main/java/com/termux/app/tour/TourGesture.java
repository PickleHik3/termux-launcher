package com.termux.app.tour;

/**
 * The gesture a card is asking for, and the only thing the finger glyph needs to know to trace it.
 *
 * <p>One value per stage of a step rather than one per step: "drag the status bar down, then up"
 * is two traces over the same target, and the overlay should not have to guess the second one from
 * the first.
 */
public enum TourGesture {
    /** No trace at all — the closing card. */
    NONE,
    TAP,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    SWIPE_UP,
    DRAG_DOWN,
    DRAG_UP,
    /** Slide along a row, then lift away from it: the A–Z scrub. */
    SCRUB
}
