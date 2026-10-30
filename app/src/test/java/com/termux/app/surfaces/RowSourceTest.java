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

    private static final int PAGE = 3;

    @Test
    public void theDotIsColouredByTheCellAndAbsentForASharedValue() {
        assertEquals(RowSource.ELEMENT_ON_PAGE, RowSource.of(Cell.ELEMENT_ON_PAGE));
        assertEquals(RowSource.PAGE, RowSource.of(Cell.PAGE));
        assertEquals(RowSource.ELEMENT, RowSource.of(Cell.ELEMENT));
        assertFalse(RowSource.of(Cell.BASE).marked());
        assertFalse(RowSource.of(Cell.SHIPPED).marked());
        assertFalse(RowSource.of(null).marked());
    }

    @Test
    public void aRowsDotFollowsWhereTheChipsReadIt() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        EditorChips dock = new EditorChips(LookGrid.NO_PAGE, Element.DOCK);
        assertFalse(source(grid, dock, Row.OPACITY).marked());
        grid.write(Property.OPACITY, null, PAGE, 40);
        assertEquals(RowSource.PAGE, source(grid, dock.withPage(PAGE), Row.OPACITY));
        grid.write(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE, 50);
        assertEquals("the page wins over the element everywhere (ADR 0017)", RowSource.PAGE,
            source(grid, dock.withPage(PAGE), Row.OPACITY));
        assertEquals(RowSource.ELEMENT, source(grid, dock, Row.OPACITY));
        grid.write(Property.OPACITY, Element.DOCK, PAGE, 60);
        assertEquals(RowSource.ELEMENT_ON_PAGE, source(grid, dock.withPage(PAGE), Row.OPACITY));
    }

    @Test
    public void resetThisRowClearsTheChipsCellAlone() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        EditorChips chips = new EditorChips(PAGE, Element.DOCK);
        grid.write(Property.GRAIN, Element.DOCK, PAGE, 30);
        grid.write(Property.GRAIN, Element.DOCK, LookGrid.NO_PAGE, 20);
        LookGrid.Key cell = chips.cell(GlassRows.gridProperty(Row.GRAIN));
        assertTrue("offered while the cell holds a value", grid.has(cell.property, cell.element,
            cell.page));
        grid.clear(cell.property, cell.element, cell.page);
        assertEquals(RowSource.ELEMENT, source(grid, chips, Row.GRAIN));
        assertEquals(20, grid.value(Property.GRAIN, Element.DOCK, PAGE));
    }

    private static RowSource source(LookGrid grid, EditorChips chips, Row row) {
        LookGrid.Key cell = chips.cell(GlassRows.gridProperty(row));
        return RowSource.of(grid.resolve(cell.property, cell.element, cell.page).cell);
    }
}
