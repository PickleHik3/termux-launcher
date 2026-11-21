package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.SideCards.Refusal;

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

    @Test public void aNewInstallsTerminalStandsApart() {
        assertNull(SideCards.refusal(HOME, DEFAULT_TERMINAL, true, false));
        assertTrue(SideCards.apart(HOME, DEFAULT_TERMINAL, true, false));
    }

    @Test public void pongsAppsUnderTheKeyboardStandApart() {
        assertNull(SideCards.refusal(LayoutFixtures.PONG.home, LayoutFixtures.PONG.window, true,
            false));
    }

    @Test public void dockedRefuses() {
        assertEquals(Refusal.DOCKED, SideCards.refusal(HOME, DEFAULT_TERMINAL, false, false));
        assertFalse(SideCards.apart(HOME, DEFAULT_TERMINAL, false, false));
    }

    @Test public void aTerminalWithoutKeysRefuses() {
        assertEquals(Refusal.NO_KEYS_ROW, SideCards.refusal(HOME,
            layout(Edge.TOP, RowPlacement.HIDDEN, false, RowPlacement.HIDDEN), true, false));
    }

    @Test public void keysOnASideRefuse() {
        assertEquals(Refusal.NO_KEYS_ROW, SideCards.refusal(HOME,
            layout(Edge.TOP, RowPlacement.HIDDEN, false, RowPlacement.LEFT), true, false));
    }

    @Test public void keysUnderTheKeyboardRefuse() {
        PlaceLayout under = DEFAULT_TERMINAL.withSlot(Element.EXTRA_KEYS,
            new Slot(false, Edge.BOTTOM, 0, true));
        assertEquals(Refusal.KEYS_NOT_OVER_KEYBOARD, SideCards.refusal(HOME, under, true, false));
    }

    @Test public void anAppsRowBesideTheKeysRefuses() {
        assertEquals(Refusal.ROWS_BESIDE_KEYS, SideCards.refusal(LayoutFixtures.TL16.home,
            LayoutFixtures.TL16.window, true, false));
    }

    @Test public void anAzRowBesideTheKeysRefuses() {
        assertEquals(Refusal.ROWS_BESIDE_KEYS, SideCards.refusal(LayoutFixtures.AZ_ON_DOCK.home,
            LayoutFixtures.AZ_ON_DOCK.window, true, false));
        assertEquals(Refusal.ROWS_BESIDE_KEYS, SideCards.refusal(HOME,
            layout(Edge.TOP, RowPlacement.HIDDEN, true, RowPlacement.BOTTOM), true, false));
    }

    @Test public void aStatusBarOnThePlankRefuses() {
        assertEquals(Refusal.STATUS_ON_PLANK,
            SideCards.refusal(HOME, DEFAULT_TERMINAL, true, true));
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
