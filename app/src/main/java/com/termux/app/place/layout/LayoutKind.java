package com.termux.app.place.layout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.WallPages.PageKind;

/**
 * The layouts a page can wear (ADR 0020 §1, ADR 0025): Home's, the Workspace's, and
 * Workspace-minimal, which the Workspace wears while minimal mode is on. The Workspace is every
 * page that is not Home: Terminal pages, Display pages and the Assistant page wear one layout
 * between them. Home ignores minimal mode.
 *
 * <p>{@link #DISPLAY} and {@link #ASSISTANT} are retired documents (ADR 0025): no page wears them
 * and nothing writes them. They stay so the {@code layouts} file's old keys still parse and a
 * downgrade finds what it left.
 */
public enum LayoutKind {
    HOME("home", "Home layout"),
    TERMINAL("terminal", "Workspace layout"),
    TERMINAL_MINIMAL("terminal_minimal", "Workspace minimal layout"),
    /** Retired (ADR 0025): Display pages wear {@link #TERMINAL}. */
    DISPLAY("display", "Display layout"),
    /** Retired (ADR 0025): the Assistant page wears {@link #TERMINAL}. */
    ASSISTANT("assistant", "Assistant layout");

    /** The document's key in the {@code layouts} file, and its name on the command line. */
    @NonNull public final String key;
    /** What the Appearance editor's title calls it. */
    @NonNull public final String title;

    LayoutKind(@NonNull String key, @NonNull String title) {
        this.key = key;
        this.title = title;
    }

    /**
     * The layout a page of {@code kind} wears, minimal mode being {@code minimal}: Home's on a Home
     * page, the Workspace's (or Workspace-minimal) on every other page (ADR 0025).
     */
    @NonNull
    public static LayoutKind of(@NonNull PageKind kind, boolean minimal) {
        if (kind == PageKind.HOME) return HOME;
        return minimal ? TERMINAL_MINIMAL : TERMINAL;
    }

    /** Whether this is a document no page wears any more ({@link #DISPLAY}, {@link #ASSISTANT}). */
    public boolean retired() {
        return this == DISPLAY || this == ASSISTANT;
    }

    /**
     * The document a read or write of this kind lands on: the Workspace's for a retired one,
     * else itself.
     */
    @NonNull
    public LayoutKind live() {
        return retired() ? TERMINAL : this;
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
