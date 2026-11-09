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
     * Every combination of the four cells present, opacity on the dock on Home page 4: the first
     * present in read order wins (ADR 0020 §2: the page reads its kind's cells), and with none
     * the shipped value answers.
     */
    @Test
    public void theFirstPresentCellWinsInReadOrder() {
        for (int mask = 0; mask < 16; mask++) {
            LookGrid grid = kindGrid(new LookGrid.MemoryStore());
            boolean elementOnKind = (mask & 1) != 0;
            boolean kind = (mask & 2) != 0;
            boolean element = (mask & 4) != 0;
            boolean base = (mask & 8) != 0;
            if (elementOnKind) grid.write(Property.OPACITY, Element.DOCK, LookGrid.Kind.HOME, 11);
            if (kind) grid.write(Property.OPACITY, null, LookGrid.Kind.HOME, 22);
            if (element) grid.write(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE, 33);
            if (base) grid.write(Property.OPACITY, null, LookGrid.NO_PAGE, 44);
            Resolved resolved = grid.resolve(Property.OPACITY, Element.DOCK, PAGE);
            int expectedValue;
            Cell expectedCell;
            if (elementOnKind) { expectedValue = 11; expectedCell = Cell.ELEMENT_ON_KIND; }
            else if (element) { expectedValue = 33; expectedCell = Cell.ELEMENT; }
            else if (kind) { expectedValue = 22; expectedCell = Cell.KIND; }
            else if (base) { expectedValue = 44; expectedCell = Cell.BASE; }
            else { expectedValue = Property.OPACITY.shipped; expectedCell = Cell.SHIPPED; }
            assertEquals("mask " + mask, expectedValue, resolved.value);
            assertEquals("mask " + mask, expectedCell, resolved.cell);

            // Everywhere (no page): the kind row is never read.
            Resolved wall = grid.resolve(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE);
            if (element) assertEquals(Cell.ELEMENT, wall.cell);
            else if (base) assertEquals(Cell.BASE, wall.cell);
            else assertEquals(Cell.SHIPPED, wall.cell);

            // All glass on the page: the element column is never read.
            Resolved all = grid.resolve(Property.OPACITY, null, PAGE);
            if (kind) assertEquals(Cell.KIND, all.cell);
            else if (base) assertEquals(Cell.BASE, all.cell);
            else assertEquals(Cell.SHIPPED, all.cell);

            // Another kind's page never reads the Home row.
            Resolved terminal = grid.resolve(Property.OPACITY, Element.DOCK, TERMINAL_PAGE);
            if (element) assertEquals(Cell.ELEMENT, terminal.cell);
            else if (base) assertEquals(Cell.BASE, terminal.cell);
            else assertEquals(Cell.SHIPPED, terminal.cell);
        }
    }

    @Test
    public void anotherKindAndAnotherElementDoNotLeak() {
        LookGrid grid = kindGrid(new LookGrid.MemoryStore());
        grid.write(Property.TINT, Element.STATUS, TERMINAL_PAGE, 50);
        grid.write(Property.TINT, null, DISPLAY_PAGE, 60);
        // Another element on the same kind's page.
        assertEquals(Cell.SHIPPED, grid.resolve(Property.TINT, Element.DOCK, TERMINAL_PAGE).cell);
        // The same element on another kind's page.
        assertEquals(Cell.SHIPPED, grid.resolve(Property.TINT, Element.STATUS, HOME_PAGE).cell);
        assertEquals(Cell.SHIPPED,
            grid.resolve(Property.TINT, Element.STATUS, LookGrid.NO_PAGE).cell);
        assertEquals(50, grid.value(Property.TINT, Element.STATUS, TERMINAL_PAGE));
        assertEquals(60, grid.value(Property.TINT, Element.STATUS, DISPLAY_PAGE));
        // One kind, one cell: the other Display page wears it too.
        assertEquals(60, grid.value(Property.TINT, Element.STATUS, UNBOUND_DISPLAY_PAGE));
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
        assertEquals(TermuxPreferenceConstants.TERMUX_APP.MAX_SURFACE_HORIZONTAL_INSET,
            grid.value(Property.MARGIN, null, LookGrid.NO_PAGE));
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
        LookGrid grid = kindGrid(new LookGrid.MemoryStore());
        LookGrid.MemoryStore store = (LookGrid.MemoryStore) grid.store();
        grid.write(Property.OPACITY, null, LookGrid.NO_PAGE, 1);
        grid.write(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE, 2);
        grid.write(Property.OPACITY, Element.DOCK, LookGrid.Kind.HOME, 8);
        // Leftover @<page> cells from before step 17 (no writer makes them now).
        store.putInt(LookGrid.key(Property.OPACITY, null, PAGE), 3);
        store.putInt(LookGrid.key(Property.OPACITY, Element.DOCK, PAGE), 4);
        store.putInt(LookGrid.key(Property.TINT, Element.STATUS, PAGE), 5);
        store.putInt(LookGrid.key(Property.TINT, Element.STATUS, 9), 6);
        store.putInt("unrelated@4", 7);

        grid.clearPage(PAGE);
        assertTrue(grid.has(Property.OPACITY, null, LookGrid.NO_PAGE));
        assertTrue(grid.has(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE));
        assertTrue("the kind's cell is no page's",
            grid.has(Property.OPACITY, Element.DOCK, LookGrid.Kind.HOME));
        assertFalse(store.contains(LookGrid.key(Property.OPACITY, null, PAGE)));
        assertFalse(store.contains(LookGrid.key(Property.OPACITY, Element.DOCK, PAGE)));
        assertFalse(store.contains(LookGrid.key(Property.TINT, Element.STATUS, PAGE)));
        assertTrue(store.contains(LookGrid.key(Property.TINT, Element.STATUS, 9)));
        assertTrue(store.contains("unrelated@4"));

        grid.clearElement(Element.STATUS);
        assertFalse(store.contains(LookGrid.key(Property.TINT, Element.STATUS, 9)));
        grid.clearElement(Element.DOCK);
        assertFalse(grid.has(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE));
        assertFalse(grid.has(Property.OPACITY, Element.DOCK, LookGrid.Kind.HOME));
        assertTrue(grid.has(Property.OPACITY, null, LookGrid.NO_PAGE));

        grid.clear(Property.OPACITY, null, LookGrid.NO_PAGE);
        assertEquals(Cell.SHIPPED, grid.resolve(Property.OPACITY, null, LookGrid.NO_PAGE).cell);
    }

    // ------------------------------------------------------------------------ the kind row

    private static final int HOME_PAGE = 4;
    private static final int DISPLAY_PAGE = 6;
    private static final int UNBOUND_DISPLAY_PAGE = 7;
    private static final int TERMINAL_PAGE = 2;

    /** The wall of these tests: page 4 Home, 2 Terminal, 6 and 7 Display (7 with no window). */
    private static LookGrid kindGrid(LookGrid.MemoryStore store) {
        java.util.Map<Integer, LookGrid.Kind> kinds = new java.util.HashMap<>();
        kinds.put(HOME_PAGE, LookGrid.Kind.HOME);
        kinds.put(TERMINAL_PAGE, LookGrid.Kind.TERMINAL);
        kinds.put(DISPLAY_PAGE, LookGrid.Kind.DISPLAY);
        kinds.put(UNBOUND_DISPLAY_PAGE, LookGrid.Kind.DISPLAY);
        return new LookGrid(store, kinds::get, (property, element, kind) ->
            element == Element.KEYBOARD && kind == LookGrid.Kind.HOME && property == Property.OPACITY
                ? Integer.valueOf(60) : null);
    }

    /** Every combination of the six cells, opacity on the keyboard on a Home page. */
    @Test
    public void anElementBeatsThePagesAllGlass() {
        // The page tier is retired (ADR 0020 §2), and an element beats a page (11-02): the
        // element on the kind, the element everywhere, all glass on the kind, then Base.
        int[] bits = {1, 4, 2, 8};
        Cell[] order = {Cell.ELEMENT_ON_KIND, Cell.ELEMENT, Cell.KIND, Cell.BASE};
        int[] values = {11, 13, 12, 14};
        for (int mask = 0; mask < 16; mask++) {
            LookGrid grid = kindGrid(new LookGrid.MemoryStore());
            if ((mask & 1) != 0) grid.write(Property.OPACITY, Element.KEYBOARD, LookGrid.Kind.HOME, 11);
            if ((mask & 2) != 0) grid.write(Property.OPACITY, null, LookGrid.Kind.HOME, 12);
            if ((mask & 4) != 0) grid.write(Property.OPACITY, Element.KEYBOARD, LookGrid.NO_PAGE, 13);
            if ((mask & 8) != 0) grid.write(Property.OPACITY, null, LookGrid.NO_PAGE, 14);
            // A leftover @<page> cell of the page itself is never read.
            grid.store().putInt(LookGrid.key(Property.OPACITY, Element.KEYBOARD, HOME_PAGE), 99);
            Resolved got = grid.resolve(Property.OPACITY, Element.KEYBOARD, HOME_PAGE);
            int first = -1;
            for (int i = 0; i < bits.length && first < 0; i++)
                if ((mask & bits[i]) != 0) first = i;
            if (mask == 0) {
                assertEquals(Cell.SHIPPED, got.cell);
                assertEquals(60, got.value);
            } else {
                assertEquals("mask " + mask, order[first], got.cell);
                assertEquals("mask " + mask, values[first], got.value);
            }
        }
    }

    @Test
    public void aDocksGlobalValueBeatsTheHomeCardAndItsHomeValueBeatsBoth() {
        LookGrid grid = kindGrid(new LookGrid.MemoryStore());
        grid.write(Property.OPACITY, null, LookGrid.Kind.HOME, 70);
        grid.write(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE, 30);
        Resolved dock = grid.resolve(Property.OPACITY, Element.DOCK, LookGrid.Kind.HOME);
        assertEquals(30, dock.value);
        assertEquals(Cell.ELEMENT, dock.cell);
        assertEquals("the status bar wears the Home card", 70,
            grid.value(Property.OPACITY, Element.STATUS, LookGrid.Kind.HOME));
        grid.write(Property.OPACITY, Element.DOCK, LookGrid.Kind.HOME, 45);
        assertEquals(45, grid.value(Property.OPACITY, Element.DOCK, LookGrid.Kind.HOME));
        assertEquals(Cell.ELEMENT_ON_KIND,
            grid.resolve(Property.OPACITY, Element.DOCK, LookGrid.Kind.HOME).cell);
    }

    @Test
    public void aKindCellHoldsOnlyForItsKind() {
        LookGrid grid = kindGrid(new LookGrid.MemoryStore());
        grid.write(Property.OPACITY, Element.KEYBOARD, LookGrid.Kind.HOME, 0);
        assertEquals(0, grid.value(Property.OPACITY, Element.KEYBOARD, HOME_PAGE));
        Resolved terminal = grid.resolve(Property.OPACITY, Element.KEYBOARD, TERMINAL_PAGE);
        assertEquals(Cell.SHIPPED, terminal.cell);
        assertEquals(Property.OPACITY.shipped, terminal.value);
        // The wall itself and everywhere have no kind: the Terminal's shipped look.
        assertEquals(Property.OPACITY.shipped,
            grid.value(Property.OPACITY, Element.KEYBOARD, LookGrid.NO_PAGE));
        Resolved atKind = grid.resolve(Property.OPACITY, Element.KEYBOARD, LookGrid.Kind.HOME);
        assertEquals(Cell.ELEMENT_ON_KIND, atKind.cell);
        assertEquals(0, atKind.value);
    }

    @Test
    public void anUnboundDisplayPageReadsTheDisplayKindNotTheTerminalPage() {
        LookGrid grid = kindGrid(new LookGrid.MemoryStore());
        grid.write(Property.OPACITY, Element.KEYBOARD, TERMINAL_PAGE, 43);
        grid.write(Property.OPACITY, Element.KEYBOARD, LookGrid.Kind.DISPLAY, 10);
        Resolved got = grid.resolve(Property.OPACITY, Element.KEYBOARD, UNBOUND_DISPLAY_PAGE);
        assertEquals(Cell.ELEMENT_ON_KIND, got.cell);
        assertEquals(10, got.value);
    }

    @Test
    public void theShippedValueIsTheKinds() {
        LookGrid grid = kindGrid(new LookGrid.MemoryStore());
        assertEquals(60, grid.value(Property.OPACITY, Element.KEYBOARD, HOME_PAGE));
        assertEquals(60, grid.value(Property.OPACITY, Element.KEYBOARD, LookGrid.Kind.HOME));
        assertEquals(Property.OPACITY.shipped, grid.value(Property.OPACITY, Element.DOCK, HOME_PAGE));
        // Base wins over the shipped value, as it does over every shipped value.
        grid.write(Property.OPACITY, null, LookGrid.NO_PAGE, 30);
        assertEquals(30, grid.value(Property.OPACITY, Element.KEYBOARD, HOME_PAGE));
    }

    @Test
    public void kindKeysParseBackAndNeverNameAPageToo() {
        String key = LookGrid.key(Property.OPACITY, Element.KEYBOARD, LookGrid.Kind.HOME);
        assertEquals("look.opacity.keyboard@kind:home", key);
        LookGrid.Key parsed = LookGrid.parse(key);
        assertEquals(Property.OPACITY, parsed.property);
        assertEquals(Element.KEYBOARD, parsed.element);
        assertEquals(LookGrid.Kind.HOME, parsed.kind);
        assertEquals(LookGrid.NO_PAGE, parsed.page);
        assertEquals(key, parsed.text());
        assertEquals(LookGrid.Kind.DISPLAY, LookGrid.parse("look.frost@kind:display").kind);
        assertNull(LookGrid.parse("look.opacity@kind:sofa"));
        assertNull(LookGrid.parse("look.opacity@kind:"));
        assertNull(LookGrid.parse("look.vibrancy@kind:home"));
        assertNull(LookGrid.parse("look.opacity@kind:home@4"));
        // Margin has a kind row since 2026-11-04 (Home's and the Workspace's); Vibrancy none.
        assertEquals("look.margin@kind:home", LookGrid.key(Property.MARGIN, null, LookGrid.Kind.HOME));
        assertEquals(LookGrid.Kind.HOME, LookGrid.parse("look.margin@kind:home").kind);
        try {
            LookGrid.key(Property.VIBRANCY, null, LookGrid.Kind.HOME);
            fail("vibrancy has no kind row");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void vibrancyIgnoresTheKind() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        store.putInt("look.vibrancy@kind:home", 5);
        LookGrid grid = kindGrid(store);
        assertEquals(Cell.SHIPPED, grid.resolve(Property.VIBRANCY, null, HOME_PAGE).cell);
    }

    @Test
    public void marginIsReadPerKindThenBase() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        LookGrid grid = kindGrid(store);
        grid.write(Property.MARGIN, null, LookGrid.NO_PAGE, 8);
        for (int page : new int[] {HOME_PAGE, TERMINAL_PAGE, DISPLAY_PAGE})
            assertEquals("Base on page " + page, 8, grid.value(Property.MARGIN, null, page));
        grid.write(Property.MARGIN, null, LookGrid.Kind.TERMINAL, 20);
        grid.write(Property.MARGIN, null, LookGrid.Kind.DISPLAY, 20);
        assertEquals(Cell.KIND, grid.resolve(Property.MARGIN, null, TERMINAL_PAGE).cell);
        assertEquals(20, grid.value(Property.MARGIN, null, TERMINAL_PAGE));
        assertEquals(20, grid.value(Property.MARGIN, Element.DOCK, DISPLAY_PAGE));
        assertEquals("Home still follows Base", 8, grid.value(Property.MARGIN, null, HOME_PAGE));
        assertEquals("everywhere reads Base", 8,
            grid.value(Property.MARGIN, null, LookGrid.NO_PAGE));
        // A page's write lands on its kind's row.
        grid.write(Property.MARGIN, null, HOME_PAGE, 4);
        assertTrue(store.contains("look.margin@kind:home"));
        assertEquals(4, grid.value(Property.MARGIN, null, HOME_PAGE));
    }

    @Test
    public void anExistingSingleMarginLooksTheSameOnEveryKind() {
        // An install from before the kind row holds only Base (and maybe an old page key, which
        // is not read and not folded): every kind resolves to Base, as before.
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        store.putInt("look.margin", 12);
        store.putInt("look.margin@" + HOME_PAGE, 30);
        LookGrid grid = kindGrid(store);
        for (int page : new int[] {HOME_PAGE, TERMINAL_PAGE, DISPLAY_PAGE, LookGrid.NO_PAGE})
            assertEquals("page " + page, 12, grid.value(Property.MARGIN, null, page));
        assertTrue("no kind row is made",
            LookGrid.foldedPageCells(java.util.Collections.singletonMap("look.margin@" + HOME_PAGE,
                (Object) 30), HOME_PAGE, TERMINAL_PAGE).isEmpty());
    }

    @Test
    public void aKindCellIsSetAndResetByItsKey() {
        LookGrid grid = kindGrid(new LookGrid.MemoryStore());
        LookGrid.Key cell = new LookGrid.Key(Property.FROST, Element.KEYBOARD, LookGrid.Kind.HOME);
        assertFalse(grid.has(cell));
        grid.write(cell, 99);
        assertTrue(grid.has(cell));
        assertEquals(Property.FROST.max, grid.value(cell));
        grid.clear(cell);
        assertFalse(grid.has(cell));
        grid.write(cell, 1);
        grid.clearElement(Element.KEYBOARD);
        assertFalse(grid.has(cell));
    }

    @Test
    public void anElementHoldsItsOwnValueOnAPageThroughItsKind() {
        // The keyboard's "linked" state: it follows all glass only while no cell of its own holds
        // for the page, the page's kind, or everywhere (the kind cell was skipped, 2026-10-31).
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        LookGrid grid = kindGrid(store);
        assertFalse(grid.holdsOwn(Property.OPACITY, Element.KEYBOARD, HOME_PAGE));
        grid.write(Property.OPACITY, Element.KEYBOARD, LookGrid.Kind.HOME, 70);
        assertTrue("the Home kind's cell is the keyboard's own on a Home page",
            grid.holdsOwn(Property.OPACITY, Element.KEYBOARD, HOME_PAGE));
        assertFalse("not on the Terminal page", grid.holdsOwn(Property.OPACITY, Element.KEYBOARD, TERMINAL_PAGE));
        grid.clear(Property.OPACITY, Element.KEYBOARD, LookGrid.Kind.HOME);
        grid.write(Property.OPACITY, Element.KEYBOARD, LookGrid.NO_PAGE, 50);
        assertTrue("everywhere counts on every page", grid.holdsOwn(Property.OPACITY, Element.KEYBOARD, TERMINAL_PAGE));
        assertFalse("all glass never holds an element's own", grid.holdsOwn(Property.OPACITY, null, HOME_PAGE));
        assertFalse("Margin has no element", grid.holdsOwn(Property.MARGIN, Element.KEYBOARD, HOME_PAGE));
    }
}
