package com.termux.app.terminal.rename;

/**
 * What a rename is renaming, in the terminal's own hierarchy: a session holds windows, a window
 * holds panes.
 *
 * <p>This is the one place the three are enumerated, so the registry tools, the anchored editor and
 * a future name-suggesting backend all agree on the vocabulary instead of each inventing its own.
 */
public enum TerminalRenameTarget {
    /** A drawer row — the tmux-style session that owns windows. */
    SESSION("session"),
    /** A window-bar tab. */
    WINDOW("window"),
    /** One shell. */
    PANE("pane");

    /** Stable lower-case id, used as the editor's label and in tool arguments. */
    public final String id;

    TerminalRenameTarget(String id) {
        this.id = id;
    }
}
