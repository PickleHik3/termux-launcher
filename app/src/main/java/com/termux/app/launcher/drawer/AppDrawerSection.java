package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The two pages of the Default drawer type, Apps and Categories. Unrelated to
 * {@link AppDrawerSectionIndex}, which is the A-Z index.
 */
public enum AppDrawerSection {
    APPS("apps"),
    CATEGORIES("categories");

    @NonNull public final String preferenceValue;

    AppDrawerSection(@NonNull String preferenceValue) {
        this.preferenceValue = preferenceValue;
    }

    /** Unknown, empty and future values read as Apps. */
    @NonNull
    public static AppDrawerSection fromPreference(@Nullable String value) {
        return CATEGORIES.preferenceValue.equals(value) ? CATEGORIES : APPS;
    }

    /** The section's place among the Default drawer's pages: Apps 0, Categories 1. */
    public int index() {
        return ordinal();
    }
}
