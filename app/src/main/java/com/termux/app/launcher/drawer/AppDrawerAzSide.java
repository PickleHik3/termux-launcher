package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Which edge the drawer's letter strip sits on, or none. Right is how the drawer has always been
 * drawn, so a missing or unknown stored value reads as Right.
 */
public enum AppDrawerAzSide {
    RIGHT("right"),
    LEFT("left"),
    OFF("off");

    @NonNull public final String value;

    AppDrawerAzSide(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static AppDrawerAzSide fromValue(@Nullable String value) {
        for (AppDrawerAzSide side : values()) {
            if (side.value.equals(value)) return side;
        }
        return RIGHT;
    }

    /** Whether the strip is there at all. */
    public boolean shown() {
        return this != OFF;
    }

    /** Whether the strip's lean and tilt are mirrored, as they are on the left edge. */
    public boolean mirrored() {
        return this == LEFT;
    }

    /** Width the list gives up on the left edge for a strip of {@code stripPx}. */
    public int leftInsetPx(int stripPx) {
        return this == LEFT ? stripPx : 0;
    }

    /** Width the list gives up on the right edge for a strip of {@code stripPx}. */
    public int rightInsetPx(int stripPx) {
        return this == RIGHT ? stripPx : 0;
    }
}
