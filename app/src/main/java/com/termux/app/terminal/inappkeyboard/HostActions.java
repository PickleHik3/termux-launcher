package com.termux.app.terminal.inappkeyboard;

/** Activity-owned actions that do not belong in terminal input dispatch. */
public interface HostActions {

    void paste();

    void copySelection();

    /** Copy the whole visible terminal screen to the clipboard — the terminal "select all". */
    default void copyScreen() {}

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

    void debugLog(String message);
}
