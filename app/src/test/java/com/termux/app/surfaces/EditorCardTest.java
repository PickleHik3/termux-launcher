package com.termux.app.surfaces;

import com.termux.app.wall.PaneWallPage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


import org.junit.Test;

/** The editor's objects: what a door names, where each stands, which hold a value per half. */
public class EditorCardTest {

    @Test
    public void aDoorNamesACardByItsKey() {
        for (EditorCard card : EditorCard.values())
            assertEquals(card, EditorCard.forKey(card.key));
        assertEquals(EditorCard.DOCK, EditorCard.forKey(" Dock "));
        assertNull(EditorCard.forKey(null));
        assertNull(EditorCard.forKey("nothing"));
    }

    @Test
    public void theSettingsDeepLinksOldNamesStillOpenTheirCards() {
        assertEquals(EditorCard.TERMINAL, EditorCard.forKey("canvas"));
        assertEquals(EditorCard.TERMINAL, EditorCard.forKey("other"));
        assertEquals(EditorCard.STATUS, EditorCard.forKey("sessions"));
        assertEquals(EditorCard.KEYBOARD, EditorCard.forKey("keyboard"));
        assertEquals(EditorCard.STATUS, EditorCard.forKey("status"));
    }

    @Test
    public void homeStandsOnHomeAndTheTerminalOnTheWorkspace() {
        assertTrue(EditorCard.HOME.standsOn(PaneWallPage.WIDGETS));
        assertFalse(EditorCard.HOME.standsOn(PaneWallPage.TERMINAL));
        assertTrue(EditorCard.TERMINAL.standsOn(PaneWallPage.TERMINAL));
        assertFalse(EditorCard.TERMINAL.standsOn(PaneWallPage.WIDGETS));
        for (EditorCard card : new EditorCard[] {EditorCard.DOCK, EditorCard.STATUS,
            EditorCard.KEYBOARD, EditorCard.APPEARANCE}) {
            for (PaneWallPage half : PaneWallPage.values())
                assertTrue(card + " on " + half, card.standsOn(half));
        }
    }

    @Test
    public void onlyTheCardsHeldPerHalfOfferACopy() {
        assertTrue(EditorCard.DOCK.perHalf());
        assertTrue(EditorCard.STATUS.perHalf());
        assertTrue(EditorCard.KEYBOARD.perHalf());
        assertTrue(EditorCard.APPEARANCE.perHalf());
        assertFalse(EditorCard.HOME.perHalf());
        assertFalse(EditorCard.TERMINAL.perHalf());
    }
}
