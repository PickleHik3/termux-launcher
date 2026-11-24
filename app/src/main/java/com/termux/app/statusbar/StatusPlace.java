package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Status bar card › Place: where the status bar stands, as its own strip or as a thin row inside
 * the top pane. One global value for every page and every layout; Home has no pane, so Home shows
 * the strip under either choice. Every install, new or existing, starts on the strip.
 */
public enum StatusPlace {
    STRIP("strip"),
    PANE("pane");

    @NonNull public final String value;

    StatusPlace(@NonNull String value) {
        this.value = value;
    }

    public static final StatusPlace DEFAULT = STRIP;

    @NonNull
    public static StatusPlace fromValue(@Nullable String value) {
        for (StatusPlace place : values()) if (place.value.equals(value)) return place;
        return DEFAULT;
    }

    /** Whether the status bar is the thin row in the top pane rather than its own strip. */
    public boolean inPane() {
        return this == PANE;
    }
}
