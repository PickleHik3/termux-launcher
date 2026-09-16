package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * Turns a resolved {@link PlaceLayout} into the handful of booleans the chrome is built from: which
 * dock rows are shown, whether the pinned apps stand in a rail, and whether the extra keys stand in
 * a column. Pure, so the derivation can be read and tested in one place instead of being spelled
 * out again at every call site.
 *
 * <p>Every answer here is read off {@link EdgeStackPolicy}, which is the whole truth about what
 * stands where. Nothing derives a placement from the old three-way row model any more, which is
 * what used to fold a bar standing on the top edge into the bottom one.
 */
public final class PlaceChromePolicy {

    private PlaceChromePolicy() {}

    /** Whether the pinned apps are on screen at all, wherever they stand. */
    public static boolean appsShown(@NonNull PlaceLayout layout) {
        return EdgeStackPolicy.isShown(layout, Element.APPS);
    }

    /** The edge the pinned apps stand on. Only meaningful while {@link #appsShown} holds. */
    @NonNull
    public static Edge appsEdge(@NonNull PlaceLayout layout) {
        return EdgeStackPolicy.edgeOf(layout, Element.APPS);
    }

    /** The pinned apps as the dock's own horizontal row along the bottom. */
    public static boolean appsRowShown(@NonNull PlaceLayout layout) {
        return appsShown(layout) && appsEdge(layout) == Edge.BOTTOM;
    }

    /**
     * The index riding the pinned apps row: the two of them on the same edge, with that row lying
     * down.
     *
     * <p>Two things have to hold. The row has to lie along the top or the bottom, since a rail
     * standing in a column has no slots along the index's own axis to fill with matches. And the
     * index has to stand on that same edge itself — moving only the row never drags the index
     * across the screen after it, and an index the user put on another edge keeps a bar of its own
     * there. Sharing the edge is what riding <em>is</em>, so the index's stored slot is never
     * overridden and {@link #azBarEdge} is simply what it says.
     */
    public static boolean azRidesAppsRow(@NonNull PlaceLayout layout) {
        if (!appsShown(layout) || !azRowShown(layout)) return false;
        Edge apps = appsEdge(layout);
        return !apps.isOnSide() && layout.slot(Element.AZ).edge == apps;
    }

    /**
     * The alphabets row is its own index. With an apps row to ride the matches land in that row;
     * without one they ride a floating strip above the letters, so the switch is the only thing
     * that decides whether the row is there.
     */
    public static boolean azRowShown(@NonNull PlaceLayout layout) {
        return EdgeStackPolicy.isShown(layout, Element.AZ);
    }

    /**
     * The index standing on its own, with no apps row to fill: the scrub shows its matches on a
     * floating strip instead of in the row. A rail leaves it standing alone, which is what
     * landscape has always done, and so does an edge of its own.
     */
    public static boolean azIndexStandsAlone(@NonNull PlaceLayout layout) {
        return azRowShown(layout) && !azRidesAppsRow(layout);
    }

    /**
     * The edge the alphabets bar draws on: the one it is stored on, always. Riding the pinned apps
     * row is the two of them sharing an edge rather than the row carrying the index around, so
     * there is nothing left here to override — a row moved to another edge leaves the index where
     * the user left it, and the index joins it again by being dropped on the same edge.
     */
    @NonNull
    public static Edge azBarEdge(@NonNull PlaceLayout layout) {
        return layout.slot(Element.AZ).edge;
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
        return appsShown(layout) && appsEdge(layout).isOnSide();
    }

    public static boolean appsRailOnRight(@NonNull PlaceLayout layout) {
        return appsShown(layout) && appsEdge(layout) == Edge.RIGHT;
    }

    /** Whether the extra keys are on screen at all, wherever they stand. */
    public static boolean extraKeysShown(@NonNull PlaceLayout layout) {
        return EdgeStackPolicy.isShown(layout, Element.EXTRA_KEYS);
    }

    /** The edge the extra keys stand on. Only meaningful while {@link #extraKeysShown} holds. */
    @NonNull
    public static Edge extraKeysEdge(@NonNull PlaceLayout layout) {
        return EdgeStackPolicy.edgeOf(layout, Element.EXTRA_KEYS);
    }

    /** The extra keys as the dock's bottom row — the one the toolbar pager holds. */
    public static boolean extraKeysRowShown(@NonNull PlaceLayout layout) {
        return extraKeysShown(layout) && extraKeysEdge(layout) == Edge.BOTTOM;
    }

    public static boolean extraKeysColumnShown(@NonNull PlaceLayout layout) {
        return extraKeysShown(layout) && extraKeysEdge(layout).isOnSide();
    }

    public static boolean extraKeysColumnOnRight(@NonNull PlaceLayout layout) {
        return extraKeysShown(layout) && extraKeysEdge(layout) == Edge.RIGHT;
    }

    /** Whether anything at all lands on the dock, which is what decides it is drawn. */
    public static boolean dockShown(@NonNull PlaceLayout layout) {
        return appsRowShown(layout) || azRowOnDock(layout) || extraKeysRowShown(layout);
    }
}
