package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        // The shipped Looks on dark glass (SurfacePresets.PRESETS): switch to
        // SurfacePresets.shippedLook(stop) once it is on this branch.
        int[][] shipped = {{2, 10, 0}, {5, 60, 0}, {3, 46, 35}, {0, 92, 0}};
        assertEquals("a cell per Look", AppearanceLooks.CUSTOM_STOP, shipped.length);
        for (int stop = 0; stop < shipped.length; stop++) {
            int[] cell = GlassRows.lookCell(stop);
            assertEquals(stop + ": its Frost stop", shipped[stop][0], cell[0]);
            assertEquals(stop + ": its opacity", shipped[stop][1], cell[1]);
            assertEquals(stop + ": its tint", shipped[stop][2], cell[2]);
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
}
