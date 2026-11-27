package com.termux.app.look;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** The two numbers a Look sets and a surface may own. Corners are Global's alone, not here. */
public enum TerlProperty {
    OPACITY("opacity", 0, 100),
    BLUR("blur", 0, 48);

    @NonNull public final String slug;
    public final int min;
    public final int max;

    TerlProperty(@NonNull String slug, int min, int max) {
        this.slug = slug;
        this.min = min;
        this.max = max;
    }

    public int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }

    @Nullable
    static TerlProperty ofSlug(@NonNull String slug) {
        for (TerlProperty property : values()) if (property.slug.equals(slug)) return property;
        return null;
    }
}
