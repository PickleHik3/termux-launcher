package com.termux.app.surfaces;

import com.termux.app.surfaces.SurfaceEditorProperties.Control;
import com.termux.app.surfaces.SurfaceEditorProperties.Kind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The editor's control table. Two things matter here and nothing else does.
 *
 * <p>Coverage and reachability: the editor is the only home in the app for most of these, so a
 * property that falls off every panel is a feature that has left the product. And order: the whole
 * design rests on the five shared rows reading down in the same sequence on every surface, so that
 * a property is always found in the same place relative to its neighbours.
 */
public class SurfaceEditorPropertiesTest {

    /** Every panel there is: the shared layer, then one per surface. */
    private static List<List<Control>> panels() {
        List<List<Control>> panels = new ArrayList<>();
        panels.add(SurfaceEditorProperties.global());
        for (SurfaceSlot slot : SurfaceSlot.values())
            panels.add(SurfaceEditorProperties.panel(slot));
        return panels;
    }

    @Test
    public void everyPanelReadsDownInTheSharedOrder() {
        for (List<Control> panel : panels()) {
            int previous = Integer.MIN_VALUE;
            for (Control control : panel) {
                int rank = SurfaceEditorProperties.rankOf(control.id);
                assertTrue(control.id + " is out of the shared order", rank >= previous);
                previous = rank;
            }
        }
    }

    @Test
    public void noPanelIsEmptyAndNoneOffersAnythingTwice() {
        for (List<Control> panel : panels()) {
            assertFalse("a panel offers nothing", panel.isEmpty());
            Set<String> ids = new HashSet<>();
            for (Control control : panel)
                assertTrue(control.id + " is repeated", ids.add(control.id));
        }
    }

    @Test
    public void theSharedLayerLeadsWithTheGlassTripleAndEndsWithTheWallpaper() {
        List<String> ids = new ArrayList<>();
        for (Control control : SurfaceEditorProperties.global())
            ids.add(control.id);
        assertEquals(Arrays.asList(
            SurfaceEditorProperties.ID_ALL_OPACITY,
            SurfaceEditorProperties.ID_ALL_BLUR,
            SurfaceEditorProperties.ID_ALL_GRAIN,
            SurfaceEditorProperties.ID_ALL_CORNERS,
            SurfaceEditorProperties.ID_ALL_MARGIN,
            SurfaceEditorProperties.ID_WALLPAPER), ids);
    }

    @Test
    public void everyGlassCellASurfaceOwnsIsOnItsPanel() {
        // The editor is the only place these numbers can be set by hand; a cell missing from its
        // surface's panel cannot be set at all.
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            Set<SurfaceProperty> present = new HashSet<>();
            for (Control control : SurfaceEditorProperties.panel(slot)) {
                if (control.cell != null)
                    present.add(control.cell.property);
            }
            for (SurfaceProperty property : SurfaceProperty.values()) {
                if (!TermuxAppSharedPreferences.hasSurfaceProperty(slot, property))
                    continue;
                // The canvas has no capsule radius and no screen-edge gap of its own; what it does
                // have is a terminal corner radius and a pane gap, which are not cascade cells.
                assertTrue(slot + "/" + property + " is on no panel", present.contains(property));
            }
        }
    }

    @Test
    public void everyControlTheEditorAloneOwnsHasAHome() {
        // These live nowhere else in the app: no settings screen carries them. Losing one from the
        // table deletes it from the product, so the list is spelled out rather than derived.
        List<String> mustExist = Arrays.asList(
            SurfaceEditorProperties.ID_SIZE,
            SurfaceEditorProperties.ID_APPS,
            SurfaceEditorProperties.ID_BORDER,
            SurfaceEditorProperties.ID_KEYBOARD_SPACING,
            SurfaceEditorProperties.ID_KEYBOARD_KEY_RADIUS,
            SurfaceEditorProperties.ID_KEYBOARD_KEY_OPACITY,
            SurfaceEditorProperties.ID_KEYBOARD_COLORS,
            SurfaceEditorProperties.ID_CHIP_RADIUS,
            SurfaceEditorProperties.ID_WALLPAPER);

        Set<String> reachable = new HashSet<>();
        for (List<Control> panel : panels()) {
            for (Control control : panel)
                reachable.add(control.id);
        }
        for (String id : mustExist)
            assertTrue(id + " is reachable from no panel", reachable.contains(id));
    }

    @Test
    public void theTerminalOwnsItsFrameItsRadiusAndItsMarginOutsideTheCascade() {
        Control corners = SurfaceEditorProperties.find(SurfaceSlot.CANVAS,
            SurfaceEditorProperties.ID_CORNERS);
        Control margin = SurfaceEditorProperties.find(SurfaceSlot.CANVAS,
            SurfaceEditorProperties.ID_MARGIN);
        Control frame = SurfaceEditorProperties.find(SurfaceSlot.CANVAS,
            SurfaceEditorProperties.ID_BORDER);
        assertNotNull(corners);
        assertNotNull(margin);
        assertNotNull(frame);
        // Not cascade cells: the canvas is the room the other surfaces are inset from, so it has no
        // Base radius or gap to follow, and its two numbers are its own.
        assertNull(corners.cell);
        assertNull(margin.cell);
        assertEquals(Kind.SWITCH, frame.kind);
        assertEquals(SurfaceEditorProperties.MAX_TERMINAL_MARGIN_DP, margin.max);
    }

    @Test
    public void theCanvasOwnsNoMarginCell() {
        // The surface-touch handler is shared by all four surfaces and reads the margin cell on the
        // way down, before it knows whether the gesture is a tap or a drag. The canvas has no such
        // cell — it is the room the others are inset from — and reaching for it there took the home
        // screen down mid-touch. Anything walking to SIDE_GAP must tolerate this being absent.
        assertNull(SurfaceEditorRows.forCell(SurfaceSlot.CANVAS, SurfaceProperty.SIDE_GAP));
        for (SurfaceSlot slot
                : new SurfaceSlot[] {SurfaceSlot.DOCK, SurfaceSlot.KEYBOARD, SurfaceSlot.STATUS})
            assertNotNull(slot + " lost its margin",
                SurfaceEditorRows.forCell(slot, SurfaceProperty.SIDE_GAP));
    }

    @Test
    public void theKeyboardShowsOnlyTheGlassItActuallyOwns() {
        // It renders the dock's material — one blurred backdrop, one grain, the dock capsule's
        // shape — so a blur or grain row on its panel would be a number controlling nothing.
        assertNotNull(SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_OPACITY));
        assertNotNull(SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_MARGIN));
        assertNull(SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_BLUR));
        assertNull(SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_GRAIN));
        assertNull(SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_CORNERS));
    }

    @Test
    public void everySliderHasAUsableTrackAndEveryCellItsRowsCeiling() {
        for (List<Control> panel : panels()) {
            for (Control control : panel) {
                if (control.kind != Kind.SLIDER)
                    continue;
                assertTrue(control.id + " has no track", control.max > 0);
                if (control.cell != null)
                    assertEquals(control.id + " disagrees with its row's ceiling",
                        control.cell.max, control.max);
            }
        }
    }

    @Test
    public void findAnswersForTheSharedLayerAndForOneSurfaceAlike() {
        assertNotNull(SurfaceEditorProperties.find(null,
            SurfaceEditorProperties.ID_ALL_CORNERS));
        assertNotNull(SurfaceEditorProperties.find(SurfaceSlot.DOCK,
            SurfaceEditorProperties.ID_CORNERS));
        assertNotNull(SurfaceEditorProperties.find(SurfaceSlot.DOCK,
            SurfaceEditorProperties.ID_APPS));
        // A surface's rows are its own: the dock does not answer for the shared layer's.
        assertNull(SurfaceEditorProperties.find(SurfaceSlot.DOCK,
            SurfaceEditorProperties.ID_ALL_CORNERS));
        assertNull(SurfaceEditorProperties.find(SurfaceSlot.STATUS,
            SurfaceEditorProperties.ID_APPS));
    }
}
