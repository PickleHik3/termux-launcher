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

    /**
     * The floor on a sheet's opacity, in percent, when no wallpaper stands under its tint (its blur
     * is 0, or there is no wallpaper to sample): only the tint then stands between the sheet's text
     * and the windows under it (Terl's search, the drawer and a window's menu over a terminal).
     * At 90 a terminal's text still read through (round 3); at 96 it still did (round 5): a sheet
     * passed 10 of 255 of the contrast under it, which reads on a flat dark sheet. The prototype's
     * sheets hide it with a blur of what is under them; here nothing blurs the windows, so a Terl
     * sheet stands opaque over them (TerlSheetBleedTest measures it).
     */
    public static final int BARE_OPACITY_FLOOR_PERCENT = 100;

    private SheetGlass() {}

    /**
     * A Terl sheet's or hint's opacity ({@code lookPercent} the Look's): at least
     * {@link #BARE_OPACITY_FLOOR_PERCENT} whatever blur stands under its tint. The blur is the
     * wallpaper's alone, never the windows' under the sheet, so the 60 % floor let their text
     * ghost through (round 4).
     */
    public static int terlSheetOpacityPercent(int lookPercent) {
        return terlOpacityPercent(lookPercent, false);
    }

    /** Global's opacity as a sheet draws it: Global's own above the floor, else the floor. */
    public static int opacityPercent(int globalPercent) {
        return Math.max(OPACITY_FLOOR_PERCENT, globalPercent);
    }

    /**
     * A Terl sheet's opacity (the sheets wear the Look's, {@code lookPercent}): at least
     * {@link #OPACITY_FLOOR_PERCENT} over the wallpaper's blur ({@code overWallpaper}), else at least
     * {@link #BARE_OPACITY_FLOOR_PERCENT}, so its text reads over the windows' text under it.
     */
    public static int terlOpacityPercent(int lookPercent, boolean overWallpaper) {
        return Math.max(overWallpaper ? OPACITY_FLOOR_PERCENT : BARE_OPACITY_FLOOR_PERCENT, lookPercent);
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
