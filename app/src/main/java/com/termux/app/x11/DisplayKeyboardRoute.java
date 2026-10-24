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
        /** The Android keyboard is selected: a system-keyboard ask aimed at the terminal view. */
        SYSTEM_ASK,
        /** Raise the launcher's keyboard, which is up or down at the user's own say. */
        RAISE_BUILT_IN,
        /** The user switched the keyboard off: turn it on, then raise it. */
        TURN_ON_AND_RAISE,
        /** No on-screen keyboard appears; a physical keyboard types into the search. */
        NONE
    }

    /**
     * What the drawer's search does about a keyboard. With the built-in keyboard selected in
     * Settings it is always the built-in keyboard, on every place: the search types through it,
     * and the drawer reveals it through the plane. Only with the Android keyboard selected is the
     * request a system-keyboard ask aimed at the terminal view (the caller opens the system IME
     * gate first).
     *
     * <p>Before 2026-10-24 the built-in keyboard was raised only over a running display; anywhere
     * else the ask went to the system IME, so a place that hands the IME back while the built-in
     * keyboard is down (Home) showed the phone's keyboard although Built-in was selected.
     *
     * <p>"Use system keyboard" (the drawer's own setting) never gets here: the search then has its
     * own text field and the phone's keyboard.
     *
     * @param builtInEnabled        the launcher's keyboard is the selected on-screen keyboard
     * @param turnedOff             the user switched it off with the keyboard key; the search, a
     *                              request for one, brings it back
     * @param yieldsToHardwareKeyboard it is set to stay away while a physical keyboard is
     *                              attached, and one is: nothing on screen, keys go to the search
     */
    @NonNull
    public static DrawerSearch drawerSearch(boolean builtInEnabled, boolean turnedOff,
                                            boolean yieldsToHardwareKeyboard) {
        if (!builtInEnabled) return DrawerSearch.SYSTEM_ASK;
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
