package com.termux.app.place;

import androidx.annotation.NonNull;

/**
 * Which of the launcher's saved arrangements a layout belongs to. The Workspace has two, the
 * normal one and the one minimal mode stands in (CONTEXT.md); Home has one of its own, the Home
 * layout (ADR 0008), whose only elements are the dock's rows and the keyboard. Each is a whole
 * per-orientation layout, kept beside {@link PlaceOrientation} in {@link PlaceLayoutStore}'s keys.
 */
public enum LayoutVariant {
    NORMAL, MINIMAL, HOME;

    /** The Workspace variant the launcher stands in while minimal mode is {@code minimal}. */
    @NonNull
    public static LayoutVariant of(boolean minimal) {
        return minimal ? MINIMAL : NORMAL;
    }

    /**
     * Whether the Layout editor offers this element on this variant, and whether it may ever be
     * shown there. The Home layout keeps the dock's rows (the pinned apps and the A–Z index); the
     * status bar and the extra keys are the Workspace's alone. The keyboard is not an element with
     * a slot and is offered on every variant.
     */
    public boolean offers(@NonNull Element element) {
        return this != HOME || element == Element.APPS || element == Element.AZ;
    }

    /**
     * The key segment that scopes this variant's keys: none for the normal layout, whose keys are
     * the ones the launcher has always written, {@code minimal.} and {@code home.} for the others.
     */
    @NonNull
    String keyScope() {
        switch (this) {
            case MINIMAL: return "minimal.";
            case HOME: return "home.";
            default: return "";
        }
    }
}
