package com.termux.shared.termux.extrakeys;

final class ExtraKeysGesturePolicy {

    private ExtraKeysGesturePolicy() {}

    /**
     * Which toolbar page a sideways drag that started on a key asks for: +1 for the next page (the
     * finger went left), -1 for the previous one (it went right), 0 for no page change. Only a
     * horizontal-dominant drag past the threshold counts, so taps, holds and the swipe-up popup
     * gesture never turn the page.
     */
    static int toolbarPageSwipeDirection(float leftTravelPx, float verticalTravelPx, float thresholdPx) {
        float horizontal = Math.abs(leftTravelPx);
        if (horizontal < thresholdPx || horizontal <= Math.abs(verticalTravelPx)) return 0;
        return leftTravelPx > 0 ? 1 : -1;
    }
}
