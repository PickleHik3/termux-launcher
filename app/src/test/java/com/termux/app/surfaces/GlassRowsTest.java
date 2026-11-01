package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.shared.termux.settings.preferences.FrostStops;
import com.termux.shared.termux.settings.preferences.LookGrid;

import org.junit.Test;

import java.util.Arrays;

/** The one glass row set, the rows that edit the chips' cell, and a Look at one cell. */
public class GlassRowsTest {

    private static final Offers ALL = new Offers(true, true);
    private static final Offers NO_EDGE = new Offers(false, true);

    @Test
    public void everyGlassCardWearsTheRowSetInTouchOrder() {
        for (EditorCard card : new EditorCard[] {EditorCard.DOCK, EditorCard.STATUS,
                EditorCard.KEYBOARD, EditorCard.TERMINAL}) {
            assertEquals(card.toString(), Arrays.asList(Row.LOOK, Row.FROST, Row.OPACITY,
                Row.TINT, Row.TINT_HUE, Row.CORNERS, Row.DEPTH, Row.GLASS_EDGE),
                EditorRows.tier(card, ALL, 1));
            assertEquals(card.toString(), Arrays.asList(Row.SECTION_MORE, Row.GRAIN, Row.RIM),
                EditorRows.tier(card, ALL, 3).subList(0, 3));
        }
    }

    @Test
    public void widgetsAndSheetsArePlainGlassCards() {
        for (EditorCard card : new EditorCard[] {EditorCard.WIDGETS, EditorCard.SHEETS}) {
            assertEquals(card.toString(), GlassRows.card(ALL).build(), EditorRows.rows(card, ALL));
            assertFalse(card.hasEye());
        }
        assertEquals(LookGrid.Element.SHEETS, EditorChips.elementOf(EditorCard.SHEETS));
        assertTrue(EditorRows.rows(EditorCard.HOME, ALL).toString().contains("LINK_WIDGETS"));
        assertTrue(EditorRows.rows(EditorCard.APPEARANCE, ALL).toString().contains("LINK_SHEETS"));
    }

    @Test
    public void depthAndEdgeOnlyWhereTheDeviceDrawsThem() {
        assertFalse(EditorRows.tier(EditorCard.DOCK, NO_EDGE, 1).contains(Row.DEPTH));
        assertFalse(EditorRows.tier(EditorCard.DOCK, NO_EDGE, 1).contains(Row.GLASS_EDGE));
    }

    @Test
    public void theCellRowsAreTheGridsProperties() {
        assertEquals(LookGrid.Property.GRAIN, GlassRows.cellProperty(Row.GRAIN));
        assertEquals(LookGrid.Property.RIM, GlassRows.cellProperty(Row.RIM));
        assertEquals(LookGrid.Property.EDGE, GlassRows.cellProperty(Row.GLASS_EDGE));
        assertEquals(LookGrid.Property.DEPTH, GlassRows.cellProperty(Row.DEPTH));
        assertEquals("Colour has an element column", LookGrid.Property.TINT_HUE,
            GlassRows.cellProperty(Row.TINT_HUE));
        assertNull("Opacity goes through the surface properties",
            GlassRows.cellProperty(Row.OPACITY));
    }

    @Test
    public void depthRoundTripsThroughItsBend() {
        for (int depth = GlassEdgeControls.MIN; depth <= GlassEdgeControls.MAX; depth++) {
            int bend = GlassRows.toCell(Row.DEPTH, depth);
            assertEquals(bend, GlassRows.toCell(Row.DEPTH, GlassRows.toRow(Row.DEPTH, bend)));
        }
        assertEquals(0, GlassRows.toRow(Row.CORNERS, LookGrid.CORNERS_AUTO));
    }

    @Test
    public void aLookWritesItsFrostOpacityAndTintAndIsReadBack() {
        // One owner: the shipped Looks on dark glass, as SurfacePresets answers them.
        for (int stop = 0; stop < AppearanceLooks.CUSTOM_STOP; stop++) {
            SurfacePresets.ShippedLook shipped = SurfacePresets.shippedLook(stop);
            assertNotNull(stop + ": a shipped Look", shipped);
            int[] cell = GlassRows.lookCell(stop);
            assertEquals(stop + ": its Frost stop", shipped.frostStop, cell[0]);
            assertEquals(stop + ": its opacity", shipped.opacity, cell[1]);
            assertEquals(stop + ": its tint", shipped.tint, cell[2]);
            assertEquals(stop, GlassRows.lookStopFor(cell[0], cell[1], cell[2]));
        }
        assertNull("Custom writes nothing", GlassRows.lookCell(AppearanceLooks.CUSTOM_STOP));
        assertEquals("a cell off every Look reads Custom", AppearanceLooks.CUSTOM_STOP,
            GlassRows.lookStopFor(5, 61, 0));
    }

    @Test
    public void aPickerOptionIsEightyDpWideAtLeast() {
        assertEquals(80, com.termux.app.surfaces.rows.PreviewTilesRow.tileWidth(80, 20, 6, 4));
        assertTrue(com.termux.app.surfaces.rows.PreviewTilesRow.tileWidth(80, 90, 6, 4) > 80);
    }

    @Test
    public void marginAndVibrancyAreTheWhereChipsAllGlassWhateverWhatNames() {
        assertTrue(GlassRows.allGlassOnly(Row.MARGIN));
        assertTrue(GlassRows.allGlassOnly(Row.VIBRANCY));
        assertFalse(GlassRows.allGlassOnly(Row.OPACITY));
        EditorChips dockOnPage = new EditorChips(4, LookGrid.Element.DOCK);
        LookGrid.Key margin = dockOnPage.cell(GlassRows.cellProperty(Row.MARGIN));
        assertNull("no element column", margin.element);
        assertEquals(4, margin.page);
    }

    @Test
    public void theAppearanceCardsDepthEdgeAndVibrancyLandOnTheWhereChipsPage() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        EditorChips onPage = EditorChips.openFor(EditorCard.APPEARANCE, 4).withPage(4);
        for (Row row : new Row[] {Row.DEPTH, Row.GLASS_EDGE, Row.VIBRANCY}) {
            LookGrid.Key cell = onPage.cell(GlassRows.cellProperty(row));
            grid.write(cell.property, cell.element, cell.page, GlassRows.toCell(row, 30));
            assertEquals(row + " is the page's", LookGrid.Cell.PAGE,
                grid.resolve(cell.property, null, 4).cell);
            assertFalse(row + " leaves the wall", grid.has(cell.property, null, LookGrid.NO_PAGE));
        }
    }

    /** ADR 0018 §3: the keyboard ships Mist on Home pages and Clear on Display pages. */
    @Test
    public void theKeyboardShipsItsOwnLookPerKind() {
        java.util.Map<Integer, LookGrid.Kind> kinds = new java.util.HashMap<>();
        kinds.put(1, LookGrid.Kind.HOME);
        kinds.put(2, LookGrid.Kind.TERMINAL);
        kinds.put(3, LookGrid.Kind.DISPLAY);
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore(), kinds::get,
            SurfacePresets.SHIPPED_BY_KIND);
        SurfacePresets.ShippedLook mist = SurfacePresets.shippedLook(
            AppearanceLooks.stopForPresetId("frost"));
        SurfacePresets.ShippedLook clear = SurfacePresets.shippedLook(
            AppearanceLooks.stopForPresetId("minimal"));
        LookGrid.Element keyboard = LookGrid.Element.KEYBOARD;

        assertEquals(FrostStops.MIST, grid.value(LookGrid.Property.FROST, keyboard, 1));
        assertEquals(60, grid.value(LookGrid.Property.OPACITY, keyboard, 1));
        assertEquals(mist.tint, grid.value(LookGrid.Property.TINT, keyboard, 1));
        assertTrue("grain on", grid.value(LookGrid.Property.GRAIN, keyboard, 1) > 0);
        assertEquals(LookGrid.Cell.SHIPPED, grid.resolve(LookGrid.Property.OPACITY, keyboard, 1).cell);

        assertEquals(FrostStops.CLEAR, clear.frostStop);
        assertEquals(clear.frostStop, grid.value(LookGrid.Property.FROST, keyboard, 3));
        assertEquals(10, grid.value(LookGrid.Property.OPACITY, keyboard, 3));
        assertEquals(10, grid.value(LookGrid.Property.OPACITY, keyboard, LookGrid.Kind.DISPLAY));

        // The Terminal page, the wall and every other element ship one look for every kind.
        for (LookGrid.Property property : new LookGrid.Property[] {LookGrid.Property.FROST,
                LookGrid.Property.OPACITY, LookGrid.Property.TINT, LookGrid.Property.GRAIN}) {
            int plain = new LookGrid(new LookGrid.MemoryStore(), null, null)
                .value(property, keyboard, LookGrid.NO_PAGE);
            assertEquals(property.key, plain, grid.value(property, keyboard, 2));
            assertEquals(property.key, plain, grid.value(property, keyboard, LookGrid.NO_PAGE));
            for (LookGrid.Element element : LookGrid.Element.values()) {
                if (element == keyboard) continue;
                for (int page = 1; page <= 3; page++)
                    assertEquals(element + " " + property.key + " on " + page,
                        new LookGrid(new LookGrid.MemoryStore(), null, null)
                            .value(property, element, LookGrid.NO_PAGE),
                        grid.value(property, element, page));
            }
        }
    }

    /** The seed writes the app's own kind looks as cells: Mist on Home, Clear on Display. */
    @Test
    public void theSeedWritesTheShippedKindLooks() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        assertEquals(6, com.termux.shared.termux.settings.preferences.LookGridKindSeed.run(store,
            SurfacePresets.SHIPPED_BY_KIND));
        LookGrid grid = new LookGrid(store, null, null);
        LookGrid.Element keyboard = LookGrid.Element.KEYBOARD;
        assertEquals(FrostStops.MIST, grid.value(LookGrid.Property.FROST, keyboard, LookGrid.Kind.HOME));
        assertEquals(60, grid.value(LookGrid.Property.OPACITY, keyboard, LookGrid.Kind.HOME));
        assertEquals(0, grid.value(LookGrid.Property.TINT, keyboard, LookGrid.Kind.HOME));
        assertEquals(FrostStops.CLEAR,
            grid.value(LookGrid.Property.FROST, keyboard, LookGrid.Kind.DISPLAY));
        assertEquals(10, grid.value(LookGrid.Property.OPACITY, keyboard, LookGrid.Kind.DISPLAY));
        assertEquals(0, grid.value(LookGrid.Property.TINT, keyboard, LookGrid.Kind.DISPLAY));
        assertEquals(LookGrid.Cell.ELEMENT_ON_KIND,
            grid.resolve(LookGrid.Property.OPACITY, keyboard, LookGrid.Kind.DISPLAY).cell);
    }
}
