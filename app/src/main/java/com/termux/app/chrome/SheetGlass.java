package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

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
}
