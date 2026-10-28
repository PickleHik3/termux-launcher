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

    /**
     * The pages on screen, as the launcher tells the store (ADR 0015 §3): the Home page in front,
     * the Terminal page and every Home page.
     */
    public static PlaceLayoutStore.ShownPages pages(final int homeInFront, final int terminal,
                                                    final Integer... homes) {
        final java.util.List<Integer> ids = java.util.Arrays.asList(homes);
        return new PlaceLayoutStore.ShownPages() {
            @Override public int homePageInFront() { return homeInFront; }
            @Override public int terminalPage() { return terminal; }
            @androidx.annotation.NonNull @Override public java.util.List<Integer> homePageIds() {
                return ids;
            }
        };
    }

    /** Home page 2 in front, the Terminal page 1. */
    public static final int HOME_ID = 2;

    /** Home page 2's own layout: what the Home layout was before version 16. */
    public static final LayoutVariant HOME_PAGE =
        LayoutVariant.page(HOME_ID, com.termux.app.wall.WallPages.PageKind.HOME);

    /** {@code store} told Home page 2 is in front; the store, for chaining. */
    public static PlaceLayoutStore onHomePage(PlaceLayoutStore store) {
        store.setShownPages(pages(HOME_ID, 1, HOME_ID));
        return store;
    }

    /** {@code store} on Home page 2, which holds a page layout of its own. */
    public static PlaceLayoutStore withHomePageLayout(PlaceLayoutStore store) {
        onHomePage(store).setPageLayout(HOME_ID, com.termux.app.wall.WallPages.PageKind.HOME, true);
        return store;
    }
}
