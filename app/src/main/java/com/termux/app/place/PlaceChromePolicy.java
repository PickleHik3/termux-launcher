package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;
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

    /**
     * The alphabets row is its own index. With the apps row along the bottom the matches land in
     * that row; without it they ride a floating strip above the letters, so the switch is the only
     * thing that decides whether the row is there.
     */
    public static boolean azRowShown(@NonNull PlaceLayout layout) {
        return layout.azRowShown;
    }

    /**
     * The index standing on its own, with no apps row under it to fill: the scrub shows its matches
     * on a floating strip instead of in the row.
     */
    public static boolean azIndexStandsAlone(@NonNull PlaceLayout layout) {
        return azRowShown(layout) && !appsRowShown(layout);
    }

    /**
     * The edge the alphabets bar actually draws on. Its stored choice only applies while it stands
     * alone; riding under the apps row pins it to the bottom regardless of what is stored.
     */
    @NonNull
    public static Edge azBarEdge(@NonNull PlaceLayout layout) {
        return azIndexStandsAlone(layout) ? layout.azBarEdge : Edge.BOTTOM;
    }

    /**
     * The alphabets bar riding the dock's own row. Every place does but one that stands the bar on
     * another edge, where it gets a host and a glass sheet of its own and the dock never knows.
     */
    public static boolean azRowOnDock(@NonNull PlaceLayout layout) {
        return azRowShown(layout) && azBarEdge(layout) == Edge.BOTTOM;
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
        return appsRowShown(layout) || azRowOnDock(layout) || extraKeysRowShown(layout);
    }
}
