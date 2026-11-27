package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How a Workspace arranges its tiles. The stack model only records which one a Workspace uses;
 * the geometry is the layout policies' (#416 Scrolling, #417 Dwindle).
 */
public enum WorkspaceLayout {
    /** Columns side by side (the default for a new Workspace). */
    SCROLLING("scrolling"),
    /** Each new window splits the focused one; too small a split makes it a tab. */
    DWINDLE("dwindle");

    /** The word the saved form writes. */
    @NonNull public final String key;

    WorkspaceLayout(@NonNull String key) {
        this.key = key;
    }

    /** The layout a saved form names, falling back to Scrolling. */
    @NonNull
    public static WorkspaceLayout fromKey(@Nullable String key) {
        for (WorkspaceLayout layout : values()) if (layout.key.equals(key)) return layout;
        return SCROLLING;
    }
}
