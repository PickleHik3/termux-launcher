package com.termux.app.surfaces;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Test;

/**
 * What an arrange session shows and what it holds back. The point of the mode is that everything
 * shared — the presets, the style pills, the global numbers — is unreachable until the user asks
 * for it, and that the card is never left with nothing but a header.
 */
public class SurfaceEditorCardPlanTest {

    @Test
    public void theFullEditorOffersThePaletteAndTheArrangeSessionDoesNot() {
        assertTrue(SurfaceEditorCardPlan.paletteShown(SurfaceEditorMode.FULL));
        assertFalse(SurfaceEditorCardPlan.paletteShown(SurfaceEditorMode.ARRANGE));
    }

    @Test
    public void everySurfaceCardCarriesItsLookRowsInTheFullEditor() {
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            assertTrue(slot.name(),
                SurfaceEditorCardPlan.lookRowsShown(SurfaceEditorMode.FULL, slot));
            assertFalse(slot.name(),
                SurfaceEditorCardPlan.moreRowShown(SurfaceEditorMode.FULL, slot));
        }
    }

    @Test
    public void anArrangeCardHoldsTheLookRowsBackAndOffersMoreInstead() {
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            assertFalse(slot.name(),
                SurfaceEditorCardPlan.lookRowsShown(SurfaceEditorMode.ARRANGE, slot));
            assertTrue(slot.name(),
                SurfaceEditorCardPlan.moreRowShown(SurfaceEditorMode.ARRANGE, slot));
        }
    }

    @Test
    public void theSharedLayerIsAlwaysTheFullCard() {
        // It is not a place, so it has nothing to arrange and no reduced card to offer.
        assertTrue(SurfaceEditorCardPlan.lookRowsShown(SurfaceEditorMode.ARRANGE, null));
        assertFalse(SurfaceEditorCardPlan.moreRowShown(SurfaceEditorMode.ARRANGE, null));
        assertTrue(SurfaceEditorCardPlan.lookRowsShown(SurfaceEditorMode.FULL, null));
        assertFalse(SurfaceEditorCardPlan.moreRowShown(SurfaceEditorMode.FULL, null));
    }

    @Test
    public void aCardNeverOffersBothTheLookRowsAndTheWayToThem() {
        for (SurfaceEditorMode mode : SurfaceEditorMode.values()) {
            for (SurfaceSlot slot : SurfaceSlot.values()) {
                assertFalse(mode + " " + slot,
                    SurfaceEditorCardPlan.lookRowsShown(mode, slot)
                        == SurfaceEditorCardPlan.moreRowShown(mode, slot));
            }
        }
    }
}
