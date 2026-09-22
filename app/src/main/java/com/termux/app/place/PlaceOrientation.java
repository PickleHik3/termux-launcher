package com.termux.app.place;

import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** The two orientations a place is arranged for. */
public enum PlaceOrientation {
    PORTRAIT, LANDSCAPE;

    @NonNull
    public String storageValue() {
        return this == LANDSCAPE ? "landscape" : "portrait";
    }

    @NonNull
    public static PlaceOrientation of(@Nullable Configuration configuration) {
        return configuration != null
            && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            ? LANDSCAPE : PORTRAIT;
    }
}
