package com.termux.app.surfaces;

import com.termux.app.page.PageKind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
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
        assertEquals(Arrays.asList(Row.DOCK_TYPE, Row.DOCK_APP_ICONS),
            EditorRows.tier(EditorCard.DOCK, ALL, 1));
        // Bars keep order and settings; Appearance holds the dock's own Opacity and Blur and
        // Peek's Reflection (spec §3, Dock). No Links: Global is the pill's.
        assertEquals(Arrays.asList(Row.DOCK_ICON_PACK, Row.DOCK_OPTIONS, Row.DOCK_MONOCHROME,
            Row.DOCK_MOST_USED), EditorRows.tier(EditorCard.DOCK, ALL, 2));
        assertEquals(Arrays.asList(Row.OPACITY, Row.FROST, Row.REFLECTION),
            EditorRows.tier(EditorCard.DOCK, ALL, 3));
        List<Row> rows = rowsOf(EditorCard.DOCK, ALL);
        assertFalse("no dock-wide Style or Design rows", rows.contains(Row.STYLE)
            || rows.contains(Row.DESIGN));
    }

    @Test
    public void theDockCardKeepsTheDockRowsOnlyWhereAnAppsRowIsShown() {
        EditorRows.Offers noAppsRow =
            new EditorRows.Offers(true, true, true, false, false, false);
        List<Row> rows = rowsOf(EditorCard.DOCK, noAppsRow);
        for (Row row : new Row[] {Row.DOCK_TYPE, Row.DOCK_APP_ICONS,
            Row.DOCK_ICON_PACK, Row.DOCK_OPTIONS, Row.DOCK_MONOCHROME, Row.DOCK_MOST_USED})
            assertFalse(row + " stands without an apps row", rows.contains(row));
        // No empty section: Bars and Appearance are the card's own and stay, headers with rows.
        assertEquals(Arrays.asList(Row.OPACITY, Row.FROST, Row.REFLECTION), rows);

        EditorRows.Offers appsRow = new EditorRows.Offers(true, true, true, false, false, true);
        assertEquals(rowsOf(EditorCard.DOCK, ALL), rowsOf(EditorCard.DOCK, appsRow));
        assertTrue(rowsOf(EditorCard.DOCK, appsRow).contains(Row.DOCK_TYPE));
        assertTrue(rowsOf(EditorCard.DOCK, appsRow).contains(Row.DOCK_MONOCHROME));
    }

    @Test
    public void theSmallBarCardsHoldTheDocksGlassAndTheirOwnKnobs() {
        // #344/#347: tapping A-Z or Extra keys opens a small card named for that bar.
        java.util.List<Row> az = new java.util.ArrayList<>();
        for (EditorRows.Spec spec : com.termux.app.surfaces.cards.DockCard.azRows())
            az.add(spec.row);
        assertEquals(Arrays.asList(Row.OPACITY, Row.FROST, Row.AZ_GLASS, Row.AZ_SHOW), az);
        java.util.List<Row> keys = new java.util.ArrayList<>();
        for (EditorRows.Spec spec : com.termux.app.surfaces.cards.DockCard.extraKeysRows())
            keys.add(spec.row);
        assertEquals(Arrays.asList(Row.OPACITY, Row.FROST, Row.EXTRA_KEYS_EDITOR), keys);
        assertEquals(EditorRows.Form.PAIR, com.termux.app.surfaces.cards.DockCard.azRows().get(0).form);
        assertEquals(EditorRows.Form.LINKS,
            com.termux.app.surfaces.cards.DockCard.extraKeysRows().get(2).form);
    }

    @Test
    public void theStatusBarCardOpensOnItsPlaceAndStrip() {
        assertEquals(Arrays.asList(Row.STATUS_PLACE, Row.STATUS_PAGE_BAR,
            Row.STATUS_INDICATORS),
            EditorRows.tier(EditorCard.STATUS, ALL, 1));
        assertEquals(Arrays.asList(Row.CLOCK_FACE, Row.CLOCK_POSITION, Row.STATUS_HOURS,
            Row.STATUS_SECONDS, Row.STATUS_UNIT), EditorRows.tier(EditorCard.STATUS, ALL, 2));
        assertEquals(Arrays.asList(Row.OPACITY, Row.FROST, Row.CAMERA_CUTOUT,
            Row.PINNED_NOTIFICATIONS), EditorRows.tier(EditorCard.STATUS, ALL, 3));
    }

    @Test
    public void theStatusDockAndKeyboardCardsDrawTheirRunsInTheCondensedForms() {
        // #347: the glass pair on all three, the clock format as one row of chips, the floating
        // size as a pair, the opacities with the link between them, links as chips.
        assertEquals(EditorRows.Form.PAIR, formOf(EditorCard.STATUS, Row.OPACITY));
        assertEquals(EditorRows.Form.PAIR, formOf(EditorCard.STATUS, Row.FROST));
        assertEquals(EditorRows.Form.LINKS, formOf(EditorCard.STATUS, Row.CAMERA_CUTOUT));
        assertEquals(EditorRows.Form.LINKS, formOf(EditorCard.STATUS, Row.PINNED_NOTIFICATIONS));
        assertEquals(EditorRows.Form.PAIR, formOf(EditorCard.DOCK, Row.OPACITY));
        assertEquals(EditorRows.Form.PAIR, formOf(EditorCard.DOCK, Row.FROST));
        assertEquals(EditorRows.Form.PLAIN, formOf(EditorCard.DOCK, Row.REFLECTION));
        assertEquals(EditorRows.Form.PAIR, formOf(EditorCard.KEYBOARD, Row.KEY_SPACING));
        assertEquals(EditorRows.Form.PAIR, formOf(EditorCard.KEYBOARD, Row.KEY_CORNERS));
        assertEquals(EditorRows.Form.PAIR, formOf(EditorCard.KEYBOARD, Row.KEYBOARD_WIDTH));
        assertEquals(EditorRows.Form.PAIR, formOf(EditorCard.KEYBOARD, Row.KEYBOARD_FLOAT_HEIGHT));
        assertEquals(EditorRows.Form.LINKS, formOf(EditorCard.KEYBOARD, Row.LINK_LAYOUTS));
        // The two opacities and their link stand together, in this order, in one run.
        List<Row> rows = rowsOf(EditorCard.KEYBOARD, ALL);
        int at = rows.indexOf(Row.OPACITY);
        assertEquals(Row.OPACITY_LINK, rows.get(at + 1));
        assertEquals(Row.KEY_OPACITY, rows.get(at + 2));
    }

    private static EditorRows.Form formOf(EditorCard card, Row row) {
        for (EditorRows.Spec spec : EditorRows.rows(card, ALL))
            if (spec.row == row)
                return spec.form;
        throw new AssertionError(row + " is not on " + card);
    }

    @Test
    public void theKeyboardCardOpensOnItsTypeThenItsGlassWithTheOpacityLink() {
        assertEquals(Arrays.asList(Row.FORM, Row.KEY_SPACING,
            Row.KEY_CORNERS, Row.BOTTOM_PADDING, Row.KEYBOARD_WIDTH, Row.KEYBOARD_FLOAT_HEIGHT,
            Row.KEYBOARD_SPLIT_GAP), EditorRows.tier(EditorCard.KEYBOARD, ALL, 1));
        assertEquals(Arrays.asList(Row.OPACITY, Row.OPACITY_LINK, Row.KEY_OPACITY),
            EditorRows.tier(EditorCard.KEYBOARD, ALL, 2));
        assertEquals(Arrays.asList(Row.FROST, Row.KEYBOARD_THEME, Row.LINK_LAYOUTS),
            EditorRows.tier(EditorCard.KEYBOARD, ALL, 3));
    }

    @Test
    public void theTerminalCardHoldsTextItsBackgroundPanesEffectsAndLayout() {
        // #346: no headings; the sliders in pairs first, the rest in group order.
        assertEquals(Arrays.asList(Row.FONT_SIZE, Row.SPLIT_GAP, Row.TERMINAL_OPACITY,
            Row.TERMINAL_BLUR, Row.WALLPAPER_DIM),
            EditorRows.tier(EditorCard.TERMINAL, ALL, 1));
        assertEquals(Arrays.asList(Row.CONTRAST, Row.CURSOR_TRAIL, Row.EFFECT,
            Row.ADAPTIVE_LEGIBILITY, Row.MULTIPLEXER, Row.AUTO_TILING, Row.FOCUS_GROWS,
            Row.SCREEN_EVENTS, Row.COMPOSITE_SIGNAL, Row.MINIMAL, Row.SEARCH_FIELD,
            Row.DRAWER_ICON,
            Row.LINK_FONT, Row.PANE_BORDERS),
            EditorRows.tier(EditorCard.TERMINAL, ALL, 3));
    }

    @Test
    public void theHomeCardHoldsOneTransitionRowOfFiveGlyphTilesInTheEnumsOrder() {
        // Page transition is Global's Screen (spec §3).
        assertTrue(EditorRows.tier(EditorCard.APPEARANCE, ALL, 3).contains(Row.PAGE_TRANSITION));
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
        // The condensed kinds (#345): no headings; the groups in order (the grid, the short
        // choices, the switches, the Layout list), the links sharing one row at the end.
        assertEquals(Arrays.asList(Row.HOME_COLUMNS, Row.HOME_ROWS, Row.LABEL_SIZE,
            Row.FOLDERS, Row.NUMERALS, Row.TWO_LINE, Row.LABEL_SHADOW, Row.WALLPAPER_SCROLL,
            Row.GLASS_WIDGETS, Row.SEARCH_FIELD, Row.DRAWER_ICON),
            EditorRows.tier(EditorCard.HOME, ALL, 1));
        assertEquals(Arrays.asList(Row.ICON_PACK, Row.LINK_GESTURES, Row.LINK_PAGES),
            EditorRows.tier(EditorCard.HOME, ALL, 3));
        for (PageKind frame : PageKind.values()) {
            for (Row row : new Row[] {Row.FROST, Row.OPACITY, Row.TINT})
                assertEquals(row + " with the frame on " + frame, TermuxAppSharedPreferences.PAGE_WALL,
                    EditorRows.pageFor(EditorCard.HOME, row));
            assertEquals(TermuxAppSharedPreferences.PAGE_ALL, EditorRows.pageFor(EditorCard.HOME, Row.HOME_ICON_SIZE));
        }
    }

    @Test
    public void theGlobalCardOpensOnTheLookAndKeepsTheRestOfTheGlassUnderFineTune() {
        assertEquals(Arrays.asList(Row.WALLPAPER, Row.SHOW_WALLPAPER, Row.LOOK,
            Row.OPACITY, Row.FROST, Row.TINT),
            EditorRows.tier(EditorCard.APPEARANCE, ALL, 1));
        assertEquals(EditorRows.tier(EditorCard.APPEARANCE, ALL, 1),
            EditorRows.tier(EditorCard.APPEARANCE, NONE, 1));
        assertEquals(Arrays.asList(Row.STYLE, Row.THEME, Row.CORNERS, Row.MARGIN, Row.TEXT_SIZE,
            Row.HOME_ICON_SIZE, Row.CONTRAST, Row.PAGE_TRANSITION,
            Row.ADAPTIVE_LEGIBILITY, Row.WALLPAPER_COLOURS, Row.FULL_SCREEN,
            Row.TINT_HUE, Row.TOOL_COLOURS, Row.SECTION_FINE_TUNE, Row.DEPTH, Row.SHINE),
            EditorRows.tier(EditorCard.APPEARANCE, ALL, 3));
        List<Row> rows = rowsOf(EditorCard.APPEARANCE, ALL);
        // Status position and Dock type are on their own cards; the Layout lists on the page
        // cards (spec §3, Global).
        for (Row gone : new Row[] {Row.CHANGE_WALLPAPER, Row.MINIMAL, Row.ICON_PACK,
            Row.FONT_SIZE, Row.HINT_GLASS_MATERIAL, Row.DOCK_TYPE})
            assertFalse(gone + " is not Global's", rows.contains(gone));
    }

    @Test
    public void frostIsTheOneGlassSliderOnEveryCardWithGlass() {
        for (EditorCard card : EditorCard.values()) {
            List<Row> rows = rowsOf(card, ALL);
            // Home has no glass: the chrome wears one look on every page (ADR 0031); the
            // Terminal's is its Background's own Blur, never Global's Frost (#287).
            assertEquals(card + " has Frost",
                card != EditorCard.HOME && card != EditorCard.TERMINAL, rows.contains(Row.FROST));
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
        assertTrue(GlassRows.needsGlassEdge(Row.SHINE));
    }

    @Test
    public void everyValueSliderIsAStripColumnAndTheLookIsTheOneHorizontalSlider() {
        for (Row row : new Row[] {Row.FROST, Row.OPACITY, Row.TINT, Row.CORNERS,
            Row.MARGIN, Row.TEXT_SIZE, Row.SPLIT_GAP,
            Row.KEY_OPACITY, Row.KEY_CORNERS, Row.KEY_SPACING, Row.DEPTH,
            Row.SHINE})
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
            // Home's one slider, Wallpaper dim, is the Terminal's since #287.
            assertTrue(card + " has a slider", card == EditorCard.HOME || runs >= 1);
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
        for (Row row : new Row[] {Row.FROST, Row.OPACITY, Row.TINT, Row.SHINE,
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
                // section, and Font stands in the Terminal's Text, as the spec puts them.
                if (spec.row.kind == Kind.LINK && spec.row != Row.KEYBOARD_THEME
                        && spec.row != Row.PANE_BORDERS && spec.row != Row.ICON_PACK
                        && spec.row != Row.LINK_FONT)
                    assertEquals(card + ": a link to Settings is rare", 3, spec.tier);
            }
        }
    }

    @Test
    public void theGlassOfTheDockStatusBarAndKeyboardIsTheFramesHalf() {
        for (EditorCard card : new EditorCard[] {EditorCard.DOCK, EditorCard.STATUS,
            EditorCard.KEYBOARD}) {
            for (Row row : new Row[] {Row.OPACITY, Row.TINT, Row.FROST}) {
                for (PageKind half : PageKind.values())
                    assertEquals(card + " " + row, TermuxAppSharedPreferences.PAGE_WALL, EditorRows.pageFor(card, row));
            }
        }
    }

    @Test
    public void theTerminalShapesAndPlacesAreOneValueForTheWall() {
        for (PageKind half : PageKind.values()) {
            assertEquals(TermuxAppSharedPreferences.PAGE_ALL, EditorRows.pageFor(EditorCard.TERMINAL, Row.OPACITY));
            for (EditorCard card : EditorCard.values()) {
                for (Row row : new Row[] {Row.CORNERS, Row.MARGIN,
                    Row.STYLE, Row.DESIGN, Row.TEXT_SIZE})
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

    /** Only the status bar, the dock and the keyboard own values (ADR 0031 §2): the arrow's cards. */
    @Test
    public void onlyTheChromeCardsShowAnArrow() {
        for (EditorCard card : EditorCard.values()) {
            boolean own = card == EditorCard.STATUS || card == EditorCard.DOCK
                || card == EditorCard.KEYBOARD;
            assertEquals(card.toString(), own, SurfaceEditorController.ownColumn(card) != null);
        }
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
