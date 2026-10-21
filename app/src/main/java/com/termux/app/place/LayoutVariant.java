package com.termux.app.place;

import androidx.annotation.NonNull;

/**
 * Which of the launcher's saved arrangements a layout belongs to. The shared layout has two, the
 * normal one and the one minimal mode stands in (CONTEXT.md), worn by both halves of the wall
 * (ADR 0009); Home may hold one of its own, the opt-in Home layout, whose elements are the
 * status bar, the dock's rows and the keyboard. Each is a whole per-orientation layout, kept beside {@link
 * PlaceOrientation} in {@link PlaceLayoutStore}'s keys.
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
     * shown there: whether a page on it wears the element, as the dress answers ({@link
     * com.termux.app.chrome.DressResolver#wornOn}). The Home layout keeps the status bar and the
     * dock's rows (the pinned apps and the A–Z index); the extra keys are the Workspace's alone.
     * The keyboard is not an element with a slot and is offered on every variant.
     */
    public boolean offers(@NonNull Element element) {
        return com.termux.app.chrome.DressResolver.wornOn(this, element);
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
