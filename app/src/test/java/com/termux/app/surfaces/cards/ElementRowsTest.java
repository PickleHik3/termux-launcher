package com.termux.app.surfaces.cards;

import com.termux.app.wall.PaneWallPage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.Element;
import com.termux.app.surfaces.EditorCard;
import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.cards.ElementRows.Item;
import com.termux.app.surfaces.cards.ElementRows.Visibility;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * The Elements list's policy: the rows each screen lists, the card each row's sliders glyph opens
 * (with the bar or section it opens on), and what each eye writes, the same values the element's
 * own card writes.
 */
public class ElementRowsTest {

    private static final List<Item> SHARED = Arrays.asList(Item.STATUS_BAR, Item.APPS_ROW,
        Item.AZ, Item.EXTRA_KEYS, Item.KEYBOARD, Item.SEARCH_FIELD, Item.DRAWER_ICON,
        Item.COMMAND_PALETTE);

    @Test
    public void eachScreenListsTheSharedRowsThenItsOwnContent() {
        List<Item> workspace = ElementRows.rows(PaneWallPage.TERMINAL);
        assertEquals(SHARED, workspace.subList(0, SHARED.size()));
        assertEquals(Item.TERMINAL, workspace.get(workspace.size() - 1));
        assertFalse(workspace.contains(Item.WIDGETS));

        List<Item> home = ElementRows.rows(PaneWallPage.WIDGETS);
        assertEquals(SHARED, home.subList(0, SHARED.size()));
        assertEquals(Item.WIDGETS, home.get(home.size() - 1));
        assertFalse(home.contains(Item.TERMINAL));
    }

    @Test
    public void theTerminalAndTheWidgetsHaveNoEye() {
        assertFalse(Item.TERMINAL.hasEye());
        assertFalse(Item.WIDGETS.hasEye());
        for (Item item : SHARED)
            assertTrue(item + " has an eye", item.hasEye());
    }

    @Test
    public void theSlidersOpenTheElementsCard() {
        assertEquals(EditorCard.STATUS, Item.STATUS_BAR.card);
        assertEquals(EditorCard.KEYBOARD, Item.KEYBOARD.card);
        assertEquals(EditorCard.TERMINAL, Item.TERMINAL.card);
        for (Item bar : new Item[] {Item.APPS_ROW, Item.AZ, Item.EXTRA_KEYS}) {
            assertEquals(EditorCard.DOCK, bar.card);
            assertEquals("the Dock card opens with " + bar + "'s settings expanded", bar.element,
                bar.expandedBar());
            assertNull(bar.section);
        }
        assertEquals(Element.APPS, Item.APPS_ROW.expandedBar());
        assertEquals(Element.AZ, Item.AZ.expandedBar());
        assertEquals(Element.EXTRA_KEYS, Item.EXTRA_KEYS.expandedBar());
        assertNull("the status bar is no Dock bar", Item.STATUS_BAR.expandedBar());
    }

    @Test
    public void theDocksOwnTilesOpenTheDockCardAtItsOptions() {
        for (Item tile : new Item[] {Item.SEARCH_FIELD, Item.DRAWER_ICON, Item.COMMAND_PALETTE}) {
            assertEquals(EditorCard.DOCK, tile.card);
            assertEquals(Row.DOCK_OPTIONS, tile.section);
            assertNull("every bar's settings closed", tile.expandedBar());
            assertTrue(tile.ridesTheDock());
            assertTrue("the Dock card holds the Options " + tile + " scrolls to",
                rowsOf(EditorCard.DOCK).contains(Row.DOCK_OPTIONS));
        }
        assertEquals(EditorCard.HOME, Item.WIDGETS.card);
        assertTrue(rowsOf(EditorCard.HOME).contains(Item.WIDGETS.section));
    }

    @Test
    public void eachEyeWritesWhatItsElementsOwnCardWrites() {
        assertEquals(Visibility.ELEMENT, Item.STATUS_BAR.visibility);
        assertEquals(Element.STATUS, Item.STATUS_BAR.element);
        assertEquals(Visibility.ELEMENT, Item.APPS_ROW.visibility);
        assertEquals(Visibility.ELEMENT, Item.AZ.visibility);
        assertEquals(Visibility.ELEMENT, Item.EXTRA_KEYS.visibility);
        assertEquals(Visibility.KEYBOARD, Item.KEYBOARD.visibility);
        assertEquals(Visibility.SEARCH_FIELD, Item.SEARCH_FIELD.visibility);
        assertEquals(Visibility.DRAWER_TILE, Item.DRAWER_ICON.visibility);
        assertEquals(Visibility.COMMAND_PALETTE_BUTTON, Item.COMMAND_PALETTE.visibility);
        assertEquals(Visibility.NONE, Item.TERMINAL.visibility);
        assertEquals(Visibility.NONE, Item.WIDGETS.visibility);
    }

    @Test
    public void onlyTheScreenCardsHoldTheList() {
        assertEquals(PaneWallPage.WIDGETS, ElementRows.screenOf(EditorCard.HOME));
        assertEquals(PaneWallPage.TERMINAL, ElementRows.screenOf(EditorCard.WORKSPACE));
        EditorRows.Offers offers = new EditorRows.Offers(true, true);
        for (EditorCard card : EditorCard.values()) {
            // The Terminal card's Layout is the Workspace's list (overhaul §2).
            boolean screen = ElementRows.screenOf(card) != null || card == EditorCard.TERMINAL;
            assertEquals(card + " holds the Elements list", screen,
                rowsOf(card).contains(Row.ELEMENTS));
        }
        assertTrue("without Home the Workspace still lists its elements",
            EditorRows.tier(EditorCard.WORKSPACE, new EditorRows.Offers(true, true, false), 3)
                .contains(Row.ELEMENTS));
        assertTrue(EditorRows.tier(EditorCard.HOME, offers, 3).contains(Row.ELEMENTS));
    }

    @Test
    public void theListStandsBeforeTheLinkChips() {
        // ADR 0017 (fb44e0fb2): every glass card's own rows go under More, after Grain and Rim,
        // so the Elements list closes the More section and the link chips follow it.
        for (EditorCard card : new EditorCard[] {EditorCard.HOME, EditorCard.WORKSPACE}) {
            List<Row> rows = rowsOf(card);
            int list = rows.indexOf(Row.ELEMENTS);
            // Overhaul §2: the list is the Layout section's, and no link repeats it.
            assertEquals(card + ": the list is under Layout", Row.SECTION_LAYOUT,
                rows.get(list - 1));
            assertFalse(card + ": no Widgets link", rows.contains(Row.LINK_WIDGETS));
        }
    }

    private static List<Row> rowsOf(EditorCard card) {
        java.util.ArrayList<Row> out = new java.util.ArrayList<>();
        for (EditorRows.Spec spec : EditorRows.rows(card, new EditorRows.Offers(true, true)))
            out.add(spec.row);
        return out;
    }
}
