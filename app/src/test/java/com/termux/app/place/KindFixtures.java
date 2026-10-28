package com.termux.app.place;

/** Test setup: the arrangement tests were written when the dock showed on every window. */
public final class KindFixtures {

    private KindFixtures() {}

    /** Shows the apps row and the A-Z index on the Terminal and Display kinds, in both orientations, where a store has not stored one (an upgrade's migration has). */
    public static PlaceLayoutStore dockOnTheTerminal(PlaceLayoutStore store) {
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            for (com.termux.app.wall.WallPages.PageKind kind : new com.termux.app.wall.WallPages.PageKind[] {
                    com.termux.app.wall.WallPages.PageKind.TERMINAL,
                    com.termux.app.wall.WallPages.PageKind.DISPLAY}) {
                for (Element element : new Element[] {Element.APPS, Element.AZ}) {
                    if (!store.hasKindKey(kind, orientation, element))
                        store.setKindShows(kind, orientation, element, true);
                }
            }
        }
        return store;
    }
}
