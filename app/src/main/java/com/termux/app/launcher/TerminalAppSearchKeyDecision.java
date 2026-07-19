package com.termux.app.launcher;

import android.view.KeyEvent;

import androidx.annotation.NonNull;

/** Pure guard for the terminal keys that may be borrowed by an active app search. */
public final class TerminalAppSearchKeyDecision {

    public enum Action { PASS, PREVIOUS, NEXT, LAUNCH, EXIT }

    private TerminalAppSearchKeyDecision() {}

    @NonNull
    public static Action decide(boolean literalPrefixPresent, boolean alternateBufferActive,
                                int resultCount, int keyCode) {
        if (!literalPrefixPresent || alternateBufferActive) return Action.PASS;
        if (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK)
            return Action.EXIT;
        if (resultCount <= 0) return Action.PASS;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
                return Action.PREVIOUS;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return Action.NEXT;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return Action.LAUNCH;
            default:
                return Action.PASS;
        }
    }
}
