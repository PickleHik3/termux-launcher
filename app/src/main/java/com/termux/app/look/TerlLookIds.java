package com.termux.app.look;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The setting ids of Terl's look model, for the settings registry and terl.conf (ADR 0041):
 * {@code look} (the Look), {@code look.corners}, {@code look.custom.<property>} and, for a surface
 * that accepts one, {@code look.<surface>.<property>} (an own value; {@code follow} clears it).
 */
public final class TerlLookIds {

    private TerlLookIds() { }

    public static final String LOOK = "look";
    public static final String CORNERS = "look.corners";

    @NonNull
    public static String of(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
        return "look." + surface.slug + "." + property.slug;
    }

    @NonNull
    public static String ofCustom(@NonNull TerlProperty property) {
        return "look.custom." + property.slug;
    }

    /** The surface and property an own-value id names, or null for any other id. */
    @Nullable
    public static TerlLookStore.OwnValue parseOwn(@NonNull String id) {
        String[] parts = id.split("\\.");
        if (parts.length != 3 || !parts[0].equals("look")) return null;
        TerlSurface surface = TerlSurface.ofSlug(parts[1]);
        TerlProperty property = TerlProperty.ofSlug(parts[2]);
        if (surface == null || property == null || !surface.acceptsOwn) return null;
        return new TerlLookStore.OwnValue(surface, property, 0);
    }

    /** The property a Custom id names, or null. */
    @Nullable
    public static TerlProperty parseCustom(@NonNull String id) {
        if (!id.startsWith("look.custom.")) return null;
        return TerlProperty.ofSlug(id.substring("look.custom.".length()));
    }
}
