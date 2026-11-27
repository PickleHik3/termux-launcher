package com.termux.app.look;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The surfaces of Terl's look model. Only the control bar, the keyboard and windows may hold an
 * own value; the sheets (search, drawer, Workspace list, Overview, menus, notices) and the Home
 * widgets always wear the Look.
 */
public enum TerlSurface {
    CONTROL_BAR("control-bar", true),
    KEYBOARD("keyboard", true),
    WINDOWS("windows", true),
    SHEETS("sheets", false),
    HOME_WIDGETS("home-widgets", false);

    /** The name in preference keys and setting ids. */
    @NonNull public final String slug;
    /** Whether this surface may hold its own Opacity and Blur. */
    public final boolean acceptsOwn;

    TerlSurface(@NonNull String slug, boolean acceptsOwn) {
        this.slug = slug;
        this.acceptsOwn = acceptsOwn;
    }

    @Nullable
    static TerlSurface ofSlug(@NonNull String slug) {
        for (TerlSurface surface : values()) if (surface.slug.equals(slug)) return surface;
        return null;
    }
}
