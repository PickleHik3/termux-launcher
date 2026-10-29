package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * The door table (editor-redesign §5, the After column): each door into the editor and the card
 * it opens, and the one line each logs.
 */
public class EditorDoorTest {

    @Test
    public void aScreensDoorOpensThatScreensCard() {
        for (PaneWallPage page : pagesAndNone()) {
            assertEquals(EditorCard.HOME, EditorDoor.HOME_SHEET_APPEARANCE.cardFor(page, null));
            assertEquals("the terminal's sheet opens the Workspace card, not the editor at rest",
                EditorCard.WORKSPACE, EditorDoor.TERMINAL_SHEET_APPEARANCE.cardFor(page, null));
        }
    }

    @Test
    public void theWallpaperThePaletteAndSettingsLookOpenGlobal() {
        for (EditorDoor door : new EditorDoor[] {EditorDoor.HOME_SHEET_WALLPAPER,
                EditorDoor.EDITOR_PALETTE, EditorDoor.EDITOR_GLOBAL_CHIP, EditorDoor.SETTINGS_LOOK,
                EditorDoor.LAUNCHERCTL_WALLPAPER}) {
            for (PaneWallPage page : pagesAndNone())
                assertEquals(door.doorName, EditorCard.APPEARANCE, door.cardFor(page, null));
        }
    }

    @Test
    public void theSettingsRowsOpenTheirObjectsCards() {
        assertEquals(EditorCard.HOME, EditorDoor.SETTINGS_HOME.cardFor(null, null));
        assertEquals(EditorCard.TERMINAL, EditorDoor.SETTINGS_TERMINAL.cardFor(null, null));
        assertEquals(EditorCard.KEYBOARD, EditorDoor.SETTINGS_KEYBOARD.cardFor(null, null));
    }

    @Test
    public void thePageLabelOpensTheCardOfThePageItNames() {
        assertEquals(EditorCard.HOME,
            EditorDoor.EDITOR_PAGE_LABEL.cardFor(PaneWallPage.WIDGETS, null));
        assertEquals(EditorCard.WORKSPACE,
            EditorDoor.EDITOR_PAGE_LABEL.cardFor(PaneWallPage.TERMINAL, null));
        assertEquals(EditorCard.WORKSPACE,
            EditorDoor.EDITOR_PAGE_LABEL.cardFor(PaneWallPage.DISPLAY, null));
    }

    @Test
    public void thePageLedDoorsAreUnchanged() {
        for (EditorDoor door : new EditorDoor[] {EditorDoor.LAUNCHERCTL,
                EditorDoor.TERMINAL_CONTEXT_MENU}) {
            assertEquals(EditorCard.HOME, door.cardFor(PaneWallPage.WIDGETS, null));
            assertEquals(EditorCard.TERMINAL, door.cardFor(PaneWallPage.TERMINAL, null));
            assertNull("at rest over the display", door.cardFor(PaneWallPage.DISPLAY, null));
            assertNull(door.cardFor(null, EditorCard.DOCK));
        }
    }

    @Test
    public void paneControlsOpenTheCardOfTheScreenTheyAreOn() {
        assertEquals(EditorCard.HOME, EditorDoor.PANE_CONTROLS.cardFor(PaneWallPage.WIDGETS, null));
        assertEquals(EditorCard.WORKSPACE,
            EditorDoor.PANE_CONTROLS.cardFor(PaneWallPage.TERMINAL, null));
        assertEquals(EditorCard.WORKSPACE,
            EditorDoor.PANE_CONTROLS.cardFor(PaneWallPage.DISPLAY, null));
        assertNull(EditorDoor.PANE_CONTROLS.cardFor(null, null));
    }

    @Test
    public void aHomeDoorFromTheWorkspacePageEndsOnHomeWithTheHomeCard() {
        EditorCard card = EditorDoor.SETTINGS_HOME.cardFor(null, null);
        assertEquals(EditorCard.HOME, card);
        assertEquals(PaneWallPage.WIDGETS,
            EditorDoor.placeFor(card, null, PaneWallPage.TERMINAL));
        assertEquals(PaneWallPage.WIDGETS,
            EditorDoor.placeFor(card, null, PaneWallPage.DISPLAY));
        assertNull(EditorDoor.placeFor(card, null, PaneWallPage.WIDGETS));
    }

    @Test
    public void aWorkspaceCardFromHomeEndsOnTheWorkspacePage() {
        assertEquals(PaneWallPage.TERMINAL,
            EditorDoor.placeFor(EditorCard.WORKSPACE, null, PaneWallPage.WIDGETS));
        assertNull("the display stays", EditorDoor.placeFor(EditorCard.WORKSPACE, null,
            PaneWallPage.DISPLAY));
        assertEquals("an explicit place wins", PaneWallPage.DISPLAY,
            EditorDoor.placeFor(EditorCard.HOME, PaneWallPage.DISPLAY, PaneWallPage.TERMINAL));
        assertNull(EditorDoor.placeFor(EditorCard.APPEARANCE, null, PaneWallPage.TERMINAL));
        assertNull(EditorDoor.placeFor(null, null, PaneWallPage.TERMINAL));
    }

    @Test
    public void theNamingDoorsOpenWhatTheyName() {
        for (EditorDoor door : new EditorDoor[] {EditorDoor.EDITOR_OUTLINE,
                EditorDoor.ELEMENTS_LIST, EditorDoor.SETTINGS_SEARCH,
                EditorDoor.SECTION_INTENT}) {
            for (EditorCard card : EditorCard.values())
                assertEquals(door.doorName, card, door.cardFor(PaneWallPage.WIDGETS, card));
            assertNull(door.cardFor(PaneWallPage.WIDGETS, null));
        }
    }

    @Test
    public void theSectionIntentNamesTheWorkspaceCard() {
        assertEquals(EditorCard.WORKSPACE, EditorDoor.SECTION_INTENT.cardFor(null,
            AppearanceEditorEntry.cardOf("workspace")));
    }

    @Test
    public void eachDoorLogsOneLineNamingItAndItsCard() {
        assertEquals("home-sheet-appearance -> HOME",
            EditorDoor.HOME_SHEET_APPEARANCE.line(EditorCard.HOME));
        assertEquals("terminal-sheet-appearance -> WORKSPACE",
            EditorDoor.TERMINAL_SHEET_APPEARANCE.line(EditorCard.WORKSPACE));
        assertEquals("pane-controls-appearance -> REST", EditorDoor.PANE_CONTROLS.line(null));
        assertEquals("EditorDoor", EditorDoor.LOG_TAG);
    }

    @Test
    public void everyDoorHasItsOwnNameAndAnIntentCarriesItBack() {
        Set<String> names = new HashSet<>();
        for (EditorDoor door : EditorDoor.values()) {
            assertTrue("one name per door: " + door.doorName, names.add(door.doorName));
            assertEquals(door, EditorDoor.forName(door.doorName, EditorDoor.SECTION_INTENT));
        }
        assertEquals(EditorDoor.SETTINGS_HOME,
            EditorDoor.forName(" Settings-Home ", EditorDoor.SECTION_INTENT));
        assertEquals(EditorDoor.SECTION_INTENT,
            EditorDoor.forName(null, EditorDoor.SECTION_INTENT));
        assertEquals(EditorDoor.SECTION_INTENT,
            EditorDoor.forName("nowhere", EditorDoor.SECTION_INTENT));
    }

    private static PaneWallPage[] pagesAndNone() {
        PaneWallPage[] pages = PaneWallPage.values();
        PaneWallPage[] out = new PaneWallPage[pages.length + 1];
        System.arraycopy(pages, 0, out, 0, pages.length);
        return out;
    }

}
