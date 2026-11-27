package com.termux.app.controlbar;

/**
 * The control bar's shape for the screen and what the user is doing (#427, ADR 0038): two rows in
 * portrait, one row while typing and always under the Terminal launcher mode, and a rail down one
 * side in landscape. Which zones each shape holds is {@link ControlBarRows#lines}; how thick it is,
 * {@link ControlBarPlacement#thicknessDp}.
 */
public enum ControlBarForm {
    /** Portrait at rest: where you are on top, the tools below. */
    TWO_ROWS,
    /** Portrait while typing, or under the Terminal launcher mode: place, tools, navigation, search. */
    ONE_ROW,
    /** Landscape: the one row's zones top to bottom, down one side of the screen. */
    RAIL;

    /**
     * @param landscape whether the screen is wider than tall
     * @param typing whether a keyboard is up (the launcher's or the phone's own)
     * @param terminalMode whether the launcher is used as a terminal (no Home)
     */
    public static ControlBarForm of(boolean landscape, boolean typing, boolean terminalMode) {
        if (landscape) return RAIL;
        return typing || terminalMode ? ONE_ROW : TWO_ROWS;
    }

    /** Whether the bar stands down a side, not along the bottom. */
    public boolean isRail() {
        return this == RAIL;
    }

    /** Whether the bar rides on the keyboard: a row does, frame by frame; a rail settles at rest. */
    public boolean ridesKeyboard() {
        return this != RAIL;
    }
}
