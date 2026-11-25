package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;

import org.junit.Test;

public class SideCardsTest {

    private static PlaceLayout layout(Edge status, RowPlacement apps, boolean az,
                                      RowPlacement keys) {
        return new PlaceLayout(status, apps, az, Edge.BOTTOM, keys, KeyboardMode.RESIZE,
            KeyboardForm.DOCKED, 4, 5);
    }

    /** A new install's Terminal: status on top, extra keys along the bottom, no apps row. */
    static final PlaceLayout DEFAULT_TERMINAL =
        layout(Edge.TOP, RowPlacement.HIDDEN, false, RowPlacement.BOTTOM);
    private static final PlaceLayout HOME = LayoutFixtures.HOME;

    /** #305: the apps row or A-Z beside the keys is no row of the keys' own card. */
    @Test public void anAppsRowBesideTheKeysIsNotTheKeysAlone() {
        assertFalse(SideCards.keysAlone(LayoutFixtures.TL16.window));
    }

    @Test public void anAzRowBesideTheKeysIsNotTheKeysAlone() {
        assertFalse(SideCards.keysAlone(LayoutFixtures.AZ_ON_DOCK.window));
        assertFalse(SideCards.keysAlone(
            layout(Edge.TOP, RowPlacement.HIDDEN, true, RowPlacement.BOTTOM)));
    }

    @Test public void theKeysAloneAreTheKeysRowsOwnCard() {
        assertTrue(SideCards.keysAlone(DEFAULT_TERMINAL));
        assertTrue(SideCards.keysAlone(LayoutFixtures.PONG.window));
    }

    /** #305: no keys row, keys on a side or under the keyboard: no keys card of their own. */
    @Test public void keysThatAreNotOverTheKeyboardAreNotTheKeysAlone() {
        assertFalse(SideCards.keysAlone(
            layout(Edge.TOP, RowPlacement.HIDDEN, false, RowPlacement.HIDDEN)));
        assertFalse(SideCards.keysAlone(
            layout(Edge.TOP, RowPlacement.HIDDEN, false, RowPlacement.LEFT)));
        assertFalse(SideCards.keysAlone(LayoutFixtures.KEYS_UNDER.window));
    }

    @Test public void theMarginIsTheTerminalsWhileApartAndTheFrontsOtherwise() {
        // Peek stands flush on Home; the Floating capsule keeps a 6dp gap (16 px at 2.625).
        assertEquals(16, SideCards.containerMarginPx(0, 16, true, true));
        assertEquals(16, SideCards.containerMarginPx(0, 16, true, false));
        assertEquals(0, SideCards.containerMarginPx(0, 16, false, true));
        assertEquals(16, SideCards.containerMarginPx(0, 16, false, false));
    }

    @Test public void homesSideStandsLowerByTheDifferenceWhileApart() {
        assertEquals(16, SideCards.homeSideDropPx(0, 16, true));
        assertEquals(0, SideCards.homeSideDropPx(16, 16, true));
        assertEquals(0, SideCards.homeSideDropPx(0, 16, false));
    }

    @Test public void theHostIsDressedAsHomeWhileApartWhicheverPageIsInFront() {
        for (com.termux.app.page.PageKind front : com.termux.app.page.PageKind.values()) {
            assertEquals(com.termux.app.page.PageKind.HOME, SideCards.hostDressPage(true, front));
            assertEquals(front, SideCards.hostDressPage(false, front));
        }
    }
}
