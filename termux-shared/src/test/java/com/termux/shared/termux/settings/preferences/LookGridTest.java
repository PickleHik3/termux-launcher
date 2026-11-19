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

/** The look grid in two places, Global and an element's own (ADR 0031). */
public class LookGridTest {

    /** Every combination of the two cells, opacity on the dock: the element's own, Global, shipped. */
    @Test
    public void theElementsOwnBeatsGlobalWhichBeatsShipped() {
        for (int mask = 0; mask < 4; mask++) {
            LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
            boolean element = (mask & 1) != 0;
            boolean base = (mask & 2) != 0;
            if (element) grid.write(Property.OPACITY, Element.DOCK, 33);
            if (base) grid.write(Property.OPACITY, null, 44);
            Resolved resolved = grid.resolve(Property.OPACITY, Element.DOCK);
            if (element) {
                assertEquals(33, resolved.value);
                assertEquals(Cell.ELEMENT, resolved.cell);
            } else if (base) {
                assertEquals(44, resolved.value);
                assertEquals(Cell.BASE, resolved.cell);
            } else {
                assertEquals(Property.OPACITY.shipped, resolved.value);
                assertEquals(Cell.SHIPPED, resolved.cell);
            }
            // All glass never reads an element's column.
            assertEquals(base ? Cell.BASE : Cell.SHIPPED, grid.resolve(Property.OPACITY, null).cell);
        }
    }

    /**
     * The chrome wears one look on every page: a page's or a kind's cell an older build wrote is
     * never read, for any element or all glass.
     */
    @Test
    public void aPagesOrAKindsOldCellIsNeverRead() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        for (Element element : Element.values()) {
            store.putInt(LookGrid.key(Property.OPACITY, element, LookGrid.Kind.HOME), 11);
            store.putInt(LookGrid.key(Property.OPACITY, element, 4), 12);
        }
        store.putInt(LookGrid.key(Property.OPACITY, null, LookGrid.Kind.TERMINAL), 13);
        store.putInt(LookGrid.key(Property.OPACITY, null, 2), 14);
        store.putInt(LookGrid.key(Property.MARGIN, null, LookGrid.Kind.HOME), 15);
        LookGrid grid = new LookGrid(store);
        grid.write(Property.OPACITY, null, 40);
        for (Element element : Element.values()) {
            assertEquals(element.key, 40, grid.value(Property.OPACITY, element));
            assertFalse(element.key, grid.holdsOwn(Property.OPACITY, element));
        }
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP,
            grid.value(Property.MARGIN, null));
    }

    @Test
    public void writesAndReadsClamp() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        grid.write(Property.OPACITY, null, 140);
        assertEquals(100, grid.value(Property.OPACITY, null));
        grid.write(Property.FROST, Element.DOCK, 12);
        assertEquals(FrostStops.LAST, grid.value(Property.FROST, Element.DOCK));
        grid.write(Property.VIBRANCY, null, 10);
        assertEquals(70, grid.value(Property.VIBRANCY, null));
        grid.write(Property.CORNERS, null, -9);
        assertEquals(LookGrid.CORNERS_AUTO, grid.value(Property.CORNERS, null));
        // A stored value out of range (a hand edit) is clamped on the read.
        grid.store().putInt("look.margin", 400);
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.MAX_SURFACE_HORIZONTAL_INSET,
            grid.value(Property.MARGIN, null));
    }

    @Test
    public void grainFollowsGlobalsFrostStopUntilSet() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        assertEquals(FrostStops.grainPercentFor(FrostStops.CLEAR),
            grid.value(Property.GRAIN, Element.DOCK));
        grid.write(Property.FROST, null, FrostStops.MIST);
        assertEquals(FrostStops.grainPercentFor(FrostStops.MIST),
            grid.value(Property.GRAIN, Element.DOCK));
        grid.write(Property.GRAIN, null, 18);
        assertEquals(18, grid.value(Property.GRAIN, Element.DOCK));
        assertEquals(Cell.BASE, grid.resolve(Property.GRAIN, Element.DOCK).cell);
    }

    /**
     * A surface holding its own Blur wears that stop's grain, over Global's Grain (developer,
     * 2026-11-19); a surface following Global's Blur wears Global's Grain.
     */
    @Test
    public void aSurfacesOwnBlurBringsItsGrain() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        grid.write(Property.GRAIN, null, 18);
        grid.write(Property.FROST, Element.KEYBOARD, FrostStops.MIST);
        assertEquals(FrostStops.grainPercentFor(FrostStops.MIST),
            grid.value(Property.GRAIN, Element.KEYBOARD));
        assertEquals(Cell.ELEMENT, grid.resolve(Property.GRAIN, Element.KEYBOARD).cell);
        assertEquals(18, grid.value(Property.GRAIN, Element.DOCK));
        grid.clear(Property.FROST, Element.KEYBOARD);
        assertEquals(18, grid.value(Property.GRAIN, Element.KEYBOARD));
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
        assertEquals("look.opacity", LookGrid.key(Property.OPACITY, null));
        assertEquals("look.frost.dock", LookGrid.key(Property.FROST, Element.DOCK));
        assertEquals("look.frost.dock@3", LookGrid.key(Property.FROST, Element.DOCK, 3));
    }

    private static void check(Property property, Element element, int page) {
        LookGrid.Key key = LookGrid.parse(LookGrid.key(property, element, page));
        assertEquals(property, key.property);
        assertEquals(element, key.element);
        assertEquals(page, key.page);
    }

    /** An older build's kind keys still parse, so the reset that deletes them finds them. */
    @Test
    public void oldKindKeysParseBack() {
        String key = LookGrid.key(Property.OPACITY, Element.KEYBOARD, LookGrid.Kind.HOME);
        assertEquals("look.opacity.keyboard@kind:home", key);
        LookGrid.Key parsed = LookGrid.parse(key);
        assertEquals(Property.OPACITY, parsed.property);
        assertEquals(Element.KEYBOARD, parsed.element);
        assertEquals(LookGrid.Kind.HOME, parsed.kind);
        assertEquals(LookGrid.NO_PAGE, parsed.page);
        assertEquals(key, parsed.text());
        assertEquals(LookGrid.Kind.HOME, LookGrid.parse("look.margin@kind:home").kind);
        assertNull(LookGrid.parse("look.opacity@kind:sofa"));
        assertNull(LookGrid.parse("look.vibrancy@kind:home"));
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
                LookGrid.key(property, Element.DOCK);
                fail(property + " took an element");
            } catch (IllegalArgumentException expected) { }
        }
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        grid.write(Property.MARGIN, null, 20);
        // Resolving with an element reads Global.
        assertEquals(20, grid.value(Property.MARGIN, Element.DOCK));
    }

    @Test
    public void clearPageAndClearElementDropOnlyTheirCells() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        LookGrid grid = new LookGrid(store);
        grid.write(Property.OPACITY, null, 1);
        grid.write(Property.OPACITY, Element.DOCK, 2);
        store.putInt(LookGrid.key(Property.OPACITY, Element.DOCK, LookGrid.Kind.HOME), 8);
        store.putInt(LookGrid.key(Property.OPACITY, null, 4), 3);
        store.putInt(LookGrid.key(Property.TINT, Element.STATUS, 4), 5);
        store.putInt(LookGrid.key(Property.TINT, Element.STATUS, 9), 6);
        store.putInt("unrelated@4", 7);

        grid.clearPage(4);
        assertTrue(grid.has(Property.OPACITY, null));
        assertTrue(grid.has(Property.OPACITY, Element.DOCK));
        assertFalse(store.contains(LookGrid.key(Property.OPACITY, null, 4)));
        assertFalse(store.contains(LookGrid.key(Property.TINT, Element.STATUS, 4)));
        assertTrue(store.contains(LookGrid.key(Property.TINT, Element.STATUS, 9)));
        assertTrue(store.contains("unrelated@4"));

        grid.clearElement(Element.STATUS);
        assertFalse(store.contains(LookGrid.key(Property.TINT, Element.STATUS, 9)));
        grid.clearElement(Element.DOCK);
        assertFalse(grid.has(Property.OPACITY, Element.DOCK));
        assertFalse(store.contains(LookGrid.key(Property.OPACITY, Element.DOCK, LookGrid.Kind.HOME)));
        assertTrue(grid.has(Property.OPACITY, null));

        grid.clear(Property.OPACITY, null);
        assertEquals(Cell.SHIPPED, grid.resolve(Property.OPACITY, null).cell);
    }
}
