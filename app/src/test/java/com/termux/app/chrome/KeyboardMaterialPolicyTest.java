package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.KeyboardMaterialPolicy.Material;
import com.termux.app.place.PlaceLayout.KeyboardForm;

import org.junit.Test;

/** The one table behind "overlays are solid, the dock is glass". */
public class KeyboardMaterialPolicyTest {

    @Test
    public void theTerminalsDockedKeyboardIsTheGlassAndKeepsItsOpacity() {
        assertEquals(Material.GLASS, KeyboardMaterialPolicy.hostMaterial(KeyboardForm.DOCKED, false));
        assertTrue(KeyboardMaterialPolicy.opacityApplies(KeyboardForm.DOCKED, false));
    }

    @Test
    public void aDockedKeyboardOverAPlaceIsOneOpaquePanel() {
        assertEquals(Material.SOLID, KeyboardMaterialPolicy.hostMaterial(KeyboardForm.DOCKED, true));
        assertFalse(KeyboardMaterialPolicy.opacityApplies(KeyboardForm.DOCKED, true));
    }

    @Test
    public void theSplitHalvesPaintTheirOwnPanelOnEveryPlace() {
        for (boolean overlays : new boolean[] {false, true}) {
            assertEquals(Material.NONE, KeyboardMaterialPolicy.hostMaterial(KeyboardForm.SPLIT, overlays));
            assertFalse(KeyboardMaterialPolicy.opacityApplies(KeyboardForm.SPLIT, overlays));
            assertTrue(KeyboardMaterialPolicy.paintsOwnSolidSlabs(KeyboardForm.SPLIT));
        }
    }

    @Test
    public void theFloatingCardIsThePanelAndTheHostPaintsNothing() {
        assertEquals(Material.NONE, KeyboardMaterialPolicy.hostMaterial(KeyboardForm.FLOATING, true));
        assertFalse(KeyboardMaterialPolicy.opacityApplies(KeyboardForm.FLOATING, true));
    }

    @Test
    public void onlyTheSplitFormPaintsItsOwnSlabs() {
        assertFalse(KeyboardMaterialPolicy.paintsOwnSolidSlabs(KeyboardForm.DOCKED));
        assertFalse(KeyboardMaterialPolicy.paintsOwnSolidSlabs(KeyboardForm.FLOATING));
    }

    @Test
    public void theSolidFillFollowsTheSurfaceShapeAndNothingElseTakesOne() {
        assertTrue(KeyboardMaterialPolicy.solidFillIsRounded(KeyboardForm.DOCKED, true, true));
        assertFalse(KeyboardMaterialPolicy.solidFillIsRounded(KeyboardForm.DOCKED, true, false));
        // No fill is painted at all for these, so there is no corner to take.
        assertFalse(KeyboardMaterialPolicy.solidFillIsRounded(KeyboardForm.DOCKED, false, true));
        assertFalse(KeyboardMaterialPolicy.solidFillIsRounded(KeyboardForm.FLOATING, true, true));
        assertFalse(KeyboardMaterialPolicy.solidFillIsRounded(KeyboardForm.SPLIT, true, true));
    }
}
