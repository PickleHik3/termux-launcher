package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.termux.app.look.TerlLookIds;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import org.junit.Test;

public class EditModeControlsTest {

    @Test
    public void aSurfacesOwnValuesLiveOnItsCard() {
        assertEquals(EditModeController.Card.BAR,
            EditModeControls.cardOf(TerlLookIds.of(TerlSurface.CONTROL_BAR, TerlProperty.OPACITY)));
        assertEquals(EditModeController.Card.KEYBOARD,
            EditModeControls.cardOf(TerlLookIds.of(TerlSurface.KEYBOARD, TerlProperty.BLUR)));
        assertEquals(EditModeController.Card.WINDOWS,
            EditModeControls.cardOf(TerlLookIds.of(TerlSurface.WINDOWS, TerlProperty.OPACITY)));
    }

    @Test
    public void theLookCornersTintAndCustomLiveOnTheGlobalCard() {
        assertEquals(EditModeController.Card.GLOBAL, EditModeControls.cardOf(TerlLookIds.LOOK));
        assertEquals(EditModeController.Card.GLOBAL, EditModeControls.cardOf(TerlLookIds.CORNERS));
        assertEquals(EditModeController.Card.GLOBAL, EditModeControls.cardOf(TerlLookIds.TINT));
        assertEquals(EditModeController.Card.GLOBAL,
            EditModeControls.cardOf(TerlLookIds.ofCustom(TerlProperty.BLUR)));
    }

    @Test
    public void otherIdsAreNoControl() {
        assertNull(EditModeControls.cardOf("windows.gap"));
        assertNull(EditModeControls.cardOf("look.sheets.opacity"));
    }

    @Test
    public void everyListedControlHasItsCard() {
        for (EditModeControls.Control control : EditModeControls.all()) {
            assertNotNull(control.id, EditModeControls.cardOf(control.id));
            assertEquals(control.id, control.card, EditModeControls.cardOf(control.id));
        }
    }
}
