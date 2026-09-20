package com.termux.app.statusbar;

/** The two forms the status pane rests in. Both persist. */
public enum TopStatusBarState {
    COMPACT,
    EXPANDED;

    public static TopStatusBarState fromCollapsedPreference(boolean collapsed) {
        return collapsed ? COMPACT : EXPANDED;
    }

    public boolean toCollapsedPreference() {
        return this == COMPACT;
    }
}
