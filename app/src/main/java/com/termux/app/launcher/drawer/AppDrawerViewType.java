package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** The app drawer's persisted presentation mode. */
public enum AppDrawerViewType {
    VERTICAL("vertical"),
    HORIZONTAL("horizontal");

    @NonNull public final String preferenceValue;

    AppDrawerViewType(@NonNull String preferenceValue) {
        this.preferenceValue = preferenceValue;
    }

    /** Unknown, empty and future values preserve the shipped vertical drawer. */
    @NonNull
    public static AppDrawerViewType fromPreference(@Nullable String value) {
        if (HORIZONTAL.preferenceValue.equals(value)) return HORIZONTAL;
        return VERTICAL;
    }
}
