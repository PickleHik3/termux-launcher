package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


import org.junit.Test;
import com.termux.app.page.PageKind;

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
        assertTrue(EditorCard.HOME.standsOn(PageKind.HOME));
        assertFalse(EditorCard.HOME.standsOn(PageKind.TERMINAL));
        assertTrue(EditorCard.TERMINAL.standsOn(PageKind.TERMINAL));
        assertFalse(EditorCard.TERMINAL.standsOn(PageKind.HOME));
        for (EditorCard card : new EditorCard[] {EditorCard.DOCK, EditorCard.STATUS,
            EditorCard.KEYBOARD, EditorCard.APPEARANCE}) {
            for (PageKind half : PageKind.values())
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
