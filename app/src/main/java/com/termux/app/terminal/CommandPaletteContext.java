package com.termux.app.terminal;

/**
 * Where the command palette was summoned, which decides the set of rows it offers.
 *
 * <p>The host passes it to {@link TerminalCommandPaletteController#show(CommandPaletteContext)};
 * every existing way in (the context menu, the chords, the space-bar swipe) keeps
 * {@link #TERMINAL}, so the Terminal's palette is unchanged until a caller asks for Home.
 */
public enum CommandPaletteContext {
    /** Over a terminal window: every action, the shell fallback on ⏎ with no match. */
    TERMINAL,
    /**
     * Over a Home page: the Home actions, apps, Linux apps, contacts, sessions and a jump row per
     * window and Home float, with the terminal-only actions left out
     * ({@link com.termux.launcherctl.LauncherToolRegistry.ToolMetadata#terminalOnly}).
     */
    HOME,
    /**
     * From the Terl control bar's search (and its leader binding and the Super key): All apps,
     * then Terminal, Linux apps and Android apps ({@link CommandPaletteTerlSet}), opened as the
     * desktop opens them, then the actions; it stands above the bar and wears Global's glass.
     */
    CONTROL_BAR
}
