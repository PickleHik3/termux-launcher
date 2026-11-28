package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * Home's A–Z letters under Terl ({@code home.az_edge}): off by default, or along the left, right
 * or bottom edge. Terl's own value, so v1's stored alphabets never decide what Terl's Home shows.
 *
 * <p>Pure: the stored value goes in, the edge comes out.
 */
public final class TerlHomeAz {

    public static final String OFF = "off";
    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    public static final String BOTTOM = "bottom";

    private TerlHomeAz() {}

    /** The edge {@code stored} names; null for off, and for anything this version does not know. */
    @Nullable
    public static Edge edgeOf(@Nullable String stored) {
        if (LEFT.equals(stored)) return Edge.LEFT;
        if (RIGHT.equals(stored)) return Edge.RIGHT;
        if (BOTTOM.equals(stored)) return Edge.BOTTOM;
        return null;
    }

    /** The value a chip writes: one of the four, anything else is off. */
    @NonNull
    public static String valueOf(@Nullable String chip) {
        return LEFT.equals(chip) || RIGHT.equals(chip) || BOTTOM.equals(chip) ? chip : OFF;
    }
}
