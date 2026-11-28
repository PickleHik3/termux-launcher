package com.termux.app.chrome;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

/**
 * The glass the sheets wear (ADR 0031): the app drawer, folders, anchored and long-press menus,
 * the command palette and notices. They hold no look of their own. They wear Global's glass, the
 * look grid's Global cell, with one floor on Opacity so text over a busy page stays readable when
 * Global is very clear. Blur, Tint, Grain, Corners and the rest follow Global as they are.
 *
 * <p>The Home float (the floating terminal window) is not a sheet: it wears {@link #global} with
 * no floor.</p>
 */
public final class SheetGlass {

    /**
     * The floor on a sheet's opacity, in percent: the one place it is set, read by every sheet.
     * PROVISIONAL: the developer picks the real number on pong.
     */
    public static final int OPACITY_FLOOR_PERCENT = 60;

    private SheetGlass() {}

    /**
     * A Terl sheet's or hint's opacity ({@code lookPercent} the Look's): the sheets' floor
     * ({@link #OPACITY_FLOOR_PERCENT}, ADR 0031 section 2), as v1's sheets wear it. A Terl sheet
     * records what is beneath it, windows included, and blurs it (ADR 0021, 0029; the glass
     * service through {@link TerlGlassWear}), so their text does not read through at that floor.
     */
    public static int terlSheetOpacityPercent(int lookPercent) {
        return opacityPercent(lookPercent);
    }

    /**
     * Whether a glass at {@code blurDp} and {@code opacityPercent} samples the wallpaper's blur:
     * only when it blurs and its tint lets some of it through. An opaque Terl sheet shows none of
     * it, so none is sampled or held (round 5 review P3-1).
     */
    public static boolean samplesBackdrop(int blurDp, int opacityPercent) {
        return blurDp > 0 && opacityPercent < 100;
    }

    /**
     * Terl's glass spec for a surface (the activity's {@code terlGlass}): Global's material at the
     * surface's {@code blurDp} and {@code opacityPercent} (a sheet's already floored,
     * {@link #terlSheetOpacityPercent}), Global's rim, cut to its own outline.
     */
    @NonNull
    public static GlassStack.Spec terlSpec(@NonNull ElementGlass global, float radiusPx,
                                           @Nullable GlassRefraction.Look fancier, int blurDp,
                                           int opacityPercent) {
        return global.spec(radiusPx, fancier).withBlur(blurDp).withTintAlpha(opacityPercent / 100f)
            .withRim(!global.rimOff()).withOwnOutline(true);
    }

    /** Global's opacity as a sheet draws it: Global's own above the floor, else the floor. */
    public static int opacityPercent(int globalPercent) {
        return Math.max(OPACITY_FLOOR_PERCENT, globalPercent);
    }

    /** Global's glass as it is, from {@code grid}: no floor (the Home float's). */
    @NonNull
    public static ElementGlass global(@NonNull LookGrid grid) {
        return ElementGlass.resolve(grid, null, -1);
    }

    /** {@link #global(LookGrid)} over the live store; the shipped look when there is none. */
    @NonNull
    public static ElementGlass global(@Nullable TermuxAppSharedPreferences preferences) {
        return ElementGlass.of(preferences, null, -1);
    }

    /** What a sheet wears: Global's glass with its opacity at or above the floor. */
    @NonNull
    public static ElementGlass of(@NonNull LookGrid grid) {
        return global(grid).withOpacityFloor(OPACITY_FLOOR_PERCENT);
    }

    /** {@link #of(LookGrid)} over the live store; the shipped look when there is none. */
    @NonNull
    public static ElementGlass of(@Nullable TermuxAppSharedPreferences preferences) {
        return global(preferences).withOpacityFloor(OPACITY_FLOOR_PERCENT);
    }

    /**
     * A sheet's whole-surface material from {@code glass}: {@link #of} at its tint opacity and grain,
     * {@code cornerRadiusPx} and the rim when {@code rim} and Global keeps one. A screen that covers
     * the window (Overview) passes 0 and false. With no store it wears the floor and the shipped grain.
     */
    @NonNull
    public static Drawable surface(@NonNull GlassSurfaceFactory glass,
                                   @Nullable TermuxAppSharedPreferences preferences,
                                   float cornerRadiusPx, boolean rim) {
        ElementGlass sheets = of(preferences);
        float alpha = preferences != null ? sheets.tintAlpha() : OPACITY_FLOOR_PERCENT / 100f;
        int grain = preferences != null ? sheets.grainPercent
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN;
        return glass.forElement(sheets).surface(alpha, 0f, 1f, false, grain, cornerRadiusPx,
            rim && !sheets.rimOff(), null, sheets.tintPercent);
    }
}
