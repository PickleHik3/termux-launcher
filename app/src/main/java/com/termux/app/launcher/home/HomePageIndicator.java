package com.termux.app.launcher.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Settings › Home › Page indicator: the small dots under the Home grid, or none. Dots is Home as
 * it was, and they still show only once there is more than one page.
 *
 * <p>The dots' band under the grid stays whichever is chosen: it is part of the grid's height,
 * so turning the dots off never resizes every widget on every page.
 */
public enum HomePageIndicator {
    DOTS("dots"),
    OFF("off");

    @NonNull public final String value;

    HomePageIndicator(@NonNull String value) {
        this.value = value;
    }

    public static final HomePageIndicator DEFAULT = DOTS;

    @NonNull
    public static HomePageIndicator fromValue(@Nullable String value) {
        for (HomePageIndicator indicator : values()) if (indicator.value.equals(value)) return indicator;
        return DEFAULT;
    }

    /** Whether the dots show for {@code pageCount} Home pages. */
    public boolean dotsShown(int pageCount) {
        return this == DOTS && pageCount > 1;
    }
}
