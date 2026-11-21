package com.termux.app.launcher.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Settings › Home › Page indicator: the page glyphs under the Home grid, or none. Glyphs show even
 * with one Home page, since they name the other pages too. The stored value stays {@code dots}.
 *
 * <p>Off gives the band back: the grid takes the height the glyphs stood in, so changing the
 * setting resizes the Home grid ({@code PageBarBand}).
 */
public enum HomePageIndicator {
    GLYPHS("dots"),
    OFF("off");

    @NonNull public final String value;

    HomePageIndicator(@NonNull String value) {
        this.value = value;
    }

    public static final HomePageIndicator DEFAULT = GLYPHS;

    @NonNull
    public static HomePageIndicator fromValue(@Nullable String value) {
        for (HomePageIndicator indicator : values()) if (indicator.value.equals(value)) return indicator;
        return DEFAULT;
    }

    /** Whether the glyph row shows, however many Home pages there are. */
    public boolean glyphsShown() {
        return this == GLYPHS;
    }
}
