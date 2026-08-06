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

    /** Launch platform speech recognition; chooser selects a recognizer explicitly when requested. */
    default void requestVoiceTyping(boolean chooser) {}

    void setComposePending(boolean pending);

    /** Toggle the keyboard view's Shift lock state and redraw it. */
    void toggleCapsLock();

    /** Reserved for a future app-owned suggestion source. */
    default void onSuggestionEntered(String text) {}

    /**
     * The keyboard's modifier snapshot changed (latch, lock or release). Drives the keybind
     * hint popup the host shows while a Ctrl+Alt prefix is held.
     */
    default void onKeyboardModifiersChanged(TerminalModifiers modifiers) {}

    /**
     * A {@code tool:<id>} key fired. The id is a launcher registry tool name, written straight
     * into the layout file, so exposing a new action on a key needs no code on either side.
     *
     * @param toolId registry tool name, e.g. {@code app.command_palette}
     */
    default void runLauncherTool(String toolId) {}

    void debugLog(String message);
}
