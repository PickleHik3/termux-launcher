package com.termux.app.terminal.inappkeyboard;

/** Activity-owned actions that do not belong in terminal input dispatch. */
public interface HostActions {

    void paste();

    void copySelection();

    /** Select all terminal scrollback so a following copy action has ordinary editor semantics. */
    default void selectAll() {}

    /**
     * Copy an active terminal selection, or the current prompt input when nothing is selected.
     *
     * @return whether the terminal input line should also be cleared with Ctrl+U
     */
    default boolean prepareCut() { return false; }

    void requestTextLayout();

    void requestNumericLayout();

    void requestGreekMathLayout();

    void requestForwardLayout();

    void requestBackwardLayout();

    void openKeyboardSettings();

    void hideKeyboard();

    void setComposePending(boolean pending);

    /** Toggle the keyboard view's Shift lock state and redraw it. */
    void toggleCapsLock();

    /** Reserved for a future app-owned suggestion source. */
    default void onSuggestionEntered(String text) {}

    /**
     * A swipe on an in-app keyboard key, named by the keyboard and resolved
     * against the gesture binding table.
     *
     * @param keyName   key identity, e.g. {@code space}
     * @param direction compass point, e.g. {@code north}
     * @return whether a binding ran, which suppresses the swiped-to key value
     */
    default boolean onKeyboardGesture(String keyName, String direction,
                                      boolean ctrl, boolean alt, boolean shift) {
        return false;
    }

    void debugLog(String message);
}
