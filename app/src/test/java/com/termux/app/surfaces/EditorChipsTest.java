package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.LookGrid.Element;
import com.termux.shared.termux.settings.preferences.LookGrid.Property;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The two chips name the cell a card's rows read and write (ADR 0017). */
public class EditorChipsTest {

    private static final int PAGE = 7;

    @Test
    public void anElementsCardOpensEverywhereOnItsElement() {
        EditorChips chips = EditorChips.openFor(EditorCard.DOCK, LookGrid.Kind.HOME);
        assertEquals(LookGrid.NO_PAGE, chips.page);
        assertEquals(Element.DOCK, chips.element);
        assertEquals(Element.TERMINAL, EditorChips.openFor(EditorCard.TERMINAL, LookGrid.Kind.TERMINAL).element);
    }

    @Test
    public void aPageCardOpensOnItsPagesKindOnAllGlass() {
        // No look is kept per page (ADR 0020 §2).
        EditorChips chips = EditorChips.openFor(EditorCard.HOME, LookGrid.Kind.HOME);
        assertEquals(LookGrid.NO_PAGE, chips.page);
        assertEquals(LookGrid.Kind.HOME, chips.kind);
        assertNull(chips.element);
        assertTrue(EditorChips.openFor(EditorCard.WORKSPACE, LookGrid.Kind.TERMINAL).onKind());
    }

    @Test
    public void theAppearanceCardOpensEverywhereOnAllGlassAndHasNoWhatChip() {
        EditorChips chips = EditorChips.openFor(EditorCard.APPEARANCE, LookGrid.Kind.HOME);
        assertFalse(chips.onPage());
        assertNull(chips.element);
        assertTrue(EditorChips.whatOptions(EditorCard.APPEARANCE,
            Arrays.asList(Element.DOCK)).isEmpty());
    }

    @Test
    public void whereOffersEverywhereThenThePagesKindButNoPage() {
        assertEquals(Arrays.asList(EditorChips.Where.EVERYWHERE,
                EditorChips.Where.kind(LookGrid.Kind.HOME)),
            EditorChips.whereOptions(PAGE, LookGrid.Kind.HOME));
        assertEquals(Collections.singletonList(EditorChips.Where.EVERYWHERE),
            EditorChips.whereOptions(PAGE, null));
        assertEquals(Collections.singletonList(EditorChips.Where.EVERYWHERE),
            EditorChips.whereOptions(-1, null));
    }

    @Test
    public void aKindNamesTheKindsCell() {
        EditorChips chips = new EditorChips(EditorChips.Where.kind(LookGrid.Kind.HOME), Element.KEYBOARD);
        assertTrue(chips.onKind());
        assertFalse(chips.onPage());
        assertEquals(EditorChips.Where.kind(LookGrid.Kind.HOME), chips.where());
        LookGrid.Key cell = chips.cell(Property.OPACITY);
        assertEquals(LookGrid.Kind.HOME, cell.kind);
        assertEquals(Element.KEYBOARD, cell.element);
        assertEquals(LookGrid.NO_PAGE, cell.page);
        // Margin and Vibrancy have no kind row: everywhere, greyed as all glass.
        LookGrid.Key margin = chips.cell(Property.MARGIN);
        assertNull(margin.kind);
        assertNull(margin.element);
        assertTrue(chips.elementIgnored(Property.MARGIN));
        assertTrue(new EditorChips(EditorChips.Where.kind(LookGrid.Kind.HOME), null)
            .elementIgnored(Property.VIBRANCY));
        // Picking What keeps the kind; picking a page or Everywhere drops it.
        assertEquals(LookGrid.Kind.HOME, chips.withElement(null).kind);
        assertNull(chips.withPage(PAGE).kind);
        assertNull(chips.withWhere(EditorChips.Where.EVERYWHERE).kind);
        assertFalse(chips.isOpening(EditorCard.KEYBOARD));
        assertFalse(chips.equals(new EditorChips(LookGrid.NO_PAGE, Element.KEYBOARD)));
    }

    @Test
    public void aWriteAndResetAtTheKindCellTouchOnlyTheKindKey() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore(),
            page -> page == PAGE ? LookGrid.Kind.HOME : null, null);
        EditorChips chips = new EditorChips(EditorChips.Where.kind(LookGrid.Kind.HOME), Element.KEYBOARD);
        LookGrid.Key cell = chips.cell(Property.OPACITY);
        grid.write(cell, 60);
        assertTrue(grid.store().contains("look.opacity.keyboard@kind:home"));
        assertEquals(RowSource.ELEMENT_ON_KIND,
            RowSource.of(grid.resolve(Property.OPACITY, Element.KEYBOARD, PAGE).cell));
        assertEquals(RowSource.ELEMENT_ON_KIND, RowSource.of(grid.resolve(cell).cell));
        grid.write(Property.OPACITY, null, LookGrid.Kind.HOME, 20);
        assertEquals(RowSource.KIND,
            RowSource.of(grid.resolve(Property.OPACITY, Element.DOCK, PAGE).cell));
        // "Reset this row" at the kind cell deletes the kind key and nothing else.
        grid.write(Property.OPACITY, Element.KEYBOARD, LookGrid.NO_PAGE, 40);
        grid.clear(cell);
        assertFalse(grid.has(cell));
        assertTrue(grid.has(Property.OPACITY, Element.KEYBOARD, LookGrid.NO_PAGE));
        assertTrue(grid.has(Property.OPACITY, null, LookGrid.Kind.HOME));
    }

    @Test
    public void whatOffersAllGlassThenTheElement() {
        assertEquals(Arrays.asList(null, Element.KEYBOARD),
            EditorChips.whatOptions(EditorCard.KEYBOARD, Arrays.asList(Element.DOCK)));
        List<Element> page = Arrays.asList(Element.STATUS, Element.DOCK);
        assertEquals(Arrays.asList(null, Element.STATUS, Element.DOCK),
            EditorChips.whatOptions(EditorCard.HOME, page));
    }

    @Test
    public void theCellIsThePageAndTheElement() {
        LookGrid.Key cell = new EditorChips(PAGE, Element.DOCK).cell(Property.OPACITY);
        assertEquals(Property.OPACITY, cell.property);
        assertEquals(Element.DOCK, cell.element);
        assertEquals(PAGE, cell.page);
        assertNull(new EditorChips(PAGE, null).cell(Property.TINT).element);
    }

    @Test
    public void aPropertyWithNoElementColumnDropsTheElement() {
        EditorChips chips = new EditorChips(LookGrid.NO_PAGE, Element.DOCK);
        assertNull(chips.cell(Property.MARGIN).element);
        assertNull(chips.cell(Property.VIBRANCY).element);
        assertTrue(chips.elementIgnored(Property.MARGIN));
        assertFalse(chips.elementIgnored(Property.FROST));
        assertFalse(new EditorChips(PAGE, null).elementIgnored(Property.MARGIN));
    }

    @Test
    public void openingIsWhereTheCardOpens() {
        assertTrue(EditorChips.openFor(EditorCard.DOCK, LookGrid.Kind.HOME).isOpening(EditorCard.DOCK));
        assertFalse(new EditorChips(PAGE, Element.DOCK).isOpening(EditorCard.DOCK));
        assertFalse(new EditorChips(LookGrid.NO_PAGE, null).isOpening(EditorCard.DOCK));
    }

    @Test
    public void aWriteAtTheChipsCellIsReadBackFromThatCell() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore(),
            page -> page == PAGE ? LookGrid.Kind.HOME : null, null);
        LookGrid.Key cell = new EditorChips(EditorChips.Where.kind(LookGrid.Kind.HOME), Element.DOCK)
            .cell(Property.OPACITY);
        grid.write(cell, 40);
        assertEquals("a Home page reads the Home kind's cell", LookGrid.Cell.ELEMENT_ON_KIND,
            grid.resolve(Property.OPACITY, Element.DOCK, PAGE).cell);
        assertFalse("the element everywhere holds nothing",
            grid.has(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE));
    }
}
