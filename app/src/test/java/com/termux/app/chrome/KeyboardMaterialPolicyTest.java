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
    public void aDockedKeyboardUpOnBothPlacesBlendsSolidAndGlassWithTheSlide() {
        // Terminal (glass) toward Display (solid): the panel fades in with the wall.
        assertEquals(0f, KeyboardMaterialPolicy.travelSolidness(KeyboardForm.DOCKED, false, true,
            true, true, 0f), 0f);
        assertEquals(0.25f, KeyboardMaterialPolicy.travelSolidness(KeyboardForm.DOCKED, false, true,
            true, true, 0.25f), 0.0001f);
        assertEquals(1f, KeyboardMaterialPolicy.travelSolidness(KeyboardForm.DOCKED, false, true,
            true, true, 1f), 0f);
        // And back the other way the panel thins out.
        assertEquals(0.6f, KeyboardMaterialPolicy.travelSolidness(KeyboardForm.DOCKED, true, false,
            true, true, 0.4f), 0.0001f);
        // The ends are exactly the resting materials, so the settle repaints nothing visible.
        assertEquals(1f, KeyboardMaterialPolicy.travelSolidness(KeyboardForm.DOCKED, true, false,
            true, true, 0f), 0f);
    }

    @Test
    public void nothingBlendsUnlessTwoDockedMaterialsDiffer() {
        assertEquals(KeyboardMaterialPolicy.NO_TRAVEL, KeyboardMaterialPolicy.travelSolidness(
            KeyboardForm.DOCKED, false, false, true, true, 0.5f), 0f);
        assertEquals("a keyboard down on one side travels whole, in the other side's material",
            KeyboardMaterialPolicy.NO_TRAVEL, KeyboardMaterialPolicy.travelSolidness(
                KeyboardForm.DOCKED, false, true, true, false, 0.5f), 0f);
        assertEquals(KeyboardMaterialPolicy.NO_TRAVEL, KeyboardMaterialPolicy.travelSolidness(
            KeyboardForm.SPLIT, false, true, true, true, 0.5f), 0f);
        assertEquals(KeyboardMaterialPolicy.NO_TRAVEL, KeyboardMaterialPolicy.travelSolidness(
            KeyboardForm.FLOATING, false, true, true, true, 0.5f), 0f);
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
