package com.termux.app.wayland;

/** What the controller asks of the activity for the panes of a split. */
public interface LinuxPaneHost {
    /** Pane window {@code id} was destroyed or taken out: its pane leaves the split, which collapses. */
    default void removePane(long id) {}
    /** The pane of window {@code id} took the finger: the split's focus moves to it. */
    default void paneTouched(long id) {}
}
