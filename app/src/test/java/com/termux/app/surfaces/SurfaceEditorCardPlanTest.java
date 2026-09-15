package com.termux.app.surfaces;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Test;

/**
 * What a card carries now that the editor only edits how a place looks: its surface's look rows,
 * and the shared strip only where the card is the shared layer's.
 */
public class SurfaceEditorCardPlanTest {

    @Test
    public void theSharedLayersCardCarriesTheSharedStrip() {
        assertTrue(SurfaceEditorCardPlan.sharedStripShown(null));
    }

    @Test
    public void aSurfacesCardCarriesNoneOfIt() {
        for (SurfaceSlot slot : SurfaceSlot.values())
            assertFalse(slot.name(), SurfaceEditorCardPlan.sharedStripShown(slot));
    }

    @Test
    public void everySurfaceCardHasLookRowsToCarry() {
        for (SurfaceSlot slot : SurfaceSlot.values())
            assertFalse(slot.name(), SurfaceEditorProperties.rowsFor(slot).isEmpty());
    }
}
