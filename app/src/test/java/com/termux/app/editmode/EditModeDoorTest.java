package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The doors' card names (#442): a card's name opens it, the rest open edit mode at rest. */
public class EditModeDoorTest {

    @Test
    public void aCardsNameOpensThatCard() {
        assertEquals(EditModeController.Card.HOME, EditModeDoor.cardOf("HOME"));
        assertEquals(EditModeController.Card.BAR, EditModeDoor.cardOf("BAR"));
    }

    @Test
    public void restAndTheWindowsCardsPlannedDoorOpenAtRest() {
        assertNull(EditModeDoor.cardOf(EditModeDoor.AT_REST));
        // The Windows card (#451) is not built: its door falls back to at rest until it is.
        assertNull(EditModeDoor.cardOf(EditModeDoor.WINDOWS));
    }

    @Test
    public void aSearchHitCarriesTheCardName() {
        String screen = EditModeDoor.screenOf(EditModeDoor.WINDOWS);
        assertEquals(true, EditModeDoor.isDoor(screen));
        assertEquals(EditModeDoor.WINDOWS, EditModeDoor.extraOf(screen));
    }
}
