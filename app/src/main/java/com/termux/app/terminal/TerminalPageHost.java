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

    /** Whether the keys already go to a terminal view. */
    boolean keysWithTerminal();

    /** Hands the keys to the active pane, once; false when nothing took them. */
    boolean handKeysToTerminal();

    /** Whether Back is mapped to Escape: the user's Escape, spent by the terminal. */
    boolean backIsEscape();

    /** Lowers the in-app keyboard when it is up; false when it was not. */
    boolean lowerKeyboard();
}
