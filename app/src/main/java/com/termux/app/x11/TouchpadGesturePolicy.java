package com.termux.app.x11;

import com.termux.x11.input.InputStub;

/**
 * The decisions the display's touchpad makes from finger counts and travel, kept free of views
 * so they can be pinned by tests. The set is a laptop's: one finger points, two scroll or pinch,
 * three swipe; a tap clicks the button its finger count names.
 */
final class TouchpadGesturePolicy {

    private TouchpadGesturePolicy() {}

    /** What two fingers are doing, once they have moved enough to tell. */
    enum TwoFingerMode { UNDECIDED, SCROLL, PINCH }

    /** Where three fingers went, once they have gone far enough to count. */
    enum Swipe { NONE, LEFT, RIGHT, UP, DOWN }

    /** A tap's button by how many fingers made it: one left, two right, three or more middle. */
    static int tapButton(int fingers) {
        if (fingers >= 3) return InputStub.BUTTON_MIDDLE;
        if (fingers == 2) return InputStub.BUTTON_RIGHT;
        return InputStub.BUTTON_LEFT;
    }

    /**
     * Two fingers scroll when their midpoint travels and pinch when the gap between them changes;
     * whichever passes {@code slop} first, by more than the other, wins the gesture. A pinch has to
     * beat the travel outright because fingers closing rarely keep their midpoint still, while
     * fingers scrolling keep their gap very steady.
     */
    static TwoFingerMode decideTwoFingers(float centroidTravel, float spreadChange, float slop) {
        float spread = Math.abs(spreadChange);
        if (spread > slop && spread > centroidTravel) return TwoFingerMode.PINCH;
        if (centroidTravel > slop) return TwoFingerMode.SCROLL;
        return TwoFingerMode.UNDECIDED;
    }

    /** The direction of a swipe once its dominant axis has moved past {@code threshold}. */
    static Swipe swipe(float dx, float dy, float threshold) {
        if (Math.abs(dx) < threshold && Math.abs(dy) < threshold) return Swipe.NONE;
        if (Math.abs(dx) >= Math.abs(dy)) return dx < 0 ? Swipe.LEFT : Swipe.RIGHT;
        return dy < 0 ? Swipe.UP : Swipe.DOWN;
    }

    /**
     * How many zoom clicks a pinch has earned so far: the gap's growth on a log scale, so
     * doubling the gap is worth the same number of clicks whatever size it started at, in
     * steps of {@code stepLog2}. Positive means fingers spreading, which zooms in.
     */
    static int pinchClicks(float startSpread, float spread, float stepLog2) {
        if (startSpread <= 0f || spread <= 0f) return 0;
        double log2 = Math.log(spread / startSpread) / Math.log(2);
        return (int) (log2 / stepLog2);
    }
}
