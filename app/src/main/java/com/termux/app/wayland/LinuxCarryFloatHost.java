package com.termux.app.wayland;

import androidx.annotation.NonNull;

/** What a carry asks of a Home page that can take a Float (#388);. */
public interface LinuxCarryFloatHost {
    /**
     * While a Home page is in front and can take a Float: the room its Floats live in (left,
     * top, right, bottom, window coordinates) into {@code out}; false for any other page.
     */
    default boolean floatRoom(@NonNull float[] out) { return false; }
    /** The bounds (left, top, right, bottom, window coordinates) the Float a drop at ({@code x}, {@code y}) takes. */
    default void floatBoundsAt(float x, float y, @NonNull float[] out) {}
}
