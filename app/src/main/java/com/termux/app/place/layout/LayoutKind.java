package com.termux.app.place.layout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.WallPages.PageKind;

/**
 * The five layouts a page can wear (ADR 0020 §1, ADR 0022): one per page kind, and Terminal-minimal, which
 * the Terminal and the Display wear while minimal mode is on. Home and Assistant pages ignore minimal mode.
 */
public enum LayoutKind {
    HOME("home", "Home layout"),
    TERMINAL("terminal", "Terminal layout"),
    TERMINAL_MINIMAL("terminal_minimal", "Terminal minimal layout"),
    DISPLAY("display", "Display layout"),
    ASSISTANT("assistant", "Assistant layout");

    /** The document's key in the {@code layouts} file, and its name on the command line. */
    @NonNull public final String key;
    /** What the Appearance editor's title calls it. */
    @NonNull public final String title;

    LayoutKind(@NonNull String key, @NonNull String title) {
        this.key = key;
        this.title = title;
    }

    /** The layout a page of {@code kind} wears, minimal mode being {@code minimal}. */
    @NonNull
    public static LayoutKind of(@NonNull PageKind kind, boolean minimal) {
        switch (kind) {
            case HOME: return HOME;
            case DISPLAY: return minimal ? TERMINAL_MINIMAL : DISPLAY;
            case ASSISTANT: return ASSISTANT;
            case TERMINAL:
            default: return minimal ? TERMINAL_MINIMAL : TERMINAL;
        }
    }

    /** The page kind whose visibility and shipped values this layout starts from. */
    @NonNull
    public PageKind pageKind() {
        switch (this) {
            case HOME: return PageKind.HOME;
            case DISPLAY: return PageKind.DISPLAY;
            case ASSISTANT: return PageKind.ASSISTANT;
            default: return PageKind.TERMINAL;
        }
    }

    /** The kind named {@code key} ({@code home}, {@code terminal}, ...), or null. */
    @Nullable
    public static LayoutKind forKey(@Nullable String key) {
        for (LayoutKind kind : values()) {
            if (kind.key.equals(key)) return kind;
        }
        return null;
    }
}
