package com.termux.app.surfaces;

import com.termux.app.surfaces.SurfaceEditorProperties.Control;
import com.termux.app.surfaces.SurfaceEditorProperties.Kind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The pill's control table. What matters here is coverage and reachability: the editor is the only
 * home in the app for most of these, so a property that falls out of both the chip row and the ⋯
 * sheet is a feature that has left the product.
 */
public class SurfaceEditorPropertiesTest {

    @Test
    public void everySurfaceLeadsWithLookAndOffersNothingTwice() {
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            List<Control> chips = SurfaceEditorProperties.chips(slot);
            assertFalse(slot + " offers no chips", chips.isEmpty());
            assertSame(slot + " does not lead with Look", Kind.LOOK, chips.get(0).kind);

            Set<String> ids = new HashSet<>();
            for (Control control : chips)
                assertTrue(slot + " repeats chip " + control.id, ids.add(control.id));
            for (Control control : SurfaceEditorProperties.more(slot))
                assertTrue(slot + " repeats " + control.id + " in its sheet", ids.add(control.id));
        }
    }

    @Test
    public void everyGlassCellIsReachableUnderThatSurfacesFine() {
        // Look folds blur, opacity and grain into one decision, so Fine is the only place the raw
        // numbers survive. A cell missing from it cannot be set by hand at all.
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            Set<SurfaceProperty> fine = new HashSet<>();
            for (Control control : SurfaceEditorProperties.fine(slot)) {
                assertNotNull(slot + "/" + control.id + " is not a cell", control.cell);
                fine.add(control.cell.property);
            }
            for (SurfaceProperty property
                    : new SurfaceProperty[] {SurfaceProperty.BLUR, SurfaceProperty.OPACITY,
                        SurfaceProperty.GRAIN}) {
                assertEquals(slot + "/" + property + " reachability",
                    TermuxAppSharedPreferences.hasSurfaceProperty(slot, property),
                    fine.contains(property));
            }
        }
    }

    @Test
    public void everyGeometryCellIsReachableAsAChip() {
        // Corner radius and side gap are chips rather than sheet rows: they are what a surface is
        // usually tuned by, and the design puts them on the pill for every surface that owns them.
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            Set<SurfaceProperty> chipped = new HashSet<>();
            for (Control control : SurfaceEditorProperties.chips(slot)) {
                if (control.cell != null)
                    chipped.add(control.cell.property);
            }
            for (SurfaceProperty property
                    : new SurfaceProperty[] {SurfaceProperty.CORNER_RADIUS,
                        SurfaceProperty.SIDE_GAP}) {
                assertEquals(slot + "/" + property + " reachability",
                    TermuxAppSharedPreferences.hasSurfaceProperty(slot, property),
                    chipped.contains(property));
            }
        }
    }

    @Test
    public void everyControlTheEditorAloneOwnsHasAHome() {
        // These live nowhere else in the app: no settings screen carries them. Losing one from the
        // table deletes it from the product, so the list is spelled out rather than derived.
        List<String> mustExist = new ArrayList<>();
        mustExist.add(SurfaceEditorProperties.ID_SIZE);
        mustExist.add(SurfaceEditorProperties.ID_APPS);
        mustExist.add(SurfaceEditorProperties.ID_BORDER);
        mustExist.add(SurfaceEditorProperties.ID_KEYBOARD_HEIGHT);
        mustExist.add(SurfaceEditorProperties.ID_KEYBOARD_SPACING);
        mustExist.add(SurfaceEditorProperties.ID_KEYBOARD_KEY_RADIUS);
        mustExist.add(SurfaceEditorProperties.ID_KEYBOARD_KEY_OPACITY);
        mustExist.add(SurfaceEditorProperties.ID_KEYBOARD_COLORS);
        mustExist.add(SurfaceEditorProperties.ID_CLOCK);
        mustExist.add(SurfaceEditorProperties.ID_CHIP_RADIUS);
        mustExist.add(SurfaceEditorProperties.ID_TERMINAL_RADIUS);
        mustExist.add(SurfaceEditorProperties.ID_TERMINAL_GAP);
        mustExist.add(SurfaceEditorProperties.ID_WALLPAPER);

        Set<String> reachable = new HashSet<>();
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (Control control : SurfaceEditorProperties.chips(slot))
                reachable.add(control.id);
            for (Control control : SurfaceEditorProperties.more(slot))
                reachable.add(control.id);
        }
        for (String id : mustExist)
            assertTrue(id + " is reachable from no surface", reachable.contains(id));
    }

    @Test
    public void theCanvasOwnsNoMarginCell() {
        // The surface-touch handler is shared by all four surfaces and reads the margin cell on the
        // way down, before it knows whether the gesture is a tap or a drag. The canvas has no such
        // cell — it is the room the others are inset from — and reaching for it there took the home
        // screen down mid-touch. Anything walking to SIDE_GAP must tolerate this being absent.
        assertEquals(null, SurfaceEditorRows.forCell(SurfaceSlot.CANVAS, SurfaceProperty.SIDE_GAP));
        for (SurfaceSlot slot
                : new SurfaceSlot[] {SurfaceSlot.DOCK, SurfaceSlot.KEYBOARD, SurfaceSlot.STATUS})
            assertNotNull(slot + " lost its margin",
                SurfaceEditorRows.forCell(slot, SurfaceProperty.SIDE_GAP));
    }

    @Test
    public void theSharedLayerCarriesItsThreeRows() {
        Set<String> ids = new HashSet<>();
        for (Control control : SurfaceEditorProperties.base())
            ids.add(control.id);
        assertTrue(ids.contains(SurfaceEditorProperties.ID_BASE_INTENSITY));
        assertTrue(ids.contains(SurfaceEditorProperties.ID_BASE_CORNERS));
        assertTrue(ids.contains(SurfaceEditorProperties.ID_BASE_GAP));
    }

    @Test
    public void everySliderHasAUsableTrackAndEveryCellItsRowsCeiling() {
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            List<Control> all = new ArrayList<>(SurfaceEditorProperties.chips(slot));
            all.addAll(SurfaceEditorProperties.more(slot));
            all.addAll(SurfaceEditorProperties.fine(slot));
            for (Control control : all) {
                if (control.kind == Kind.PICKER)
                    continue;
                assertTrue(control.id + " has no track", control.max > 0);
                if (control.cell != null)
                    assertEquals(control.id + " disagrees with its row's ceiling",
                        control.cell.max, control.max);
            }
        }
    }

    @Test
    public void findReachesChipsSheetRowsAndFineAlike() {
        assertNotNull(SurfaceEditorProperties.find(SurfaceSlot.DOCK,
            SurfaceEditorProperties.ID_CORNERS));
        assertNotNull(SurfaceEditorProperties.find(SurfaceSlot.DOCK,
            SurfaceEditorProperties.ID_APPS));
        assertNotNull(SurfaceEditorProperties.find(SurfaceSlot.DOCK,
            SurfaceEditorProperties.ID_FINE_BLUR));
        // The keyboard owns opacity and a side gap, and nothing else in the glass triple.
        assertNotNull(SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_FINE_OPACITY));
        assertEquals(null, SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_FINE_BLUR));
        assertEquals(null, SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_CORNERS));
    }
}
