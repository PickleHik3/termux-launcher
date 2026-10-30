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
        EditorChips chips = EditorChips.openFor(EditorCard.DOCK, PAGE);
        assertEquals(LookGrid.NO_PAGE, chips.page);
        assertEquals(Element.DOCK, chips.element);
        assertEquals(Element.TERMINAL, EditorChips.openFor(EditorCard.TERMINAL, PAGE).element);
    }

    @Test
    public void aPageCardOpensOnItsPageOnAllGlass() {
        EditorChips chips = EditorChips.openFor(EditorCard.HOME, PAGE);
        assertEquals(PAGE, chips.page);
        assertNull(chips.element);
        assertTrue(EditorChips.openFor(EditorCard.WORKSPACE, PAGE).onPage());
    }

    @Test
    public void theAppearanceCardOpensEverywhereOnAllGlassAndHasNoWhatChip() {
        EditorChips chips = EditorChips.openFor(EditorCard.APPEARANCE, PAGE);
        assertFalse(chips.onPage());
        assertNull(chips.element);
        assertTrue(EditorChips.whatOptions(EditorCard.APPEARANCE,
            Arrays.asList(Element.DOCK)).isEmpty());
    }

    @Test
    public void whereOffersEverywhereThenThePage() {
        assertEquals(Arrays.asList(LookGrid.NO_PAGE, PAGE), EditorChips.whereOptions(PAGE));
        assertEquals(Collections.singletonList(LookGrid.NO_PAGE), EditorChips.whereOptions(-1));
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
        assertTrue(EditorChips.openFor(EditorCard.DOCK, PAGE).isOpening(EditorCard.DOCK));
        assertFalse(new EditorChips(PAGE, Element.DOCK).isOpening(EditorCard.DOCK));
        assertFalse(new EditorChips(LookGrid.NO_PAGE, null).isOpening(EditorCard.DOCK));
    }

    @Test
    public void aWriteAtTheChipsCellIsReadBackFromThatCell() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        LookGrid.Key cell = new EditorChips(PAGE, Element.DOCK).cell(Property.OPACITY);
        grid.write(cell.property, cell.element, cell.page, 40);
        assertEquals(LookGrid.Cell.ELEMENT_ON_PAGE,
            grid.resolve(Property.OPACITY, Element.DOCK, PAGE).cell);
        assertFalse("the element everywhere holds nothing",
            grid.has(Property.OPACITY, Element.DOCK, LookGrid.NO_PAGE));
    }
}
