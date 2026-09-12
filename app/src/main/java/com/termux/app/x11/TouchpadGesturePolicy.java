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

    /** The value of "last tap" before any tap has landed. */
    static final long NO_TAP = Long.MIN_VALUE;

    /**
     * Whether a finger landing at {@code downTime} came soon enough after the last left tap, at
     * {@code lastTapTime}, to turn its travel into a drag. A pad that has never been tapped arms
     * nothing: {@link #NO_TAP} is a marker, not a time, and subtracting it would wrap.
     */
    static boolean tapDragArmed(long downTime, long lastTapTime, long windowMs) {
        if (lastTapTime == NO_TAP) return false;
        long since = downTime - lastTapTime;
        return since >= 0 && since <= windowMs;
    }

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

    /**
     * Whether a touch at {@code x} lands in the scroll strip along the pad's trailing edge:
     * within {@code hitBand} of the pad's own right edge at {@code panelRight}. The strip's hit
     * band reaches wider than the strip is drawn, the same way a rail's hit band does, so a
     * thumb does not have to land on the thin track itself.
     */
    static boolean stripHit(float x, float panelRight, float hitBand) {
        return x >= panelRight - hitBand;
    }

    /**
     * Whole wheel notches earned by {@code accum} travel at {@code notch} spacing, signed by
     * direction. The caller keeps what is left over by subtracting the count times {@code notch}
     * from its own accumulator. Shared by the two-finger scroll and the strip, which differ only
     * in notch size.
     */
    static int notchCount(float accum, float notch) {
        return notch > 0f ? (int) (accum / notch) : 0;
    }

    /**
     * Whether the strip still leaves a usable pointing area once it takes {@code stripWidth} off
     * the pad's own {@code panelWidth}; below {@code minPointing} left over, the strip hides
     * instead of crowding the pointing area further, rather than the pad growing to make room.
     */
    /**
     * Where the strip's grip is drawn while a thumb holds it: it follows the thumb's travel from
     * where it landed, easing into the ends of its reach rather than stopping dead against them,
     * so the grip moves with the finger and reads as the handle it looks like. The scroll itself is
     * unbounded — wheel notches — so the grip's place carries no position, only motion, and it
     * settles back to the centre when the thumb lifts.
     *
     * @param travelPx the thumb's travel along the strip since it landed, signed
     * @param reachPx  how far from the centre the grip may go, never negative
     */
    static float gripOffset(float travelPx, float reachPx) {
        float reach = Math.max(0f, reachPx);
        if (reach <= 0f || travelPx == 0f) return 0f;
        return reach * (float) Math.tanh(travelPx / reach);
    }

    static boolean stripFits(float panelWidth, float stripWidth, float minPointing) {
        return panelWidth - stripWidth >= minPointing;
    }
}
