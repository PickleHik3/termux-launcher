package com.termux.app.statusbar;

/** The persisted two-state pane plus the transient FULL presentation. */
public enum TopStatusBarState {
    COMPACT,
    EXPANDED,
    FULL;

    public static TopStatusBarState fromCollapsedPreference(boolean collapsed) {
        return collapsed ? COMPACT : EXPANDED;
    }

    public boolean toCollapsedPreference() {
        if (this == FULL) throw new IllegalStateException("FULL is not a persisted preference");
        return this == COMPACT;
    }

    public boolean allowsNormalSwipe() { return this != FULL; }
}
