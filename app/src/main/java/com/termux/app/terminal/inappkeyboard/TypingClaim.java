package com.termux.app.terminal.inappkeyboard;

import androidx.annotation.NonNull;

/**
 * Who the launcher keyboard's keys are typed into, when several things want them at once.
 *
 * <p>The keyboard has one interceptor slot, and every surface that borrows it used to put itself
 * in and later put {@code null} back, which is wrong whenever something else was holding it by
 * then: the drawer's search closing over a display window left the keyboard typing into the
 * terminal that is hidden behind. So nothing clears the slot. Every open and every close asks
 * here who owns it now, and the answer is the same whichever of them asks.
 *
 * <p>The order is the z order of the surfaces: the command palette, then a terminal sheet, then a
 * rename, then the app drawer's search, then the display window under all of them, and nobody
 * (the terminal itself) when none of those is up.
 */
public final class TypingClaim {

    /** The owner of the keyboard's keys. */
    public enum Owner {
        COMMAND_PALETTE, TERMINAL_SHEET, RENAME, APP_DRAWER,
        /** The display window's X bridge: the keys are X's. */
        DISPLAY,
        /** No interceptor: the keys go to the focused terminal. */
        NONE
    }

    private TypingClaim() {}

    /**
     * @param displayWindow whether the display window is the one the wall rests on, with a display
     *                      to type into. The one it last settled on: a swipe in flight keeps its
     *                      keys with the window it started from.
     */
    @NonNull
    public static Owner owner(boolean commandPalette, boolean terminalSheet, boolean rename,
                              boolean appDrawer, boolean displayWindow) {
        if (commandPalette) return Owner.COMMAND_PALETTE;
        if (terminalSheet) return Owner.TERMINAL_SHEET;
        if (rename) return Owner.RENAME;
        if (appDrawer) return Owner.APP_DRAWER;
        return displayWindow ? Owner.DISPLAY : Owner.NONE;
    }
}
