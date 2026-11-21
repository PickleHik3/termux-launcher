package com.termux.app.terminal;

import android.view.View;

import androidx.annotation.Nullable;

import com.termux.app.x11.DisplayViewFocus;

/**
 * The one rule for handing the keys to a terminal pane. The pane controller's focus step after a
 * render and the activity's hand-back both use it.
 *
 * <p>A pane holds the keys only when it is attached and is the view its window's root finds
 * focused ({@link DisplayViewFocus#holdsKeys}). Its own focused flag is not enough. A pane
 * focused while it was out of the window still says it is focused, refuses a new request, and no
 * key reaches it. That stale claim is cleared before asking again. A pane the wall keeps off
 * screen refuses the request on its own (TerminalView#requestFocus). Its keys are handed over
 * when it comes back.
 */
public final class PaneKeys {

    private PaneKeys() {}

    /** Whether {@code pane} really holds the keys. */
    public static boolean holds(@Nullable View pane) {
        return DisplayViewFocus.holdsKeys(pane);
    }

    /**
     * Gives the keys to {@code pane} unless it already holds them, and says whether it holds them
     * now. {@code quiet} holds off the pane's focus listener for the request, so the hand-back
     * does not read as the user focusing the terminal and raise a keyboard.
     */
    public static boolean handTo(@Nullable View pane, boolean quiet) {
        if (pane == null) return false;
        return DisplayViewFocus.handBackTo(pane, () -> {
            View.OnFocusChangeListener listener = quiet ? pane.getOnFocusChangeListener() : null;
            if (quiet) pane.setOnFocusChangeListener(null);
            try {
                if (pane.isFocused()) pane.clearFocus();
                pane.requestFocus();
            } finally {
                if (quiet) pane.setOnFocusChangeListener(listener);
            }
        });
    }
}
