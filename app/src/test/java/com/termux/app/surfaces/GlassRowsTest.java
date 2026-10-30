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
        for (int stop = 0; stop < GlassRows.LOOK_CELLS.length; stop++) {
            int[] cell = GlassRows.lookCell(stop);
            assertEquals(stop, GlassRows.lookStopFor(cell[0], cell[1], cell[2]));
        }
        assertEquals(FrostStops.nearestStopByBlur(8), GlassRows.lookCell(1)[0]);
        assertNull("Custom writes nothing", GlassRows.lookCell(AppearanceLooks.CUSTOM_STOP));
        assertEquals(AppearanceLooks.CUSTOM_STOP, GlassRows.lookStopFor(0, 50, 50));
    }

    @Test
    public void aPickerOptionIsEightyDpWideAtLeast() {
        assertEquals(80, com.termux.app.surfaces.rows.PreviewTilesRow.tileWidth(80, 20, 6, 4));
        assertTrue(com.termux.app.surfaces.rows.PreviewTilesRow.tileWidth(80, 90, 6, 4) > 80);
    }
}
