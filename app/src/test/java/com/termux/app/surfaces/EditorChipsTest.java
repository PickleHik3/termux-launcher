package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.surfaces.EditorChips.AppliesTo;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.LookGrid.Element;
import com.termux.shared.termux.settings.preferences.LookGrid.Property;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Card × Applies to names the cells a card's rows write (appearance-cards-overhaul §1). */
public class EditorChipsTest {

    /** The keys a card writes for Opacity at {@code chosen}. */
    private static List<String> written(EditorCard card, AppliesTo chosen) {
        List<String> out = new ArrayList<>();
        for (LookGrid.Key key : EditorChips.openFor(card, chosen).cells(Property.OPACITY))
            out.add(key.text());
        return out;
    }

    @Test
    public void cardTimesAppliesToNamesTheCellsWritten() {
        Object[][] table = {
            // card, Applies to choice, cells written
            {EditorCard.APPEARANCE, null, new String[] {"look.opacity"}},
            {EditorCard.APPEARANCE, AppliesTo.HOME, new String[] {"look.opacity"}},
            {EditorCard.HOME, null, new String[] {"look.opacity@kind:home"}},
            {EditorCard.WORKSPACE, null,
                new String[] {"look.opacity@kind:terminal", "look.opacity@kind:display"}},
            {EditorCard.WIDGETS, null, new String[] {"look.opacity.widgets@kind:home"}},
            {EditorCard.WIDGETS, AppliesTo.WORKSPACE,
                new String[] {"look.opacity.widgets@kind:home"}},
            {EditorCard.TERMINAL, null, new String[] {"look.opacity.terminal@kind:terminal",
                "look.opacity.terminal@kind:display"}},
            {EditorCard.DOCK, null, new String[] {"look.opacity.dock"}},
            {EditorCard.DOCK, AppliesTo.GLOBAL, new String[] {"look.opacity.dock"}},
            {EditorCard.DOCK, AppliesTo.HOME, new String[] {"look.opacity.dock@kind:home"}},
            {EditorCard.DOCK, AppliesTo.WORKSPACE, new String[] {"look.opacity.dock@kind:terminal",
                "look.opacity.dock@kind:display"}},
            {EditorCard.STATUS, AppliesTo.HOME, new String[] {"look.opacity.status@kind:home"}},
            {EditorCard.KEYBOARD, AppliesTo.WORKSPACE, new String[] {
                "look.opacity.keyboard@kind:terminal", "look.opacity.keyboard@kind:display"}},
            {EditorCard.SHEETS, AppliesTo.GLOBAL, new String[] {"look.opacity.sheets"}},
            {EditorCard.SHEETS, AppliesTo.HOME, new String[] {"look.opacity.sheets@kind:home"}},
        };
        for (Object[] row : table) {
            EditorCard card = (EditorCard) row[0];
            AppliesTo chosen = (AppliesTo) row[1];
            assertEquals(card + " at " + chosen, Arrays.asList((String[]) row[2]),
                written(card, chosen));
        }
    }

    @Test
    public void workspaceWritesBothKindsAndReadsTheTerminals() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore(), page -> null, null);
        EditorChips workspace = EditorChips.openFor(EditorCard.WORKSPACE, null);
        for (LookGrid.Key cell : workspace.cells(Property.FROST))
            grid.write(cell, 3);
        assertEquals(3, grid.value(Property.FROST, null, LookGrid.Kind.TERMINAL));
        assertEquals(3, grid.value(Property.FROST, null, LookGrid.Kind.DISPLAY));
        assertFalse("Home is left", grid.has(Property.FROST, null, LookGrid.Kind.HOME));
        assertFalse("Base is left", grid.has(Property.FROST, null, LookGrid.NO_PAGE));
        assertEquals(LookGrid.Kind.TERMINAL, workspace.cell(Property.FROST).kind);
    }

    @Test
    public void customizationWritesBaseThatEveryKindShows() {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore(), page -> null, null);
        EditorChips customization = EditorChips.openFor(EditorCard.APPEARANCE, null);
        for (LookGrid.Key cell : customization.cells(Property.TINT))
            grid.write(cell, 42);
        for (LookGrid.Kind kind : LookGrid.Kind.values())
            assertEquals(kind + " follows Base", 42,
                grid.value(Property.TINT, Element.DOCK, kind));
    }

    @Test
    public void vibrancyAndMarginStayBaseWhateverTheCard() {
        for (EditorCard card : EditorCard.values())
            for (Property property : new Property[] {Property.VIBRANCY, Property.MARGIN}) {
                List<LookGrid.Key> cells = EditorChips.openFor(card, AppliesTo.WORKSPACE)
                    .cells(property);
                assertEquals(1, cells.size());
                assertNull(cells.get(0).element);
                assertNull(cells.get(0).kind);
            }
    }

    @Test
    public void onlyTheFourElementCardsOfferAppliesTo() {
        for (EditorCard card : EditorCard.values()) {
            boolean offers = card == EditorCard.STATUS || card == EditorCard.DOCK
                || card == EditorCard.KEYBOARD || card == EditorCard.SHEETS;
            assertEquals(card.name(), offers, EditorChips.offersAppliesTo(card));
            assertEquals(card.name(), offers
                    ? Arrays.asList(AppliesTo.GLOBAL, AppliesTo.HOME, AppliesTo.WORKSPACE)
                    : Collections.emptyList(),
                EditorChips.appliesToOptions(card));
        }
    }

    @Test
    public void anElementCardOpensOnGlobal() {
        EditorChips dock = EditorChips.openFor(EditorCard.DOCK, null);
        assertEquals(AppliesTo.GLOBAL, dock.appliesTo);
        assertEquals(Element.DOCK, dock.element);
        assertFalse(dock.onKind());
        assertTrue(dock.withAppliesTo(AppliesTo.HOME).onKind());
    }

    @Test
    public void elementIgnoredGreysTheBaseOnlyRowsAwayFromCustomization() {
        assertFalse(EditorChips.openFor(EditorCard.APPEARANCE, null)
            .elementIgnored(Property.VIBRANCY));
        assertTrue(EditorChips.openFor(EditorCard.HOME, null).elementIgnored(Property.MARGIN));
        assertTrue(EditorChips.openFor(EditorCard.DOCK, null).elementIgnored(Property.VIBRANCY));
        assertFalse(EditorChips.openFor(EditorCard.DOCK, null).elementIgnored(Property.OPACITY));
    }

    private static List<String> texts(List<LookGrid.Key> keys) {
        List<String> out = new ArrayList<>();
        for (LookGrid.Key key : keys) out.add(key.text());
        return out;
    }

    @Test
    public void resetClearsEveryCellOfTheCardsPlace() {
        List<LookGrid.Key> workspace =
            SurfaceEditorController.resetCells(EditorChips.openFor(EditorCard.WORKSPACE, null));
        assertTrue(texts(workspace).contains("look.opacity@kind:display"));
        assertTrue(texts(workspace).contains("look.opacity@kind:terminal"));
        for (LookGrid.Key key : workspace)
            assertTrue("Base stays: " + key.text(), key.kind != null);
        List<String> customization = texts(
            SurfaceEditorController.resetCells(EditorChips.openFor(EditorCard.APPEARANCE, null)));
        assertTrue(customization.contains("look.vibrancy"));
        assertTrue(customization.contains("look.frost"));
        List<String> dockHome = texts(SurfaceEditorController.resetCells(
            EditorChips.openFor(EditorCard.DOCK, AppliesTo.HOME)));
        assertTrue(dockHome.contains("look.grain.dock@kind:home"));
        assertFalse("Vibrancy is Base's", dockHome.contains("look.vibrancy"));
    }
}
