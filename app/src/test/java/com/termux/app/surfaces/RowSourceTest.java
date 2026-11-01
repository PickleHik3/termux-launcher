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
        LookGrid grid = homeGrid();
        EditorChips dock = new EditorChips(LookGrid.NO_PAGE, Element.DOCK);
        EditorChips onHome = dock.withWhere(EditorChips.Where.kind(LookGrid.Kind.HOME));
        assertFalse(source(grid, dock, Row.OPACITY).marked());
        grid.write(Property.OPACITY, null, PAGE, 40);
        assertEquals("a page's value is its kind's (ADR 0020 §2)", RowSource.KIND,
            source(grid, onHome, Row.OPACITY));
        grid.write(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE, 50);
        assertEquals("the kind wins over the element everywhere", RowSource.KIND,
            source(grid, onHome, Row.OPACITY));
        assertEquals(RowSource.ELEMENT, source(grid, dock, Row.OPACITY));
        grid.write(Property.OPACITY, Element.DOCK, PAGE, 60);
        assertEquals(RowSource.ELEMENT_ON_KIND, source(grid, onHome, Row.OPACITY));
    }

    @Test
    public void resetThisRowClearsTheChipsCellAlone() {
        LookGrid grid = homeGrid();
        EditorChips chips = new EditorChips(EditorChips.Where.kind(LookGrid.Kind.HOME), Element.DOCK);
        grid.write(Property.GRAIN, Element.DOCK, PAGE, 30);
        grid.write(Property.GRAIN, Element.DOCK, LookGrid.NO_PAGE, 20);
        LookGrid.Key cell = chips.cell(GlassRows.gridProperty(Row.GRAIN));
        assertTrue("offered while the cell holds a value", grid.has(cell));
        grid.clear(cell);
        assertEquals(RowSource.ELEMENT, source(grid, chips, Row.GRAIN));
        assertEquals(20, grid.value(Property.GRAIN, Element.DOCK, PAGE));
    }

    /** A grid whose page {@link #PAGE} is a Home page. */
    private static LookGrid homeGrid() {
        return new LookGrid(new LookGrid.MemoryStore(),
            page -> page == PAGE ? LookGrid.Kind.HOME : null, null);
    }

    private static RowSource source(LookGrid grid, EditorChips chips, Row row) {
        LookGrid.Key cell = chips.cell(GlassRows.gridProperty(row));
        return RowSource.of(grid.resolve(cell).cell);
    }
}
