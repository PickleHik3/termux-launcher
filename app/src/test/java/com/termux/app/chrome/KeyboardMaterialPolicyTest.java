package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.KeyboardMaterialPolicy.Material;
import com.termux.app.place.PlaceLayout.KeyboardForm;

import org.junit.Test;

/** The one table behind "overlays are solid, the dock is glass, and Home is glass". */
public class KeyboardMaterialPolicyTest {

    @Test
    public void theTerminalsDockedKeyboardIsTheGlassAndKeepsItsOpacity() {
        assertEquals(Material.GLASS,
            KeyboardMaterialPolicy.hostMaterial(KeyboardForm.DOCKED, false, false));
        assertTrue(KeyboardMaterialPolicy.opacityApplies(KeyboardForm.DOCKED, false, false));
    }

    @Test
    public void aDockedKeyboardOverAPlaceIsOneOpaquePanel() {
        assertEquals(Material.SOLID,
            KeyboardMaterialPolicy.hostMaterial(KeyboardForm.DOCKED, true, false));
        assertFalse(KeyboardMaterialPolicy.opacityApplies(KeyboardForm.DOCKED, true, false));
    }

    @Test
    public void theKeyboardOverHomeWearsTheGlassAndItsOpacity() {
        // Home's keyboard overlays the page, but what it lies over is the wallpaper.
        for (KeyboardForm form : new KeyboardForm[] {KeyboardForm.DOCKED, KeyboardForm.SPLIT}) {
            assertEquals(Material.GLASS, KeyboardMaterialPolicy.hostMaterial(form, true, true));
            assertTrue(KeyboardMaterialPolicy.opacityApplies(form, true, true));
            assertFalse(KeyboardMaterialPolicy.solidFillIsRounded(form, true, true, true));
        }
    }

    @Test
    public void theFloatingCardStaysThePanelOnHomeToo() {
        assertEquals(Material.NONE,
            KeyboardMaterialPolicy.hostMaterial(KeyboardForm.FLOATING, true, true));
        assertFalse(KeyboardMaterialPolicy.opacityApplies(KeyboardForm.FLOATING, true, true));
    }

    @Test
    public void theSplitKeyboardWearsTheDockedKeyboardsMaterialOnEveryPlace() {
        // Only its keys part: the host paints it as it paints the docked keyboard, so the parting
        // shows the glass on the terminal and the panel where the keyboard overlays.
        for (boolean overlays : new boolean[] {false, true}) {
            for (boolean home : new boolean[] {false, true}) {
                assertEquals(KeyboardMaterialPolicy.hostMaterial(KeyboardForm.DOCKED, overlays, home),
                    KeyboardMaterialPolicy.hostMaterial(KeyboardForm.SPLIT, overlays, home));
                assertEquals(KeyboardMaterialPolicy.opacityApplies(KeyboardForm.DOCKED, overlays, home),
                    KeyboardMaterialPolicy.opacityApplies(KeyboardForm.SPLIT, overlays, home));
            }
        }
        assertEquals(Material.GLASS,
            KeyboardMaterialPolicy.hostMaterial(KeyboardForm.SPLIT, false, false));
        assertEquals(Material.SOLID,
            KeyboardMaterialPolicy.hostMaterial(KeyboardForm.SPLIT, true, false));
    }

    @Test
    public void theFloatingCardIsThePanelAndTheHostPaintsNothing() {
        assertEquals(Material.NONE,
            KeyboardMaterialPolicy.hostMaterial(KeyboardForm.FLOATING, true, false));
        assertFalse(KeyboardMaterialPolicy.opacityApplies(KeyboardForm.FLOATING, true, false));
    }

    @Test
    public void aKeyboardUpOnBothPlacesBlendsSolidAndGlassWithTheSlide() {
        // Glass toward the panel (the terminal toward an overlaid display): the panel fades in
        // with the wall.
        assertEquals(0f, KeyboardMaterialPolicy.travelSolidness(Material.GLASS, Material.SOLID,
            true, true, 0f), 0f);
        assertEquals(0.25f, KeyboardMaterialPolicy.travelSolidness(Material.GLASS, Material.SOLID,
            true, true, 0.25f), 0.0001f);
        assertEquals(1f, KeyboardMaterialPolicy.travelSolidness(Material.GLASS, Material.SOLID,
            true, true, 1f), 0f);
        // And back the other way the panel thins out.
        assertEquals(0.6f, KeyboardMaterialPolicy.travelSolidness(Material.SOLID, Material.GLASS,
            true, true, 0.4f), 0.0001f);
        // The ends are exactly the resting materials, so the settle repaints nothing visible.
        assertEquals(1f, KeyboardMaterialPolicy.travelSolidness(Material.SOLID, Material.GLASS,
            true, true, 0f), 0f);
    }

    @Test
    public void homeAndTheTerminalAreBothGlassSoNothingBlendsBetweenThem() {
        Material home = KeyboardMaterialPolicy.hostMaterial(KeyboardForm.DOCKED, true, true);
        Material terminal = KeyboardMaterialPolicy.hostMaterial(KeyboardForm.DOCKED, false, false);
        assertEquals(KeyboardMaterialPolicy.NO_TRAVEL,
            KeyboardMaterialPolicy.travelSolidness(home, terminal, true, true, 0.5f), 0f);
    }

    @Test
    public void nothingBlendsUnlessTwoDockedMaterialsDiffer() {
        assertEquals(KeyboardMaterialPolicy.NO_TRAVEL, KeyboardMaterialPolicy.travelSolidness(
            Material.GLASS, Material.GLASS, true, true, 0.5f), 0f);
        assertEquals("a keyboard down on one side travels whole, in the other side's material",
            KeyboardMaterialPolicy.NO_TRAVEL, KeyboardMaterialPolicy.travelSolidness(
                Material.GLASS, Material.SOLID, true, false, 0.5f), 0f);
        assertEquals(KeyboardMaterialPolicy.NO_TRAVEL, KeyboardMaterialPolicy.travelSolidness(
            Material.NONE, Material.NONE, true, true, 0.5f), 0f);
    }

    @Test
    public void theSolidFillFollowsTheSurfaceShapeAndNothingElseTakesOne() {
        assertTrue(KeyboardMaterialPolicy.solidFillIsRounded(KeyboardForm.DOCKED, true, false, true));
        assertFalse(KeyboardMaterialPolicy.solidFillIsRounded(KeyboardForm.DOCKED, true, false, false));
        // No fill is painted at all for these, so there is no corner to take.
        assertFalse(KeyboardMaterialPolicy.solidFillIsRounded(KeyboardForm.DOCKED, false, false, true));
        assertFalse(KeyboardMaterialPolicy.solidFillIsRounded(KeyboardForm.FLOATING, true, false, true));
        // The split keyboard's panel takes the docked one's corner.
        assertTrue(KeyboardMaterialPolicy.solidFillIsRounded(KeyboardForm.SPLIT, true, false, true));
        assertFalse(KeyboardMaterialPolicy.solidFillIsRounded(KeyboardForm.SPLIT, true, false, false));
    }
}
