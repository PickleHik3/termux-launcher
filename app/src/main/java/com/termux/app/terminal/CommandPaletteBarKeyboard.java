package com.termux.app.terminal;

import androidx.annotation.NonNull;

/**
 * Which keyboard the control bar's search raises to type into the palette, and so which one it
 * puts away again when it closes: only one it raised itself. A keyboard already up stays up. Pure.
 */
public final class CommandPaletteBarKeyboard {

    /** What to raise. */
    public enum Raise {
        /** Nothing: a keyboard is up already, or the phone's own keys type. */
        NONE,
        /** The launcher's keyboard. */
        IN_APP,
        /** The system keyboard, typing through the terminal view (the overlay's code points). */
        SYSTEM
    }

    private CommandPaletteBarKeyboard() {}

    /**
     * @param inAppEnabled  the launcher's keyboard is on
     * @param inAppVisible  it is up
     * @param yieldsToHardware it stands aside for a hardware keyboard that is typing
     * @param hardwareKeys  the phone has keys of its own
     * @param systemImeVisible the system keyboard is up
     */
    @NonNull
    public static Raise raise(boolean inAppEnabled, boolean inAppVisible, boolean yieldsToHardware,
                              boolean hardwareKeys, boolean systemImeVisible) {
        if (inAppEnabled) return inAppVisible || yieldsToHardware ? Raise.NONE : Raise.IN_APP;
        if (hardwareKeys || systemImeVisible) return Raise.NONE;
        return Raise.SYSTEM;
    }
}
