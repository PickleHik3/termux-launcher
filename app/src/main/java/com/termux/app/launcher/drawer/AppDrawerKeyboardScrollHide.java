package com.termux.app.launcher.drawer;

/**
 * The search keyboard going away as the drawer's list is scrolled, the way launchers do it: the
 * first scroll of the list means "I am browsing now, not typing". Past a small threshold the
 * built-in keyboard slides down in step with the distance scrolled until it is gone, and is then
 * dismissed; the system keyboard, a window the plane cannot move, is dismissed at the threshold.
 *
 * <p>Only distance scrolled down the list counts, and it only adds up, so scrolling back up never
 * brings the keyboard back: a tap on the search does. Pure, so the mapping is testable.
 */
final class AppDrawerKeyboardScrollHide {

    /** How far the list scrolls before the keyboard starts to go. */
    static final float THRESHOLD_DP = 24f;

    /**
     * A scroll that stops with the keyboard at least this far down finishes the dismissal; one
     * that stops short of it lets the keyboard spring back up.
     */
    static final float DISMISS_AT_REST_FRACTION = 0.5f;

    private AppDrawerKeyboardScrollHide() {}

    /**
     * How far down the keyboard sits after the list has scrolled {@code scrolledPx} down: nothing
     * until the threshold, then one pixel per pixel scrolled, up to its whole travel.
     */
    static float translationPx(float scrolledPx, float thresholdPx, float travelPx) {
        if (travelPx <= 0f) return 0f;
        return Math.max(0f, Math.min(travelPx, scrolledPx - Math.max(0f, thresholdPx)));
    }

    /**
     * The same as a fraction of the travel: 0 fully up, 1 gone. A keyboard with no travel to
     * slide (the system keyboard) jumps from 0 to 1 at the threshold.
     */
    static float hiddenFraction(float scrolledPx, float thresholdPx, float travelPx) {
        if (travelPx <= 0f) return reachesThreshold(scrolledPx, thresholdPx) ? 1f : 0f;
        return translationPx(scrolledPx, thresholdPx, travelPx) / travelPx;
    }

    /**
     * Whether a drag that starts on the list may send the keyboard away: only when the list has
     * somewhere to go down. A list that fits above the keyboard does not scroll, and a drag on it
     * neither moves the keyboard nor holds it part-way; the search pill brings nothing back
     * because nothing went. Measured as the list measures itself
     * ({@code computeVerticalScroll*}), so it agrees with {@code canScrollVertically(1)}.
     */
    static boolean armsOnDrag(int scrollRangePx, int scrollExtentPx, int scrollOffsetPx) {
        return scrollRangePx - scrollExtentPx > 0
            && scrollOffsetPx < scrollRangePx - scrollExtentPx;
    }

    /** Whether the list has scrolled far enough for the keyboard to start going. */
    static boolean reachesThreshold(float scrolledPx, float thresholdPx) {
        return scrolledPx > Math.max(0f, thresholdPx);
    }

    /** Whether a scroll that came to rest with the keyboard {@code hidden} down finishes it off. */
    static boolean dismissesAtRest(float hidden) {
        return hidden >= DISMISS_AT_REST_FRACTION;
    }

    /** The scrolled distance after a scroll step of {@code dy}: only steps down the list count. */
    static float accumulate(float scrolledPx, int dy) {
        return dy > 0 ? scrolledPx + dy : scrolledPx;
    }
}
