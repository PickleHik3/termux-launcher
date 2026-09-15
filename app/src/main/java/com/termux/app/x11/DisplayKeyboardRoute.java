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
