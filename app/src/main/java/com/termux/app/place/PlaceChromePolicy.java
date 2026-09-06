package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.RowPlacement;

/**
 * Turns a resolved {@link PlaceLayout} into the handful of booleans the chrome is built from: which
 * dock rows are shown, whether the pinned apps stand in a rail, and whether the extra keys stand in
 * a column. Pure, so the derivation can be read and tested in one place instead of being spelled
 * out again at every call site.
 */
public final class PlaceChromePolicy {

    private PlaceChromePolicy() {}

    /** The pinned apps as the horizontal dock row. */
    public static boolean appsRowShown(@NonNull PlaceLayout layout) {
        return layout.appsRow == RowPlacement.BOTTOM;
    }

    /** The alphabets row rides on the apps row: on an edge or hidden, it has nothing to index. */
    public static boolean azRowShown(@NonNull PlaceLayout layout) {
        return layout.azRowShown && appsRowShown(layout);
    }

    /** The pinned apps as a column on a screen edge — the rail. */
    public static boolean appsRailShown(@NonNull PlaceLayout layout) {
        return layout.appsRow.isOnSide();
    }

    public static boolean appsRailOnRight(@NonNull PlaceLayout layout) {
        return layout.appsRow.isOnRight();
    }

    /** The extra keys as the bottom row; a column collapses the row the same way off does. */
    public static boolean extraKeysRowShown(@NonNull PlaceLayout layout) {
        return layout.extraKeys == RowPlacement.BOTTOM;
    }

    public static boolean extraKeysColumnShown(@NonNull PlaceLayout layout) {
        return layout.extraKeys.isOnSide();
    }

    public static boolean extraKeysColumnOnRight(@NonNull PlaceLayout layout) {
        return layout.extraKeys.isOnRight();
    }

    /** Whether anything at all lands on the dock, which is what decides it is drawn. */
    public static boolean dockShown(@NonNull PlaceLayout layout) {
        return appsRowShown(layout) || azRowShown(layout) || extraKeysRowShown(layout);
    }
}
