package com.termux.app.controlbar;

import androidx.annotation.NonNull;

/**
 * The readings the bar's reading slot can show (#428, spec "The control bar"): the order a tap
 * cycles them in, and the words the remembered one is stored as ({@code bar.reading_kind}).
 */
public enum ReadingKind {
    CPU("cpu"),
    GPU("gpu"),
    RAM("ram"),
    BATTERY("battery"),
    WEATHER("weather"),
    MEDIA("media");

    /** What the slot shows until the user taps it: the one that is always there. */
    public static final ReadingKind DEFAULT = BATTERY;

    @NonNull private final String mKey;

    ReadingKind(@NonNull String key) {
        mKey = key;
    }

    /** The stored word. */
    @NonNull
    public String key() {
        return mKey;
    }

    /** The reading stored as {@code key}; {@link #DEFAULT} for anything else, null included. */
    @NonNull
    public static ReadingKind fromKey(String key) {
        for (ReadingKind kind : values()) if (kind.mKey.equals(key)) return kind;
        return DEFAULT;
    }
}
