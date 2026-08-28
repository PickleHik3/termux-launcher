package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * The material macro's curves: one (family, intensity) point in, the Base glass triple out.
 *
 * <p>Three families, each a straight line through blur/opacity/grain space. Glass passes through
 * the shipped Base triple (8 / 34 / 18) at intensity 50, so the shipped look reads as a point on
 * the default curve rather than as "Custom". The macro never runs the other way: a triple the
 * curves cannot reproduce simply is Custom, and the editor leaves it alone until the user touches
 * the macro again.
 *
 * <p>Pure arithmetic, no {@code Context}, so the curves are testable on their own.
 */
public final class SurfaceMaterials {

    private SurfaceMaterials() {}

    /** Index of blur in a triple, in dp. */
    public static final int BLUR = 0;
    /** Index of opacity in a triple, in percent. */
    public static final int OPACITY = 1;
    /** Index of grain in a triple, in percent. */
    public static final int GRAIN = 2;

    /**
     * The Base triple a family produces at {@code intensity}. An unknown family reads as glass,
     * the default, so a stored value from a newer build degrades to something sensible.
     */
    @NonNull
    public static int[] triple(@Nullable String material, int intensity) {
        int t = Math.max(0, Math.min(100, intensity));
        if (TERMUX_APP.SURFACE_MATERIAL_SOLID.equals(material))
            return clampTriple(0, Math.round(55f + 0.45f * t), 0);
        if (TERMUX_APP.SURFACE_MATERIAL_FROST.equals(material))
            return clampTriple(Math.round(12f + 0.14f * t), Math.round(45f + 0.35f * t),
                Math.round(0.08f * t));
        return clampTriple(Math.round(2f + 0.12f * t), Math.round(14f + 0.40f * t),
            Math.round(6f + 0.24f * t));
    }

    private static int[] clampTriple(int blur, int opacity, int grain) {
        return new int[] {
            Math.max(0, Math.min(30, blur)),
            Math.max(0, Math.min(100, opacity)),
            Math.max(0, Math.min(100, grain)),
        };
    }
}
