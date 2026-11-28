package com.termux.app.look;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.FrostStops;
import com.termux.shared.termux.settings.preferences.GlassMaterial;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The shipped Looks as Terl wears them (ADR 0033's presets): Opacity and Blur, and the slab's
 * thickness, Shine and tint mix, from the same numbers v1's presets ship ({@code SurfacePresets};
 * ids are theirs, and a test holds the two together).
 */
public final class TerlLooks {

    private TerlLooks() { }

    public static final String CLEAR = "minimal";
    public static final String FROSTED = "frost";
    public static final String TINTED = "stock";
    public static final String SOLID = "solid";
    public static final String LIQUID = "liquid";

    private static final TerlLookSource SHIPPED = () -> Collections.unmodifiableList(Arrays.asList(
        new TerlLookSource.Look(CLEAR, 10, FrostStops.blurDpAt(FrostStops.positionOf(FrostStops.CLEAR)),
            GlassMaterial.REALISTIC_BEND_DP, GlassMaterial.SHINE_DEFAULT, 0),
        new TerlLookSource.Look(FROSTED, 60, FrostStops.blurDpAt(FrostStops.positionOf(FrostStops.MIST)),
            GlassMaterial.REALISTIC_BEND_DP, GlassMaterial.SHINE_DEFAULT, 0),
        new TerlLookSource.Look(TINTED, 46, FrostStops.blurDpAt(FrostStops.positionOf(FrostStops.TINT)),
            21, GlassMaterial.SHINE_DEFAULT, GlassMaterial.TINT_LOOK_PERCENT),
        new TerlLookSource.Look(SOLID, 92, 0, 0, 30, 0),
        new TerlLookSource.Look(LIQUID, 8, FrostStops.blurDpAt(FrostStops.positionOf(FrostStops.CLEAR)),
            GlassMaterial.bendForDepth(90), 85, 0)));

    @NonNull
    public static TerlLookSource shipped() {
        return SHIPPED;
    }
}
