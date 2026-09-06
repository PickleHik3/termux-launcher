package com.termux.app.fragments.settings.termux;

import androidx.annotation.Keep;

/**
 * Alias for a deep link or shortcut carrying the pre-phase-6 combined class name: the old
 * "Terminal &amp; status" page split into {@link TerminalPreferencesFragment} (panes, lazy mode,
 * full screen, Recents) and {@link StatusBarPreferencesFragment} (clock, CPU, memory, weather,
 * notifications). This resolves to the terminal half; nothing in the app links to this class name
 * any more (the root destination and every internal caller point at the new fragments directly),
 * so the choice only matters for an external shortcut or a rebroadcast Intent from before the
 * split.
 */
@Keep
public final class TerminalStatusPreferencesFragment extends TerminalPreferencesFragment {
}
