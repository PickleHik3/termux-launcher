package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** What the keyboard does when the wall lands on a place. Remembered per place. */
public enum KeyboardOnEnter {
    /** Come back the way the place was left. */
    AS_LEFT("as_left"),
    OPEN("open"),
    CLOSED("closed");

    @NonNull private final String mStorageValue;

    KeyboardOnEnter(@NonNull String storageValue) {
        mStorageValue = storageValue;
    }

    @NonNull
    public String storageValue() {
        return mStorageValue;
    }

    @NonNull
    public static KeyboardOnEnter parse(@Nullable String value, @NonNull KeyboardOnEnter fallback) {
        if (value != null) {
            for (KeyboardOnEnter onEnter : values()) {
                if (onEnter.mStorageValue.equals(value)) return onEnter;
            }
        }
        return fallback;
    }
}
