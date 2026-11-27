package com.termux.app.look;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.FrostStops;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The shipped Looks as Terl wears them: Opacity and Blur, from the same numbers v1's presets ship
 * ({@code SurfacePresets}; ids are theirs, and a test holds the two together). The rest of a v1
 * preset (tint, depth, shine) is not part of this model yet.
 */
public final class TerlLooks {

    private TerlLooks() { }

    public static final String CLEAR = "minimal";
    public static final String FROSTED = "frost";
    public static final String TINTED = "stock";
    public static final String SOLID = "solid";
    public static final String LIQUID = "liquid";

    private static final TerlLookSource SHIPPED = () -> Collections.unmodifiableList(Arrays.asList(
        new TerlLookSource.Look(CLEAR, 10, FrostStops.blurDpAt(FrostStops.positionOf(FrostStops.CLEAR))),
        new TerlLookSource.Look(FROSTED, 60, FrostStops.blurDpAt(FrostStops.positionOf(FrostStops.MIST))),
        new TerlLookSource.Look(TINTED, 46, FrostStops.blurDpAt(FrostStops.positionOf(FrostStops.TINT))),
        new TerlLookSource.Look(SOLID, 92, 0),
        new TerlLookSource.Look(LIQUID, 8, FrostStops.blurDpAt(FrostStops.positionOf(FrostStops.CLEAR)))));

    @NonNull
    public static TerlLookSource shipped() {
        return SHIPPED;
    }
}
