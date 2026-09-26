package com.termux.app.surfaces;

import com.termux.app.surfaces.SurfaceEditorProperties.Control;
import com.termux.app.surfaces.SurfaceEditorProperties.Kind;
import com.termux.app.surfaces.SurfaceEditorProperties.Section;
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
            SurfaceEditorProperties.ID_APPS,
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
    public void theTerminalOwnsItsRadiusAndItsMarginOutsideTheCascade() {
        Control corners = SurfaceEditorProperties.find(SurfaceSlot.CANVAS,
            SurfaceEditorProperties.ID_CORNERS);
        Control margin = SurfaceEditorProperties.find(SurfaceSlot.CANVAS,
            SurfaceEditorProperties.ID_MARGIN);
        assertNotNull(corners);
        assertNotNull(margin);
        // Not cascade cells: the canvas is the room the other surfaces are inset from, so it has no
        // Base radius or gap to follow, and its two numbers are its own.
        assertNull(corners.cell);
        assertNull(margin.cell);
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
    public void theKeyboardOwnsItsOwnGlassAndItsCapsuleShapeAlone() {
        // Blur, Opacity and Grain now have their own -1 "follow the dock" knobs (outside the
        // cascade, like the terminal's radius/margin), plus the pre-existing Intensity cell —
        // still there, just not under ID_OPACITY any more. Only the dock capsule's corner radius
        // stays borrowed outright: the keyboard has no shape of its own to give it.
        assertNotNull(SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_OPACITY));
        assertNotNull(SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_BLUR));
        assertNotNull(SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_GRAIN));
        assertNotNull(SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_KEYBOARD_INTENSITY));
        assertNotNull(SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_MARGIN));
        assertNull(SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_CORNERS));
        // Blur, Opacity and Grain are outside the cascade here — each is its own -1-following knob,
        // not a cell of Base — while Intensity is still the OPACITY cell it always was.
        Control blur = SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_BLUR);
        Control opacity = SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_OPACITY);
        Control grain = SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_GRAIN);
        Control intensity = SurfaceEditorProperties.find(SurfaceSlot.KEYBOARD,
            SurfaceEditorProperties.ID_KEYBOARD_INTENSITY);
        assertNull(blur.cell);
        assertNull(opacity.cell);
        assertNull(grain.cell);
        assertNotNull(intensity.cell);
        assertEquals(SurfaceProperty.OPACITY, intensity.cell.property);
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

    // --------------------------------------------------------------------------- the sections

    @Test
    public void everySharedRowStandsUnderTheSectionItBelongsTo() {
        assertSection(null, SurfaceEditorProperties.ID_ALL_OPACITY, Section.MATERIAL);
        assertSection(null, SurfaceEditorProperties.ID_ALL_BLUR, Section.MATERIAL);
        assertSection(null, SurfaceEditorProperties.ID_ALL_GRAIN, Section.MATERIAL);
        assertSection(null, SurfaceEditorProperties.ID_ALL_CORNERS, Section.SHAPE);
        assertSection(null, SurfaceEditorProperties.ID_ALL_MARGIN, Section.SHAPE);
        assertSection(null, SurfaceEditorProperties.ID_WALLPAPER, Section.WALLPAPER);
    }

    @Test
    public void eachSurfaceKeepsMaterialAndShapeAndAddsOneSectionOfItsOwn() {
        assertEquals(Arrays.asList(Section.MATERIAL, Section.SHAPE, Section.APPS),
            sectionsOf(SurfaceEditorProperties.panel(SurfaceSlot.DOCK)));
        assertEquals(Arrays.asList(Section.MATERIAL, Section.SHAPE, Section.KEYS),
            sectionsOf(SurfaceEditorProperties.panel(SurfaceSlot.KEYBOARD)));
        assertEquals(Arrays.asList(Section.MATERIAL, Section.SHAPE, Section.INDICATOR),
            sectionsOf(SurfaceEditorProperties.panel(SurfaceSlot.STATUS)));
        // The terminal's frame is always on now, so the canvas has no section of its own.
        assertEquals(Arrays.asList(Section.MATERIAL, Section.SHAPE),
            sectionsOf(SurfaceEditorProperties.panel(SurfaceSlot.CANVAS)));
        assertEquals(Arrays.asList(Section.MATERIAL, Section.SHAPE, Section.WALLPAPER),
            sectionsOf(SurfaceEditorProperties.global()));
    }

    @Test
    public void everyPanelIsHandedOverAlreadyInSectionOrder() {
        // The editor walks the list once and adds a heading each time the section changes, so a
        // section may never come back after another one has started.
        for (List<Control> panel : panels()) {
            Set<Section> seen = new HashSet<>();
            Section current = null;
            for (Control control : panel) {
                if (control.section == current)
                    continue;
                assertFalse(control.id + " reopens " + control.section,
                    seen.contains(control.section));
                seen.add(control.section);
                current = control.section;
            }
        }
    }

    @Test
    public void theSharedOrderStillHoldsInsideASection() {
        // Section-major sorting must not disturb the one thing every panel agreed on: opacity,
        // blur, grain, then corners, margin.
        for (List<Control> panel : panels()) {
            Section current = null;
            int previousRank = -1;
            for (Control control : panel) {
                if (control.section != current) {
                    current = control.section;
                    previousRank = -1;
                }
                int rank = SurfaceEditorProperties.rankOf(control.id);
                assertTrue(control.id + " sorts before its neighbour", rank >= previousRank);
                previousRank = rank;
            }
        }
    }

    @Test
    public void everySectionHasAName() {
        for (Section section : Section.values())
            assertTrue(section + " has no title", section.titleRes != 0);
    }

    private static void assertSection(SurfaceSlot slot, String id, Section expected) {
        Control control = SurfaceEditorProperties.find(slot, id);
        assertNotNull(id + " is not on that panel", control);
        assertEquals(id, expected, control.section);
    }

    private static List<Section> sectionsOf(List<Control> panel) {
        List<Section> sections = new ArrayList<>();
        for (Control control : panel) {
            if (sections.isEmpty() || sections.get(sections.size() - 1) != control.section)
                sections.add(control.section);
        }
        return sections;
    }
}
