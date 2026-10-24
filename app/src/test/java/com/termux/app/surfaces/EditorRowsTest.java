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
    public void theDockCardIsTheSpecsWithTheRenames() {
        assertEquals(Arrays.asList(Row.OPACITY, Row.TINT, Row.HEIGHT, Row.VISIBLE, Row.POSITION),
            EditorRows.tier(EditorCard.DOCK, ALL, 1));
        assertEquals(Arrays.asList(Row.STYLE, Row.DESIGN, Row.BLUR, Row.CORNERS, Row.MARGIN),
            EditorRows.tier(EditorCard.DOCK, ALL, 2));
        assertEquals(Arrays.asList(Row.GRAIN, Row.DEPTH, Row.SEARCH_FIELD, Row.APP_ICONS,
            Row.LINK_DOCK_ITEMS), EditorRows.tier(EditorCard.DOCK, ALL, 3));
    }

    @Test
    public void theStatusBarAndKeyboardCardsOpenOnTheirGlassAndPlace() {
        assertEquals(Arrays.asList(Row.OPACITY, Row.TINT, Row.VISIBLE, Row.POSITION),
            EditorRows.tier(EditorCard.STATUS, ALL, 1));
        assertEquals(Arrays.asList(Row.OPACITY, Row.TINT, Row.HEIGHT, Row.VISIBLE),
            EditorRows.tier(EditorCard.KEYBOARD, ALL, 1));
        assertTrue(EditorRows.tier(EditorCard.KEYBOARD, ALL, 2).contains(Row.KEYBOARD_THEME));
    }

    @Test
    public void theTerminalCardHasNoPlaceOfItsOwn() {
        assertEquals(Arrays.asList(Row.OPACITY, Row.TINT, Row.TEXT_SIZE),
            EditorRows.tier(EditorCard.TERMINAL, ALL, 1));
        List<Row> all = rowsOf(EditorCard.TERMINAL, ALL);
        assertFalse(all.contains(Row.VISIBLE));
        assertFalse(all.contains(Row.POSITION));
    }

    @Test
    public void theHomeCardIsHomesRowsAndNoGlass() {
        assertEquals(Arrays.asList(Row.ICON_SIZE, Row.LABEL_SIZE, Row.GLASS_WIDGETS),
            EditorRows.tier(EditorCard.HOME, ALL, 1));
        List<Row> all = rowsOf(EditorCard.HOME, ALL);
        for (Row row : all)
            assertNull(row + " is not a surface's glass", row.property());
    }

    @Test
    public void theAppearanceCardOpensOnTheLookTheWallpaperAndTheTheme() {
        assertEquals(Arrays.asList(Row.LOOK, Row.WALLPAPER, Row.THEME),
            EditorRows.tier(EditorCard.APPEARANCE, ALL, 1));
        List<Row> two = EditorRows.tier(EditorCard.APPEARANCE, ALL, 2);
        assertTrue(two.containsAll(Arrays.asList(Row.ICON_PACK, Row.MONOCHROME, Row.TEXT_SIZE,
            Row.WALLPAPER_COLOURS, Row.CONTRAST, Row.FULL_SCREEN, Row.MINIMAL)));
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
    public void theRareRowsAreUnderMore() {
        for (EditorCard card : EditorCard.values()) {
            for (Spec spec : EditorRows.rows(card, ALL)) {
                if (spec.row.kind == Kind.LINK && spec.row != Row.KEYBOARD_THEME)
                    assertEquals(card + ": a link to Settings is rare", 3, spec.tier);
                if (spec.row == Row.GRAIN)
                    assertEquals(card + ": Grain is rare", 3, spec.tier);
            }
        }
    }

    @Test
    public void theGlassOfTheDockStatusBarAndKeyboardIsTheFramesHalf() {
        for (EditorCard card : new EditorCard[] {EditorCard.DOCK, EditorCard.STATUS,
            EditorCard.KEYBOARD}) {
            for (Row row : new Row[] {Row.OPACITY, Row.TINT, Row.BLUR, Row.GRAIN}) {
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
                for (Row row : new Row[] {Row.CORNERS, Row.MARGIN, Row.HEIGHT, Row.POSITION,
                    Row.VISIBLE, Row.STYLE, Row.DESIGN, Row.TEXT_SIZE})
                    assertNull(card + " " + row, EditorRows.halfFor(card, row, half));
            }
        }
    }

    @Test
    public void theLookSliderIsTheFramesHalfAndTheRestOfTheAppearanceCardIsTheWalls() {
        assertEquals(WallHalf.HOME, EditorRows.halfFor(EditorCard.APPEARANCE, Row.LOOK,
            WallHalf.HOME));
        assertEquals(WallHalf.WORKSPACE, EditorRows.halfFor(EditorCard.APPEARANCE, Row.LOOK,
            WallHalf.WORKSPACE));
        for (Row row : rowsOf(EditorCard.APPEARANCE, ALL)) {
            if (row != Row.LOOK)
                assertNull(row.name(), EditorRows.halfFor(EditorCard.APPEARANCE, row,
                    WallHalf.HOME));
        }
    }

    @Test
    public void theDotIsOnlyForASurfacesOwnGlass() {
        assertTrue(EditorRows.marksOwnValue(EditorCard.DOCK, Row.OPACITY));
        assertTrue(EditorRows.marksOwnValue(EditorCard.TERMINAL, Row.TINT));
        assertFalse(EditorRows.marksOwnValue(EditorCard.DOCK, Row.CORNERS));
        assertFalse(EditorRows.marksOwnValue(EditorCard.APPEARANCE, Row.TEXT_SIZE));
        assertFalse(EditorRows.marksOwnValue(EditorCard.HOME, Row.ICON_SIZE));
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
