package com.termux.app.place;

import androidx.annotation.NonNull;

/**
 * Which of the launcher's two arrangements a layout belongs to: the normal one, and the one minimal
 * mode stands in (CONTEXT.md). Each is a whole per-orientation layout of its own, kept beside
 * {@link PlaceOrientation} in {@link PlaceLayoutStore}'s keys.
 */
public enum LayoutVariant {
    NORMAL, MINIMAL;

    /** The variant the launcher stands in while minimal mode is {@code minimal}. */
    @NonNull
    public static LayoutVariant of(boolean minimal) {
        return minimal ? MINIMAL : NORMAL;
    }

    /**
     * The key segment that scopes this variant's keys: none for the normal layout, whose keys are
     * the ones the launcher has always written, and {@code minimal.} for the other.
     */
    @NonNull
    String keyScope() {
        return this == MINIMAL ? "minimal." : "";
    }
}
