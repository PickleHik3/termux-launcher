package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.EditorRows.Kind;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

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

    @Test
    public void theDockCardOpensOnItsStripThenItsPlace() {
        assertEquals(Arrays.asList(Row.FROST, Row.OPACITY, Row.TINT, Row.HEIGHT, Row.CORNERS,
            Row.MARGIN, Row.APP_ICONS, Row.REFLECTION),
            EditorRows.tier(EditorCard.DOCK, ALL, 1));
        assertEquals(Arrays.asList(Row.STYLE, Row.DESIGN),
            EditorRows.tier(EditorCard.DOCK, ALL, 2));
        assertEquals(Arrays.asList(Row.SEARCH_FIELD, Row.DRAWER_ICON, Row.LINK_DOCK_ITEMS),
            EditorRows.tier(EditorCard.DOCK, ALL, 3));
    }

    @Test
    public void theStatusBarAndKeyboardCardsOpenOnTheirStripAndPlace() {
        assertEquals(Arrays.asList(Row.FROST, Row.OPACITY, Row.TINT, Row.CORNERS, Row.MARGIN),
            EditorRows.tier(EditorCard.STATUS, ALL, 1));
    }

    @Test
    public void theKeyboardCardOpensOnItsTypeThenItsGlassWithTheOpacityLink() {
        assertEquals(Arrays.asList(Row.FORM, Row.SECTION_GLASS, Row.FROST, Row.TINT, Row.OPACITY,
            Row.OPACITY_LINK, Row.KEY_OPACITY), EditorRows.tier(EditorCard.KEYBOARD, ALL, 1));
        assertEquals(Arrays.asList(Row.SECTION_SHAPE, Row.KEY_CORNERS, Row.KEY_SPACING,
            Row.HEIGHT, Row.CORNERS, Row.MARGIN, Row.BOTTOM_PADDING),
            EditorRows.tier(EditorCard.KEYBOARD, ALL, 2));
        assertEquals(Arrays.asList(Row.SECTION_MORE, Row.KEYBOARD_THEME, Row.LINK_LAYOUTS),
            EditorRows.tier(EditorCard.KEYBOARD, ALL, 3));
        assertFalse("the floating keyboard is sized on the keyboard itself",
            rowsOf(EditorCard.KEYBOARD, ALL).contains(Row.LINK_KEYBOARD_SIZE));
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
        assertEquals(Arrays.asList(Row.FROST, Row.OPACITY, Row.TINT, Row.TEXT_SIZE, Row.CORNERS,
            Row.MARGIN, Row.SPLIT_GAP), EditorRows.tier(EditorCard.TERMINAL, ALL, 1));
        List<Row> all = rowsOf(EditorCard.TERMINAL, ALL);
        assertFalse(all.contains(Row.VISIBLE));
    }

    @Test
    public void theHomeCardOpensOnHomesGridLookAndGlassAndItIsAlwaysTheHomeHalfs() {
        assertEquals(Arrays.asList(Row.SECTION_GRID, Row.HOME_COLUMNS, Row.HOME_ROWS,
            Row.SECTION_LOOK, Row.LOOK, Row.SECTION_GLASS, Row.FROST, Row.OPACITY, Row.TINT),
            EditorRows.tier(EditorCard.HOME, ALL, 1));
        assertEquals(Arrays.asList(Row.SECTION_LABELS_FOLDERS, Row.LABEL_SIZE,
            Row.HOME_ICON_SIZE, Row.FOLDERS, Row.NUMERALS, Row.SECTION_OPTIONS,
            Row.GLASS_WIDGETS, Row.TWO_LINE, Row.LABEL_SHADOW, Row.PAGE_DOTS,
            Row.WALLPAPER_SCROLL), EditorRows.tier(EditorCard.HOME, ALL, 2));
        assertEquals(Arrays.asList(Row.MATCH_WORKSPACE, Row.SECTION_MORE, Row.LINK_GLOBAL,
            Row.LINK_GESTURES, Row.LINK_PAGES), EditorRows.tier(EditorCard.HOME, ALL, 3));
        assertFalse("the dock sizes its own icons on its card",
            rowsOf(EditorCard.HOME, ALL).contains(Row.ICON_SIZE));
        for (WallHalf frame : WallHalf.values()) {
            for (Row row : new Row[] {Row.FROST, Row.OPACITY, Row.TINT})
                assertEquals(row + " with the frame on " + frame, WallHalf.HOME,
                    EditorRows.halfFor(EditorCard.HOME, row, frame));
            assertNull(EditorRows.halfFor(EditorCard.HOME, Row.ICON_SIZE, frame));
        }
    }

    @Test
    public void theGlobalCardOpensOnTheWallpaperThenTheColourAndKeepsTheGlassMaterial() {
        assertEquals(Arrays.asList(Row.SECTION_WALLPAPER, Row.WALLPAPER, Row.SHOW_WALLPAPER,
            Row.WALLPAPER_COLOURS, Row.SECTION_COLOUR, Row.THEME, Row.CONTRAST, Row.TINT_HUE),
            EditorRows.tier(EditorCard.APPEARANCE, ALL, 1));
        assertEquals(Arrays.asList(Row.SECTION_GLASS_MATERIAL, Row.VIBRANCY, Row.GLASS_EDGE,
            Row.DEPTH, Row.HINT_GLASS_MATERIAL, Row.SECTION_OPTIONS, Row.FULL_SCREEN,
            Row.MINIMAL), EditorRows.tier(EditorCard.APPEARANCE, ALL, 2));
        assertEquals(Arrays.asList(Row.SECTION_GLASS_MATERIAL, Row.VIBRANCY,
            Row.HINT_GLASS_MATERIAL, Row.SECTION_OPTIONS, Row.FULL_SCREEN, Row.MINIMAL),
            EditorRows.tier(EditorCard.APPEARANCE, NONE, 2));
        assertEquals(Arrays.asList(Row.SECTION_MORE, Row.ICON_PACK, Row.TOOL_COLOURS),
            EditorRows.tier(EditorCard.APPEARANCE, ALL, 3));
        List<Row> rows = rowsOf(EditorCard.APPEARANCE, ALL);
        for (Row gone : new Row[] {Row.LOOK, Row.FROST, Row.OPACITY, Row.TINT, Row.TEXT_SIZE})
            assertFalse(gone + " lives on a screen's card or the Terminal's", rows.contains(gone));
    }

    @Test
    public void frostIsTheOneGlassSliderOnEveryCardWithGlass() {
        for (EditorCard card : EditorCard.values()) {
            // Global holds the glass material alone: each screen stores its own glass.
            if (card == EditorCard.APPEARANCE)
                continue;
            List<Row> rows = rowsOf(card, ALL);
            assertTrue(card + " has Frost", rows.contains(Row.FROST));
        }
        for (Row row : Row.values())
            assertFalse("no separate Blur or Grain row: " + row,
                row.name().equals("BLUR") || row.name().equals("GRAIN"));
    }

    @Test
    public void whatTheDeviceCannotDrawIsNotOffered() {
        for (EditorCard card : EditorCard.values()) {
            List<Row> rows = rowsOf(card, NONE);
            assertFalse(card + " offers Depth without the glass edge", rows.contains(Row.DEPTH));
            assertFalse(card + " offers the glass edge", rows.contains(Row.GLASS_EDGE));
            assertFalse(card + " offers the effect", rows.contains(Row.EFFECT));
        }
        assertTrue(rowsOf(EditorCard.TERMINAL, ALL).contains(Row.EFFECT));
    }

    @Test
    public void everyValueSliderIsAStripColumnAndTheLookIsTheOneHorizontalSlider() {
        for (Row row : new Row[] {Row.FROST, Row.OPACITY, Row.TINT, Row.HEIGHT, Row.CORNERS,
            Row.MARGIN, Row.TEXT_SIZE, Row.SPLIT_GAP, Row.ICON_SIZE, Row.APP_ICONS,
            Row.KEY_OPACITY, Row.KEY_CORNERS, Row.KEY_SPACING, Row.DEPTH,
            Row.VIBRANCY, Row.GLASS_EDGE})
            assertEquals(row + " is vertical", Kind.STRIP, row.kind);
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
                if (row.kind == Kind.SECTION) {
                    sections++;
                    inRun = false;
                    continue;
                }
                boolean strip = row.kind == Kind.STRIP || row.kind == Kind.STRIP_LINK;
                if (strip && !inRun)
                    runs++;
                inRun = strip;
            }
            assertTrue(card + "'s sliders stand together, one strip to a section",
                runs >= 1 && runs <= Math.max(1, sections));
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).kind != Kind.STRIP_LINK)
                    continue;
                assertTrue(card + ": a link stands between two strips", i > 0
                    && i < rows.size() - 1 && rows.get(i - 1).kind == Kind.STRIP
                    && rows.get(i + 1).kind == Kind.STRIP);
            }
        }
    }

    @Test
    public void allSurfacesIsTheGlassAndTheTextSize() {
        for (Row row : new Row[] {Row.FROST, Row.OPACITY, Row.TINT, Row.GLASS_EDGE, Row.VIBRANCY,
            Row.DEPTH, Row.TEXT_SIZE})
            assertTrue(row.name(), EditorRows.isAllSurfaces(row));
        assertFalse(EditorRows.isAllSurfaces(Row.LOOK));
        assertFalse(EditorRows.isAllSurfaces(Row.WALLPAPER));
    }

    @Test
    public void theLinksToSettingsComeLast() {
        for (EditorCard card : EditorCard.values()) {
            for (Spec spec : EditorRows.rows(card, ALL)) {
                if (spec.row.kind == Kind.LINK && spec.row != Row.KEYBOARD_THEME)
                    assertEquals(card + ": a link to Settings is rare", 3, spec.tier);
            }
        }
    }

    @Test
    public void theGlassOfTheDockStatusBarAndKeyboardIsTheFramesHalf() {
        for (EditorCard card : new EditorCard[] {EditorCard.DOCK, EditorCard.STATUS,
            EditorCard.KEYBOARD}) {
            for (Row row : new Row[] {Row.OPACITY, Row.TINT, Row.FROST}) {
                for (WallHalf half : WallHalf.values())
                    assertEquals(card + " " + row, half, EditorRows.halfFor(card, row, half));
            }
        }
    }

    @Test
    public void theTerminalShapesAndPlacesAreOneValueForTheWall() {
        for (WallHalf half : WallHalf.values()) {
            assertNull(EditorRows.halfFor(EditorCard.TERMINAL, Row.OPACITY, half));
            for (EditorCard card : EditorCard.values()) {
                for (Row row : new Row[] {Row.CORNERS, Row.MARGIN, Row.HEIGHT,
                    Row.VISIBLE, Row.STYLE, Row.DESIGN, Row.TEXT_SIZE})
                    assertNull(card + " " + row, EditorRows.halfFor(card, row, half));
            }
        }
    }

    @Test
    public void theLookAndTheSharedGlassAreTheFramesHalfAndTheRestOfTheAppearanceCardIsTheWalls() {
        for (Row row : new Row[] {Row.LOOK, Row.FROST, Row.OPACITY, Row.TINT}) {
            assertEquals(WallHalf.HOME, EditorRows.halfFor(EditorCard.APPEARANCE, row,
                WallHalf.HOME));
            assertEquals(WallHalf.WORKSPACE, EditorRows.halfFor(EditorCard.APPEARANCE, row,
                WallHalf.WORKSPACE));
        }
        for (Row row : rowsOf(EditorCard.APPEARANCE, ALL)) {
            if (row != Row.LOOK && row.property() == null)
                assertNull(row.name(), EditorRows.halfFor(EditorCard.APPEARANCE, row,
                    WallHalf.HOME));
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
        assertFalse(EditorRows.marksOwnValue(EditorCard.HOME, Row.ICON_SIZE));
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
