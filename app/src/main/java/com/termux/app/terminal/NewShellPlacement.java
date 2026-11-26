package com.termux.app.terminal;

/**
 * Where a brand-new shell's window goes on the one wall (#357). Pure. Joining the place on screen
 * is asked for explicitly (the New session key binding, a Workspace's New session in Overview);
 * every other new shell (the + card's Workspace, an intent, a command) is a Workspace of its own,
 * landing right after the window in front.
 */
public final class NewShellPlacement {

    private NewShellPlacement() { }

    public enum Where {
        /** A new session of the place on screen, standing in front of it. */
        JOIN_PLACE,
        /** A Workspace of its own, right after the window in front. */
        NEW_PLACE,
        /** The Terminal has no window yet: the shell starts the wall's row. */
        START_ROW
    }

    /** {@code joinAsked}: the caller asked to join the place on screen; {@code hasFront}: there is one. */
    public static Where of(boolean joinAsked, boolean hasFront) {
        if (!hasFront) return Where.START_ROW;
        return joinAsked ? Where.JOIN_PLACE : Where.NEW_PLACE;
    }
}
