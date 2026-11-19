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

    /** The shared Appearance block, after its heading (appearance-cards overhaul §2). */
    private static final java.util.List<Row> BLOCK = Arrays.asList(Row.SECTION_APPEARANCE,
        Row.LOOK, Row.OPACITY, Row.FROST, Row.GRAIN, Row.TINT, Row.CORNERS, Row.GLASS_EDGE,
        Row.DEPTH, Row.RIM, Row.TINT_HUE);

    @Test
    public void everyGlassCardWearsTheAppearanceBlock() {
        for (EditorCard card : new EditorCard[] {EditorCard.DOCK, EditorCard.STATUS,
                EditorCard.TERMINAL, EditorCard.WIDGETS,
                EditorCard.SHEETS}) {
            java.util.List<Row> rows = rowsOf(card, ALL);
            int at = rows.indexOf(Row.SECTION_APPEARANCE);
            assertTrue(card.toString(), at >= 0);
            // In group order; a card's own rows (the Dock's Reflection) may stand among them.
            java.util.List<Row> block = new java.util.ArrayList<>();
            for (Row row : rows.subList(at, rows.size()))
                if (BLOCK.contains(row) && block.size() < BLOCK.size()) block.add(row);
            assertEquals(card.toString(), BLOCK, block);
        }
    }

    @Test
    public void widgetsAndSheetsArePlainGlassCards() {
        for (EditorCard card : new EditorCard[] {EditorCard.WIDGETS, EditorCard.SHEETS}) {
            assertEquals(card.toString(), BLOCK, rowsOf(card, ALL));
            assertFalse(card.hasEye());
        }
        assertEquals(LookGrid.Element.SHEETS, EditorChips.elementOf(EditorCard.SHEETS));
        assertFalse(EditorRows.rows(EditorCard.HOME, ALL).toString().contains("LINK_WIDGETS"));
        assertTrue(EditorRows.rows(EditorCard.APPEARANCE, ALL).toString().contains("LINK_SHEETS"));
    }

    @Test
    public void depthAndEdgeStayWhereTheDeviceCannotDrawThem() {
        assertEquals(rowsOf(EditorCard.DOCK, ALL), rowsOf(EditorCard.DOCK, NO_EDGE));
        assertTrue(rowsOf(EditorCard.APPEARANCE, NO_EDGE).contains(Row.DEPTH));
        assertTrue(GlassRows.needsGlassEdge(Row.GLASS_EDGE));
        assertTrue(GlassRows.needsGlassEdge(Row.DEPTH));
        assertFalse(GlassRows.needsGlassEdge(Row.FROST));
    }

    private static java.util.List<Row> rowsOf(EditorCard card, Offers offers) {
        java.util.List<Row> out = new java.util.ArrayList<>();
        for (EditorRows.Spec spec : EditorRows.rows(card, offers))
            out.add(spec.row);
        return out;
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
    public void pictureGridLinesAreBalanced() {
        // A phone card: room for five 64dp tiles 8dp apart (379 px at 1x).
        assertEquals(3, com.termux.app.surfaces.rows.PreviewTilesRow.columns(379, 64, 8, 6));
        assertEquals(4, com.termux.app.surfaces.rows.PreviewTilesRow.columns(379, 64, 8, 7));
        assertEquals(5, com.termux.app.surfaces.rows.PreviewTilesRow.columns(379, 64, 8, 5));
        assertEquals(4, com.termux.app.surfaces.rows.PreviewTilesRow.columns(379, 64, 8, 4));
        // Large text: three fit, so seven is 3 + 3 + 1 at worst, never wider than the fit.
        assertEquals(3, com.termux.app.surfaces.rows.PreviewTilesRow.columns(379, 120, 8, 7));
        assertEquals(1, com.termux.app.surfaces.rows.PreviewTilesRow.columns(60, 120, 8, 5));
    }

    @Test
    public void marginAndVibrancyAreTheWhereChipsAllGlassWhateverWhatNames() {
        assertTrue(GlassRows.allGlassOnly(Row.MARGIN));
        assertTrue(GlassRows.allGlassOnly(Row.VIBRANCY));
        assertFalse(GlassRows.allGlassOnly(Row.OPACITY));
        EditorChips dock = new EditorChips(LookGrid.Element.DOCK);
        LookGrid.Key margin = dock.cell(GlassRows.cellProperty(Row.MARGIN));
        assertNull("no element column", margin.element);
        assertNull(margin.kind);
        LookGrid.Key vibrancy = dock.cell(GlassRows.cellProperty(Row.VIBRANCY));
        assertNull(vibrancy.element);
        assertEquals(LookGrid.NO_PAGE, vibrancy.page);
    }

}
