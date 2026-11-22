package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.LookGrid.Element;
import com.termux.shared.termux.settings.preferences.LookGrid.Property;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The cell a card's glass rows write: its element's own, or Global's (ADR 0031). No card names a
 * page or a kind of page.
 */
public class EditorChipsTest {

    @Test
    public void everyCardWritesItsElementsCellOrGlobals() {
        for (EditorCard card : EditorCard.values()) {
            LookGrid.Key cell = EditorChips.openFor(card).cell(Property.OPACITY);
            assertNull(card + " names no kind", cell.kind);
            assertEquals(card + " names no page", LookGrid.NO_PAGE, cell.page);
            assertEquals(card.name(), EditorChips.elementOf(card), cell.element);
        }
        assertEquals("look.opacity", EditorChips.openFor(EditorCard.APPEARANCE)
            .cell(Property.OPACITY).text());
        assertEquals("look.opacity.dock", EditorChips.openFor(EditorCard.DOCK)
            .cell(Property.OPACITY).text());
        assertEquals("the page cards' glass is Global's", "look.opacity",
            EditorChips.openFor(EditorCard.HOME).cell(Property.OPACITY).text());
    }

    @Test
    public void vibrancyAndMarginAreGlobalsWhateverTheCard() {
        for (EditorCard card : EditorCard.values()) {
            assertEquals("look.vibrancy", EditorChips.openFor(card).cell(Property.VIBRANCY).text());
            assertEquals("look.margin", EditorChips.openFor(card).cell(Property.MARGIN).text());
        }
    }

    @Test
    public void elementIgnoredGreysGlobalsOnlyRowsOnAnElementsCard() {
        assertFalse(EditorChips.openFor(EditorCard.APPEARANCE).elementIgnored(Property.VIBRANCY));
        assertTrue(EditorChips.openFor(EditorCard.DOCK).elementIgnored(Property.MARGIN));
        assertTrue(EditorChips.openFor(EditorCard.DOCK).elementIgnored(Property.VIBRANCY));
        assertFalse(EditorChips.openFor(EditorCard.DOCK).elementIgnored(Property.OPACITY));
    }

    private static List<String> texts(List<LookGrid.Key> keys) {
        List<String> out = new ArrayList<>();
        for (LookGrid.Key key : keys) out.add(key.text());
        return out;
    }

    @Test
    public void resetClearsTheCardsCells() {
        List<String> global = texts(
            SurfaceEditorController.resetCells(EditorChips.openFor(EditorCard.APPEARANCE)));
        assertTrue(global.contains("look.vibrancy"));
        assertTrue(global.contains("look.frost"));
        List<String> dock = texts(
            SurfaceEditorController.resetCells(EditorChips.openFor(EditorCard.DOCK)));
        assertTrue(dock.contains("look.grain.dock"));
        assertFalse("Vibrancy is Global's", dock.contains("look.vibrancy"));
        for (String key : dock) assertFalse(key, key.contains("@"));
    }

    @Test
    public void aDockCardStandsOnTheDock() {
        EditorChips dock = EditorChips.openFor(EditorCard.DOCK);
        assertEquals(Element.DOCK, dock.element);
    }
}
