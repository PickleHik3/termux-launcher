package com.termux.app.terminal;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** What the Terminal pages ask of the activity; the pages never see the activity itself. */
public interface TerminalPageHost {

    /** The window in front inside the Terminal cell, or null when there is none. */
    @Nullable TerminalPaneController.Window activeWindow();

    /** The one pane host every window's panes are drawn in. */
    @NonNull View paneHost();

    /**
     * Whether the active pane of the window in front really holds the keys (
     * {@link PaneKeys#holds}). Another window's pane, a chip or a stale focus claim do not count.
     */
    boolean activePaneHoldsKeys();

    /** Hands the keys to the active pane, once; false when nothing took them. */
    boolean handKeysToTerminal();

    /** Whether Back is mapped to Escape: the user's Escape, spent by the terminal. */
    boolean backIsEscape();

    /** Lowers the in-app keyboard when it is up; false when it was not. */
    boolean lowerKeyboard();

    /**
     * A Linux window stands in front of the Terminal's row and is full screen: Back leaves full
     * screen and stays on it. False when none is, so the chain goes on.
     */
    default boolean leaveLinuxFullscreen() { return false; }
}
