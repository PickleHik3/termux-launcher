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
        Row.LOOK, Row.OPACITY, Row.FROST, Row.TINT, Row.CORNERS, Row.SHINE,
        Row.DEPTH, Row.TINT_HUE);

    /** The terminal's glass card wears the whole Appearance block until #287 turns it into Background. */
    @Test
    public void theTerminalCardWearsItsBackgroundAndNoGlassBlock() {
        // ADR 0031 §4 (#287): Opacity, Blur and Wallpaper dim, its own; the glass block is gone.
        java.util.List<Row> rows = rowsOf(EditorCard.TERMINAL, ALL);
        int at = rows.indexOf(Row.SECTION_BACKGROUND);
        assertTrue(at >= 0);
        assertEquals(java.util.Arrays.asList(Row.SECTION_BACKGROUND, Row.TERMINAL_OPACITY,
            Row.TERMINAL_BLUR, Row.WALLPAPER_DIM), rows.subList(at, at + 4));
        for (Row row : BLOCK)
            assertFalse(row.name(), rows.contains(row));
        assertFalse(rows.contains(Row.SECTION_APPEARANCE));
    }

    /**
     * The status bar, the dock and the keyboard own Opacity and Blur only (ADR 0031 §2): their
     * Appearance holds those two and the surface's own extras, never Tint or the rest.
     */
    @Test
    public void theChromeCardsHoldOpacityAndBlurOnly() {
        for (EditorCard card : new EditorCard[] {EditorCard.DOCK, EditorCard.STATUS,
                EditorCard.KEYBOARD}) {
            java.util.List<Row> rows = rowsOf(card, ALL);
            assertTrue(card + " Opacity", rows.contains(Row.OPACITY));
            assertTrue(card + " Blur", rows.contains(Row.FROST));
            for (Row gone : new Row[] {Row.LOOK, Row.TINT, Row.TINT_HUE, Row.CORNERS,
                    Row.SHINE, Row.DEPTH, Row.LINK_GLASS_REFRACTION})
                assertFalse(card + " " + gone, rows.contains(gone));
        }
    }

    @Test
    public void depthAndEdgeStayWhereTheDeviceCannotDrawThem() {
        assertEquals(rowsOf(EditorCard.DOCK, ALL), rowsOf(EditorCard.DOCK, NO_EDGE));
        assertTrue(rowsOf(EditorCard.APPEARANCE, NO_EDGE).contains(Row.DEPTH));
        assertTrue(GlassRows.needsGlassEdge(Row.SHINE));
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
        assertEquals(LookGrid.Property.SHINE, GlassRows.cellProperty(Row.SHINE));
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
        assertTrue("Depth and Shine are app-wide", GlassRows.allGlassOnly(Row.DEPTH));
        assertTrue(GlassRows.allGlassOnly(Row.SHINE));
        assertFalse(GlassRows.allGlassOnly(Row.OPACITY));
        EditorChips dock = new EditorChips(LookGrid.Element.DOCK);
        LookGrid.Key margin = dock.cell(GlassRows.cellProperty(Row.MARGIN));
        assertNull("no element column", margin.element);
        assertNull(margin.kind);
        LookGrid.Key shine = dock.cell(GlassRows.cellProperty(Row.SHINE));
        assertNull(shine.element);
        assertEquals(LookGrid.NO_PAGE, shine.page);
    }

}
