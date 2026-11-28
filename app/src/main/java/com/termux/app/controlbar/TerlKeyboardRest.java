package com.termux.app.controlbar;

/**
 * When the launcher's keyboard goes down under Terl for something other than its own key (pong,
 * 11-28), pure. The keyboard is one for every place; these are the moments its being up would
 * leave a room nobody asked for.
 *
 * <ul>
 *   <li>The drawer scrolled it away: the drawer only slides it out of sight, so its state stayed
 *   up, the bar kept riding where it stood and the drawer kept the room above it; a close brought
 *   it back. Under the bar it goes down for real.</li>
 *   <li>Home comes in: Home arrives without the keyboard, unless Home keeps it on (Settings, the
 *   Home keyboard always on).</li>
 * </ul>
 */
public final class TerlKeyboardRest {

    private TerlKeyboardRest() {}

    /** Whether the keyboard the drawer scrolled away goes down for real: under the bar, while up. */
    public static boolean downOnDrawerScroll(boolean underBar, boolean keyboardUp) {
        return underBar && keyboardUp;
    }

    /**
     * Whether Home coming in puts the keyboard down: Home was wholly away ({@code fromTravel} 1, the
     * tiling desktop's {@code moveHome}) and starts to come in ({@code toTravel} below 1), the
     * keyboard is up, and Home does not keep it on.
     */
    public static boolean downOnHomeArrival(float fromTravel, float toTravel, boolean keyboardUp,
                                            boolean homeKeyboardAlwaysOn) {
        return fromTravel >= 1f && toTravel < 1f && keyboardUp && !homeKeyboardAlwaysOn;
    }
}
