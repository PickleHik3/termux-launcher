package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** The four kinds of tiling-desktop window (ADR 0037): each is one tile of the same sort. */
public enum WindowKind {
    /** One shell; restored in its working directory. */
    SHELL("shell"),
    /** One Linux app; restored by its app id. */
    LINUX_APP("linux_app"),
    /** The X11 Display. */
    DISPLAY("display"),
    /** The Assistant. */
    ASSISTANT("assistant");

    /** The word the saved form writes. */
    @NonNull public final String key;

    WindowKind(@NonNull String key) {
        this.key = key;
    }

    /** The kind a saved form names, or null for one this build does not know. */
    @Nullable
    public static WindowKind fromKey(@Nullable String key) {
        for (WindowKind kind : values()) if (kind.key.equals(key)) return kind;
        return null;
    }
}
