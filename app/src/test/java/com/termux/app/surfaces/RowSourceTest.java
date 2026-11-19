package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.EditorRows.Row;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.LookGrid.Cell;
import com.termux.shared.termux.settings.preferences.LookGrid.Element;
import com.termux.shared.termux.settings.preferences.LookGrid.Property;

import org.junit.Test;

/** The dot after a row's name, and Reset this row, speak of the cell the value comes from. */
public class RowSourceTest {

    @Test
    public void theDotMarksAnElementsOwnValueAndNoSharedOne() {
        assertEquals(RowSource.ELEMENT, RowSource.of(Cell.ELEMENT));
        assertTrue(RowSource.of(Cell.ELEMENT).marked());
        assertFalse(RowSource.of(Cell.BASE).marked());
        assertFalse(RowSource.of(Cell.SHIPPED).marked());
        assertFalse(RowSource.of(null).marked());
    }

    @Test
    public void aRowsDotFollowsTheElementsOwnCell() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        EditorChips dock = new EditorChips(Element.DOCK);
        assertFalse(source(grid, dock, Row.OPACITY).marked());
        grid.write(Property.OPACITY, null, 40);
        assertFalse("Global's value is shared", source(grid, dock, Row.OPACITY).marked());
        grid.write(Property.OPACITY, Element.DOCK, 50);
        assertEquals(RowSource.ELEMENT, source(grid, dock, Row.OPACITY));
    }

    @Test
    public void resetThisRowClearsTheChipsCellAlone() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        EditorChips chips = new EditorChips(Element.DOCK);
        grid.write(Property.GRAIN, null, 30);
        grid.write(Property.GRAIN, Element.DOCK, 20);
        LookGrid.Key cell = chips.cell(GlassRows.gridProperty(Row.GRAIN));
        assertTrue("offered while the cell holds a value", grid.has(cell));
        grid.clear(cell);
        assertFalse(source(grid, chips, Row.GRAIN).marked());
        assertEquals(30, grid.value(Property.GRAIN, Element.DOCK));
    }

    private static RowSource source(LookGrid grid, EditorChips chips, Row row) {
        LookGrid.Key cell = chips.cell(GlassRows.gridProperty(row));
        return RowSource.of(grid.resolve(cell).cell);
    }
}
