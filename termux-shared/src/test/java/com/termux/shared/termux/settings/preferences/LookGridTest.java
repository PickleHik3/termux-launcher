package com.termux.shared.termux.settings.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.termux.shared.termux.settings.preferences.LookGrid.Cell;
import com.termux.shared.termux.settings.preferences.LookGrid.Element;
import com.termux.shared.termux.settings.preferences.LookGrid.Property;
import com.termux.shared.termux.settings.preferences.LookGrid.Resolved;

import org.junit.Test;

public class LookGridTest {

    private static final int PAGE = 4;

    /**
     * Every combination of the four cells present, opacity on the dock on page 4: the first
     * present in read order wins, and with none the shipped value answers.
     */
    @Test
    public void theFirstPresentCellWinsInReadOrder() {
        for (int mask = 0; mask < 16; mask++) {
            LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
            boolean elementOnPage = (mask & 1) != 0;
            boolean page = (mask & 2) != 0;
            boolean element = (mask & 4) != 0;
            boolean base = (mask & 8) != 0;
            if (elementOnPage) grid.write(Property.OPACITY, Element.DOCK, PAGE, 11);
            if (page) grid.write(Property.OPACITY, null, PAGE, 22);
            if (element) grid.write(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE, 33);
            if (base) grid.write(Property.OPACITY, null, LookGrid.NO_PAGE, 44);
            Resolved resolved = grid.resolve(Property.OPACITY, Element.DOCK, PAGE);
            int expectedValue;
            Cell expectedCell;
            if (elementOnPage) { expectedValue = 11; expectedCell = Cell.ELEMENT_ON_PAGE; }
            else if (page) { expectedValue = 22; expectedCell = Cell.PAGE; }
            else if (element) { expectedValue = 33; expectedCell = Cell.ELEMENT; }
            else if (base) { expectedValue = 44; expectedCell = Cell.BASE; }
            else { expectedValue = Property.OPACITY.shipped; expectedCell = Cell.SHIPPED; }
            assertEquals("mask " + mask, expectedValue, resolved.value);
            assertEquals("mask " + mask, expectedCell, resolved.cell);

            // Everywhere (no page): the page row is never read.
            Resolved wall = grid.resolve(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE);
            if (element) assertEquals(Cell.ELEMENT, wall.cell);
            else if (base) assertEquals(Cell.BASE, wall.cell);
            else assertEquals(Cell.SHIPPED, wall.cell);

            // All glass on the page: the element column is never read.
            Resolved all = grid.resolve(Property.OPACITY, null, PAGE);
            if (page) assertEquals(Cell.PAGE, all.cell);
            else if (base) assertEquals(Cell.BASE, all.cell);
            else assertEquals(Cell.SHIPPED, all.cell);
        }
    }

    @Test
    public void anotherPageAndAnotherElementDoNotLeak() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        grid.write(Property.TINT, Element.STATUS, 7, 50);
        grid.write(Property.TINT, null, 8, 60);
        assertEquals(Cell.SHIPPED, grid.resolve(Property.TINT, Element.DOCK, 7).cell);
        assertEquals(Cell.SHIPPED, grid.resolve(Property.TINT, Element.STATUS, PAGE).cell);
        assertEquals(60, grid.value(Property.TINT, Element.STATUS, 8));
    }

    @Test
    public void writesAndReadsClamp() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        grid.write(Property.OPACITY, null, LookGrid.NO_PAGE, 140);
        assertEquals(100, grid.value(Property.OPACITY, null, LookGrid.NO_PAGE));
        grid.write(Property.FROST, Element.DOCK, LookGrid.NO_PAGE, 12);
        assertEquals(FrostStops.LAST, grid.value(Property.FROST, Element.DOCK, LookGrid.NO_PAGE));
        grid.write(Property.VIBRANCY, null, LookGrid.NO_PAGE, 10);
        assertEquals(70, grid.value(Property.VIBRANCY, null, LookGrid.NO_PAGE));
        grid.write(Property.CORNERS, null, LookGrid.NO_PAGE, -9);
        assertEquals(LookGrid.CORNERS_AUTO, grid.value(Property.CORNERS, null, LookGrid.NO_PAGE));
        // A stored value out of range (a hand edit) is clamped on the read.
        grid.store().putInt("look.margin", 400);
        assertEquals(40, grid.value(Property.MARGIN, null, LookGrid.NO_PAGE));
    }

    @Test
    public void grainFollowsTheFrostStopUntilSet() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        assertEquals(FrostStops.grainPercentFor(FrostStops.CLEAR),
            grid.value(Property.GRAIN, Element.DOCK, PAGE));
        grid.write(Property.FROST, Element.DOCK, LookGrid.NO_PAGE, FrostStops.MIST);
        assertEquals(FrostStops.grainPercentFor(FrostStops.MIST),
            grid.value(Property.GRAIN, Element.DOCK, PAGE));
        grid.write(Property.GRAIN, null, LookGrid.NO_PAGE, 18);
        assertEquals(18, grid.value(Property.GRAIN, Element.DOCK, PAGE));
        assertEquals(Cell.BASE, grid.resolve(Property.GRAIN, Element.DOCK, PAGE).cell);
    }

    @Test
    public void keysRoundTrip() {
        for (Property property : Property.values()) {
            for (int page : new int[] {LookGrid.NO_PAGE, 0, 12}) {
                check(property, null, page);
                if (!property.perElement) continue;
                for (Element element : Element.values()) check(property, element, page);
            }
        }
        assertEquals("look.opacity", LookGrid.key(Property.OPACITY, null, LookGrid.NO_PAGE));
        assertEquals("look.frost.dock@3", LookGrid.key(Property.FROST, Element.DOCK, 3));
        assertEquals("look.tint_hue@0", LookGrid.key(Property.TINT_HUE, null, 0));
    }

    private static void check(Property property, Element element, int page) {
        LookGrid.Key key = LookGrid.parse(LookGrid.key(property, element, page));
        assertEquals(property, key.property);
        assertEquals(element, key.element);
        assertEquals(page, key.page);
    }

    @Test
    public void anythingElseDoesNotParse() {
        for (String key : new String[] {null, "", "look.", "look.blur", "look.opacity.canvas",
            "look.opacity@", "look.opacity@x", "look.opacity@-1", "surface_base_opacity",
            "look.vibrancy.dock", "look.margin.terminal@2", "app_bar_opacity@3"})
            assertNull(key, LookGrid.parse(key));
    }

    @Test
    public void vibrancyAndMarginRefuseAnElement() {
        for (Property property : new Property[] {Property.VIBRANCY, Property.MARGIN}) {
            assertFalse(property.perElement);
            try {
                LookGrid.key(property, Element.DOCK, LookGrid.NO_PAGE);
                fail(property + " took an element");
            } catch (IllegalArgumentException expected) { }
        }
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        grid.write(Property.MARGIN, null, LookGrid.NO_PAGE, 20);
        // Resolving with an element reads the Where level.
        assertEquals(20, grid.value(Property.MARGIN, Element.DOCK, PAGE));
    }

    @Test
    public void clearPageAndClearElementDropOnlyTheirCells() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        grid.write(Property.OPACITY, null, LookGrid.NO_PAGE, 1);
        grid.write(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE, 2);
        grid.write(Property.OPACITY, null, PAGE, 3);
        grid.write(Property.OPACITY, Element.DOCK, PAGE, 4);
        grid.write(Property.TINT, Element.STATUS, PAGE, 5);
        grid.write(Property.TINT, Element.STATUS, 9, 6);
        grid.store().putInt("unrelated@4", 7);

        grid.clearPage(PAGE);
        assertTrue(grid.has(Property.OPACITY, null, LookGrid.NO_PAGE));
        assertTrue(grid.has(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE));
        assertFalse(grid.has(Property.OPACITY, null, PAGE));
        assertFalse(grid.has(Property.OPACITY, Element.DOCK, PAGE));
        assertFalse(grid.has(Property.TINT, Element.STATUS, PAGE));
        assertTrue(grid.has(Property.TINT, Element.STATUS, 9));
        assertTrue(grid.store().contains("unrelated@4"));

        grid.clearElement(Element.STATUS);
        assertFalse(grid.has(Property.TINT, Element.STATUS, 9));
        grid.clearElement(Element.DOCK);
        assertFalse(grid.has(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE));
        assertTrue(grid.has(Property.OPACITY, null, LookGrid.NO_PAGE));

        grid.clear(Property.OPACITY, null, LookGrid.NO_PAGE);
        assertEquals(Cell.SHIPPED, grid.resolve(Property.OPACITY, null, LookGrid.NO_PAGE).cell);
    }
}
