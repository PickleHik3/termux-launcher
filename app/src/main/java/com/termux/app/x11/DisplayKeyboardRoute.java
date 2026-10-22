package com.termux.app.x11;

import androidx.annotation.NonNull;

/**
 * Where a request for a keyboard goes. The Display place can be typed into with the phone's own
 * keyboard — the "Use Android keyboard" setting — and while it is, every route into a keyboard on
 * that place (the keyboard key, {@code keyboard.show}/{@code hide}, the text-focus policy) aims at
 * the system IME over the display's own view rather than at the launcher's built-in keyboard.
 *
 * <p>The setting is the Display place's alone: anywhere else, and with no display to type into,
 * the answer is the built-in keyboard exactly as before — or nothing at all, when the user has
 * switched that keyboard off.
 */
public final class DisplayKeyboardRoute {

    private DisplayKeyboardRoute() {}

    /** What the drawer's search asks of the keyboard. */
    public enum DrawerSearch {
        /** Its own way: a system-keyboard ask aimed at the terminal view. */
        SYSTEM_ASK,
        /** Raise the launcher's keyboard, which is up or down at the user's own say. */
        RAISE_BUILT_IN,
        /** The user switched the keyboard off: turn it on, then raise it. */
        TURN_ON_AND_RAISE,
        /** No on-screen keyboard appears; a physical keyboard types into the search. */
        NONE
    }

    /**
     * What the drawer's search does about a keyboard. Its request is a system-keyboard ask aimed
     * at the terminal view, which is served everywhere but over a running display: there the
     * display's view holds the focus and the ask is dropped, so nothing appears. The launcher's
     * keyboard has to be raised for it instead — except where the user has taken it away.
     *
     * <p>"Use system keyboard" (the drawer's own setting) never gets here: the search then has its
     * own text field and the phone's keyboard.
     *
     * @param builtInEnabled        the launcher's keyboard is switched on in Settings
     * @param turnedOff             the user switched it off with the keyboard key; the search, a
     *                              request for one, brings it back
     * @param yieldsToHardwareKeyboard it is set to stay away while a physical keyboard is
     *                              attached, and one is: nothing on screen, keys go to the search
     */
    @NonNull
    public static DrawerSearch drawerSearch(boolean onDisplayPlace, boolean displayRunning,
                                            boolean builtInEnabled, boolean turnedOff,
                                            boolean yieldsToHardwareKeyboard) {
        if (!onDisplayPlace || !displayRunning || !builtInEnabled) return DrawerSearch.SYSTEM_ASK;
        if (yieldsToHardwareKeyboard) return DrawerSearch.NONE;
        return turnedOff ? DrawerSearch.TURN_ON_AND_RAISE : DrawerSearch.RAISE_BUILT_IN;
    }

    /**
     * Whether the drawer closing puts back down the keyboard its search raised. Only one it raised
     * itself, that the user has not claimed since (by pinning or hiding it by hand), and that is
     * still up: a keyboard that was there first, or that the user took over, is theirs to close.
     */
    public static boolean drawerCloseLowers(boolean raisedByDrawer, boolean userTouchedSince,
                                            boolean stillUp) {
        return raisedByDrawer && !userTouchedSince && stillUp;
    }

    /** The keyboard a request lands on. */
    public enum Target {
        /** The phone's own keyboard, typing into the display's view. */
        SYSTEM_IME,
        /** The launcher's built-in keyboard. */
        BUILT_IN,
        /** There is no keyboard to aim at, and the request cannot be met. */
        NONE
    }

    /**
     * @param androidKeyboard    the "Use Android keyboard" setting
     * @param onDisplayPlace     the wall rests on the Display place
     * @param displayRunning     a display server is up, so there is something to type into
     * @param builtInEnabled     the launcher's own keyboard is switched on
     */
    @NonNull
    public static Target decide(boolean androidKeyboard, boolean onDisplayPlace,
                                boolean displayRunning, boolean builtInEnabled) {
        if (androidKeyboard && onDisplayPlace && displayRunning) return Target.SYSTEM_IME;
        return builtInEnabled ? Target.BUILT_IN : Target.NONE;
    }
}
