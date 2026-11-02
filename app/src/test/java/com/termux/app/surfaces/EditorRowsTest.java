package com.termux.app.surfaces;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.wall.PaneWallPage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.EditorRows.Kind;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * What each card of the Appearance editor holds (appearance-and-settings-rework §3, §7, §8): the
 * rows, tier by tier, and the half each row writes.
 */
public class EditorRowsTest {

    private static final EditorRows.Offers ALL = new EditorRows.Offers(true, true);
    private static final EditorRows.Offers NONE = new EditorRows.Offers(false, false);

    @Test
    public void everyCardStandsItsTiersInOrder() {
        for (EditorCard card : EditorCard.values()) {
            int tier = 1;
            for (Spec spec : EditorRows.rows(card, ALL)) {
                assertTrue(card + ": " + spec + " after tier " + tier, spec.tier >= tier);
                assertTrue(card + ": tiers are 1 to 3", spec.tier >= 1 && spec.tier <= 3);
                tier = spec.tier;
            }
            assertFalse(card + " opens on its first tier",
                EditorRows.tier(card, ALL, 1).isEmpty());
        }
    }

    @Test
    public void noCardHoldsARowTwice() {
        for (EditorCard card : EditorCard.values()) {
            EnumSet<Row> seen = EnumSet.noneOf(Row.class);
            for (Spec spec : EditorRows.rows(card, ALL))
                assertTrue(card + " repeats " + spec.row, seen.add(spec.row));
        }
    }

    // Each card's sections in full are EditorCardSectionsTest's; these hold the first section (tier
    // 1) and the rest (tier 3) as the overhaul §2 orders them.

    @Test
    public void theDockCardOpensOnItsStyleItsBarsAndItsGlass() {
        assertEquals(Arrays.asList(Row.DOCK_TYPE, Row.MARGIN, Row.HOME_ICON_SIZE,
            Row.DOCK_APP_ICONS),
            EditorRows.tier(EditorCard.DOCK, ALL, 1));
        assertEquals(Arrays.asList(Row.SECTION_MORE_OPTIONS, Row.DOCK_ICON_PACK, Row.DOCK_OPTIONS,
            Row.DOCK_MONOCHROME,
            Row.SECTION_LAYOUT, Row.DOCK_BARS, Row.SECTION_APPEARANCE, Row.LOOK, Row.OPACITY, Row.FROST, Row.GRAIN, Row.TINT,
            Row.TINT_HUE, Row.CORNERS, Row.RIM, Row.GLASS_EDGE, Row.DEPTH, Row.REFLECTION,
            Row.SECTION_LINKS, Row.LINK_SHEETS, Row.LINK_GLOBAL),
            EditorRows.tier(EditorCard.DOCK, ALL, 3));
        List<Row> rows = rowsOf(EditorCard.DOCK, ALL);
        assertFalse("no dock-wide Style or Design rows", rows.contains(Row.STYLE)
            || rows.contains(Row.DESIGN));
    }

    @Test
    public void theStatusBarCardOpensOnItsPlaceAndStrip() {
        assertEquals(Arrays.asList(Row.STATUS_POSITION, Row.MARGIN, Row.STATUS_INDICATORS),
            EditorRows.tier(EditorCard.STATUS, ALL, 1));
        assertEquals(Arrays.asList(Row.SECTION_CLOCK_WEATHER, Row.CLOCK_FACE, Row.CLOCK_POSITION,
            Row.STATUS_SECONDS, Row.STATUS_HOURS, Row.STATUS_UNIT, Row.SECTION_APPEARANCE, Row.LOOK, Row.OPACITY, Row.FROST, Row.GRAIN, Row.TINT,
            Row.TINT_HUE, Row.CORNERS, Row.RIM, Row.GLASS_EDGE, Row.DEPTH,
            Row.SECTION_LINKS, Row.CAMERA_CUTOUT, Row.PINNED_NOTIFICATIONS),
            EditorRows.tier(EditorCard.STATUS, ALL, 3));
    }

    @Test
    public void theKeyboardCardOpensOnItsTypeThenItsGlassWithTheOpacityLink() {
        assertEquals(Arrays.asList(Row.FORM, Row.HEIGHT, Row.KEY_SPACING, Row.MARGIN,
            Row.KEY_CORNERS, Row.BOTTOM_PADDING), EditorRows.tier(EditorCard.KEYBOARD, ALL, 1));
        assertEquals(Arrays.asList(Row.SECTION_APPEARANCE, Row.LOOK, Row.OPACITY, Row.OPACITY_LINK,
            Row.KEY_OPACITY, Row.FROST, Row.GRAIN, Row.TINT, Row.TINT_HUE, Row.CORNERS, Row.RIM,
            Row.GLASS_EDGE, Row.DEPTH, Row.SECTION_LINKS, Row.KEYBOARD_THEME, Row.LINK_LAYOUTS),
            EditorRows.tier(EditorCard.KEYBOARD, ALL, 3));
    }

    @Test
    public void theEyeStandsInTheHeaderOfTheCardsWhoseObjectCanBeHidden() {
        for (EditorCard card : EditorCard.values()) {
            boolean eye = card == EditorCard.STATUS || card == EditorCard.DOCK
                || card == EditorCard.KEYBOARD;
            assertEquals(card + " has an eye", eye, card.hasEye());
            assertFalse(card + ": the eye is not a row", rowsOf(card, ALL).contains(Row.VISIBLE));
        }
    }

    @Test
    public void theTerminalCardHasNoPlaceOfItsOwn() {
        assertEquals(Arrays.asList(Row.TEXT_SIZE, Row.CONTRAST, Row.PANE_BORDERS, Row.CURSOR_TRAIL,
            Row.EFFECT, Row.SPLIT_GAP, Row.MARGIN, Row.MULTIPLEXER, Row.AUTO_TILING,
            Row.FOCUS_GROWS), EditorRows.tier(EditorCard.TERMINAL, ALL, 1));
        assertEquals(Arrays.asList(Row.SECTION_LAYOUT, Row.ELEMENTS, Row.SECTION_APPEARANCE, Row.LOOK, Row.OPACITY, Row.FROST, Row.GRAIN, Row.TINT,
            Row.TINT_HUE, Row.CORNERS, Row.RIM, Row.GLASS_EDGE, Row.DEPTH,
            Row.SECTION_LINKS, Row.LINK_FONT), EditorRows.tier(EditorCard.TERMINAL, ALL, 3));
        List<Row> all = rowsOf(EditorCard.TERMINAL, ALL);
        assertFalse(all.contains(Row.VISIBLE));
    }

    @Test
    public void theHomeCardHoldsOneTransitionRowOfFiveGlyphTilesInTheEnumsOrder() {
        // Page transition closes the Home section (overhaul §2).
        assertTrue(EditorRows.tier(EditorCard.HOME, ALL, 1).contains(Row.PAGE_TRANSITION));
        assertEquals(Kind.SEGMENTS, Row.PAGE_TRANSITION.kind);
        assertEquals(5, com.termux.app.launcher.home.HomePageTransition.values().length);
        assertEquals("slide", com.termux.app.launcher.home.HomePageTransition.values()[0].value);
    }

    @Test
    public void iconPackIsALinkToItsSubcardOfThePackTiles() {
        assertEquals("Icon pack opens a subcard, no longer expands in place",
            EditorRows.Kind.LINK, Row.ICON_PACK.kind);
        List<Row> subcard = new java.util.ArrayList<>();
        for (EditorRows.Spec spec : SurfaceEditorController.iconPackCardRows())
            subcard.add(spec.row);
        assertEquals(Arrays.asList(Row.ICON_PACK_PAGE), subcard);
        assertFalse("the page row is the subcard's alone",
            rowsOf(EditorCard.HOME, ALL).contains(Row.ICON_PACK_PAGE));
    }

    @Test
    public void theHomeCardOpensOnHomesGridLookAndGlassAndItIsAlwaysTheHomeHalfs() {
        assertEquals(Arrays.asList(Row.HOME_COLUMNS, Row.HOME_ROWS, Row.ICON_PACK,
            Row.HOME_ICON_SIZE, Row.LABEL_SIZE, Row.PAGE_TRANSITION),
            EditorRows.tier(EditorCard.HOME, ALL, 1));
        assertEquals(Arrays.asList(Row.SECTION_MORE_OPTIONS, Row.TWO_LINE, Row.LABEL_SHADOW,
            Row.PAGE_DOTS, Row.WALLPAPER_SCROLL, Row.FOLDERS, Row.NUMERALS, Row.GLASS_WIDGETS,
            Row.SECTION_LAYOUT, Row.ELEMENTS, Row.SECTION_APPEARANCE, Row.LOOK, Row.OPACITY, Row.FROST, Row.GRAIN, Row.TINT,
            Row.TINT_HUE, Row.CORNERS, Row.RIM, Row.GLASS_EDGE, Row.DEPTH, Row.SECTION_LINKS,
            Row.LINK_GLOBAL, Row.LINK_GESTURES, Row.LINK_PAGES, Row.LINK_SHEETS),
            EditorRows.tier(EditorCard.HOME, ALL, 3));
        for (PaneWallPage frame : PaneWallPage.values()) {
            for (Row row : new Row[] {Row.FROST, Row.OPACITY, Row.TINT})
                assertEquals(row + " with the frame on " + frame, TermuxAppSharedPreferences.PAGE_WALL,
                    EditorRows.pageFor(EditorCard.HOME, row));
            assertEquals(TermuxAppSharedPreferences.PAGE_ALL, EditorRows.pageFor(EditorCard.HOME, Row.HOME_ICON_SIZE));
        }
    }

    @Test
    public void theGlobalCardOpensOnTheWallpaperThenTheColourAndKeepsTheGlassMaterial() {
        assertEquals(Arrays.asList(Row.SECTION_APPEARANCE, Row.LOOK, Row.OPACITY, Row.FROST,
            Row.GRAIN, Row.TINT, Row.CORNERS, Row.RIM, Row.VIBRANCY, Row.GLASS_EDGE, Row.DEPTH),
            EditorRows.tier(EditorCard.APPEARANCE, ALL, 1));
        assertEquals(EditorRows.tier(EditorCard.APPEARANCE, ALL, 1),
            EditorRows.tier(EditorCard.APPEARANCE, NONE, 1));
        assertEquals(Arrays.asList(Row.SECTION_COLOURS, Row.THEME, Row.CONTRAST, Row.TINT_HUE,
            Row.WALLPAPER_COLOURS, Row.SECTION_LAYOUT, Row.ELEMENTS, Row.SECTION_MORE_OPTIONS,
            Row.STYLE, Row.FULL_SCREEN, Row.SHOW_WALLPAPER, Row.SECTION_LINKS, Row.LINK_SHEETS,
            Row.TOOL_COLOURS),
            EditorRows.tier(EditorCard.APPEARANCE, ALL, 3));
        List<Row> rows = rowsOf(EditorCard.APPEARANCE, ALL);
        for (Row gone : new Row[] {Row.CHANGE_WALLPAPER, Row.MINIMAL, Row.ICON_PACK,
            Row.TEXT_SIZE, Row.HINT_GLASS_MATERIAL})
            assertFalse(gone + " is not Customization's", rows.contains(gone));
    }

    @Test
    public void frostIsTheOneGlassSliderOnEveryCardWithGlass() {
        for (EditorCard card : EditorCard.values()) {
            List<Row> rows = rowsOf(card, ALL);
            assertTrue(card + " has Frost", rows.contains(Row.FROST));
        }
        // Grain is its own row since the look grid (ADR 0017); Blur stays Frost's.
        for (Row row : Row.values())
            assertFalse("no separate Blur row: " + row, row.name().equals("BLUR"));
    }

    @Test
    public void whatTheDeviceCannotDrawIsNotOffered() {
        // Edge and Depth stay listed where the phone cannot draw them: the editor shows them
        // disabled, reading "Not on this phone." (SurfaceEditorCardsTest holds that).
        for (EditorCard card : EditorCard.values()) {
            List<Row> rows = rowsOf(card, NONE);
            assertEquals(card + " lists Depth either way", rowsOf(card, ALL).contains(Row.DEPTH),
                rows.contains(Row.DEPTH));
            assertFalse(card + " offers the effect", rows.contains(Row.EFFECT));
        }
        assertTrue(rowsOf(EditorCard.TERMINAL, ALL).contains(Row.EFFECT));
        assertTrue(GlassRows.needsGlassEdge(Row.DEPTH));
        assertTrue(GlassRows.needsGlassEdge(Row.GLASS_EDGE));
    }

    @Test
    public void everyValueSliderIsAStripColumnAndTheLookIsTheOneHorizontalSlider() {
        for (Row row : new Row[] {Row.FROST, Row.OPACITY, Row.TINT, Row.HEIGHT, Row.CORNERS,
            Row.MARGIN, Row.TEXT_SIZE, Row.SPLIT_GAP,
            Row.KEY_OPACITY, Row.KEY_CORNERS, Row.KEY_SPACING, Row.DEPTH,
            Row.VIBRANCY, Row.GLASS_EDGE})
            assertEquals(row + " is vertical", Kind.SLIDER, row.kind);
        for (Row row : Row.values()) {
            if (row != Row.LOOK)
                assertTrue(row + " is no second horizontal slider", row.kind != Kind.LOOK);
        }
    }

    @Test
    public void aSectionsSlidersStandInOneStrip() {
        for (EditorCard card : EditorCard.values()) {
            List<Row> rows = rowsOf(card, ALL);
            int runs = 0;
            int sections = 0;
            boolean inRun = false;
            for (Row row : rows) {
                if (CardBody.shape(row) == CardBody.Shape.SECTION) {
                    sections++;
                    inRun = false;
                    continue;
                }
                boolean strip = row.kind == Kind.SLIDER
                    || CardBody.shape(row) == CardBody.Shape.SLIDER_LINK;
                if (strip && !inRun)
                    runs++;
                inRun = strip;
            }
            // The spec's touch order puts Colour between the legend strip and Corners, and
            // Grain under More, so a card may hold several runs; it holds one at least.
            assertTrue(card + " has a slider", runs >= 1);
            for (int i = 0; i < rows.size(); i++) {
                if (CardBody.shape(rows.get(i)) != CardBody.Shape.SLIDER_LINK)
                    continue;
                assertTrue(card + ": a link stands between two strips", i > 0
                    && i < rows.size() - 1 && rows.get(i - 1).kind == Kind.SLIDER
                    && rows.get(i + 1).kind == Kind.SLIDER);
            }
        }
    }

    @Test
    public void allSurfacesIsTheGlassAndTheTextSize() {
        for (Row row : new Row[] {Row.FROST, Row.OPACITY, Row.TINT, Row.GLASS_EDGE, Row.VIBRANCY,
            Row.DEPTH, Row.TEXT_SIZE})
            assertTrue(row.name(), EditorRows.isAllSurfaces(row));
        assertFalse(EditorRows.isAllSurfaces(Row.LOOK));
        assertFalse(EditorRows.isAllSurfaces(Row.CHANGE_WALLPAPER));
    }

    @Test
    public void theLinksToSettingsComeLast() {
        for (EditorCard card : EditorCard.values()) {
            for (Spec spec : EditorRows.rows(card, ALL)) {
                // Pane borders and Icon pack open a subcard (›) inside their card's first
                // section, as the spec puts them.
                if (spec.row.kind == Kind.LINK && spec.row != Row.KEYBOARD_THEME
                        && spec.row != Row.PANE_BORDERS && spec.row != Row.ICON_PACK)
                    assertEquals(card + ": a link to Settings is rare", 3, spec.tier);
            }
        }
    }

    @Test
    public void theGlassOfTheDockStatusBarAndKeyboardIsTheFramesHalf() {
        for (EditorCard card : new EditorCard[] {EditorCard.DOCK, EditorCard.STATUS,
            EditorCard.KEYBOARD}) {
            for (Row row : new Row[] {Row.OPACITY, Row.TINT, Row.FROST}) {
                for (PaneWallPage half : PaneWallPage.values())
                    assertEquals(card + " " + row, TermuxAppSharedPreferences.PAGE_WALL, EditorRows.pageFor(card, row));
            }
        }
    }

    @Test
    public void theTerminalShapesAndPlacesAreOneValueForTheWall() {
        for (PaneWallPage half : PaneWallPage.values()) {
            assertEquals(TermuxAppSharedPreferences.PAGE_ALL, EditorRows.pageFor(EditorCard.TERMINAL, Row.OPACITY));
            for (EditorCard card : EditorCard.values()) {
                for (Row row : new Row[] {Row.CORNERS, Row.MARGIN, Row.HEIGHT,
                    Row.VISIBLE, Row.STYLE, Row.DESIGN, Row.TEXT_SIZE})
                    assertEquals(card + " " + row, TermuxAppSharedPreferences.PAGE_ALL, EditorRows.pageFor(card, row));
            }
        }
    }

    @Test
    public void theLookAndTheSharedGlassAreTheFramesHalfAndTheRestOfTheAppearanceCardIsTheWalls() {
        for (Row row : new Row[] {Row.LOOK, Row.FROST, Row.OPACITY, Row.TINT}) {
            assertEquals(TermuxAppSharedPreferences.PAGE_WALL, EditorRows.pageFor(EditorCard.APPEARANCE, row));
            assertEquals(TermuxAppSharedPreferences.PAGE_WALL, EditorRows.pageFor(EditorCard.APPEARANCE, row));
        }
        for (Row row : rowsOf(EditorCard.APPEARANCE, ALL)) {
            if (row != Row.LOOK && row.property() == null)
                assertEquals(row.name(), TermuxAppSharedPreferences.PAGE_ALL, EditorRows.pageFor(EditorCard.APPEARANCE, row));
        }
    }

    @Test
    public void theDotIsOnlyForASurfacesOwnGlass() {
        assertTrue(EditorRows.marksOwnValue(EditorCard.DOCK, Row.OPACITY));
        assertTrue(EditorRows.marksOwnValue(EditorCard.TERMINAL, Row.TINT));
        assertTrue(EditorRows.marksOwnValue(EditorCard.STATUS, Row.FROST));
        assertFalse("the shared glass has no dot",
            EditorRows.marksOwnValue(EditorCard.APPEARANCE, Row.FROST));
        assertFalse(EditorRows.marksOwnValue(EditorCard.DOCK, Row.CORNERS));
        assertFalse(EditorRows.marksOwnValue(EditorCard.APPEARANCE, Row.TEXT_SIZE));
        assertFalse(EditorRows.marksOwnValue(EditorCard.HOME, Row.HOME_ICON_SIZE));
        assertFalse("Home's glass is the Base itself", EditorRows.marksOwnValue(EditorCard.HOME,
            Row.FROST));
    }

    @Test
    public void eachSurfaceCardIsItsSlots() {
        assertEquals(SurfaceSlot.DOCK, EditorCard.DOCK.slot);
        assertEquals(SurfaceSlot.CANVAS, EditorCard.TERMINAL.slot);
        assertEquals(EditorCard.KEYBOARD, EditorCard.forSlot(SurfaceSlot.KEYBOARD));
        assertNull(EditorCard.HOME.slot);
    }

    private static List<Row> rowsOf(EditorCard card, EditorRows.Offers offers) {
        List<Row> out = new java.util.ArrayList<>();
        for (Spec spec : EditorRows.rows(card, offers))
            out.add(spec.row);
        return out;
    }
}
