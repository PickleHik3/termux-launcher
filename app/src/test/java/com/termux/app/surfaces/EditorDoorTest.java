package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import com.termux.app.page.PageKind;

/**
 * The door table (editor-redesign §5, the After column): each door into the editor and the card
 * it opens, and the one line each logs.
 */
public class EditorDoorTest {

    @Test
    public void aScreensDoorOpensThatScreensCard() {
        for (PageKind page : pagesAndNone()) {
            assertEquals("the Home sheet's Appearance opens Global, from any page",
                EditorCard.APPEARANCE, EditorDoor.HOME_SHEET_APPEARANCE.cardFor(page, null));
            assertEquals("the terminal's sheet opens the Terminal card, not the editor at rest",
                EditorCard.TERMINAL, EditorDoor.TERMINAL_SHEET_APPEARANCE.cardFor(page, null));
        }
    }

    @Test
    public void theWallpaperThePaletteAndSettingsLookOpenGlobal() {
        for (EditorDoor door : new EditorDoor[] {EditorDoor.HOME_SHEET_WALLPAPER,
                EditorDoor.TERMINAL_SHEET_WALLPAPER, EditorDoor.EDITOR_PALETTE, EditorDoor.EDITOR_GLOBAL_CHIP, EditorDoor.SETTINGS_LOOK,
                EditorDoor.LAUNCHERCTL_WALLPAPER}) {
            for (PageKind page : pagesAndNone())
                assertEquals(door.doorName, EditorCard.APPEARANCE, door.cardFor(page, null));
        }
    }

    @Test
    public void everyWallpaperDoorOpensThePicker() {
        // Home long-press › Wallpaper, the palette's Wallpaper (launcherctl set_wallpaper) and
        // the Terminal's More › Wallpaper: no card holds a Change row, so each opens the picker.
        for (EditorDoor door : new EditorDoor[] {EditorDoor.HOME_SHEET_WALLPAPER,
                EditorDoor.LAUNCHERCTL_WALLPAPER, EditorDoor.TERMINAL_SHEET_WALLPAPER}) {
            assertTrue(door.doorName, door.opensWallpaperPicker());
        }
        int pickers = 0;
        for (EditorDoor door : EditorDoor.values()) {
            if (door.opensWallpaperPicker()) pickers++;
        }
        assertEquals("only the Wallpaper doors", 3, pickers);
        assertFalse(EditorDoor.HOME_SHEET_APPEARANCE.opensWallpaperPicker());
        assertFalse(EditorDoor.TERMINAL_SHEET_APPEARANCE.opensWallpaperPicker());
    }

    @Test
    public void theSettingsRowsOpenTheirObjectsCards() {
        assertEquals(EditorCard.HOME, EditorDoor.SETTINGS_HOME.cardFor(null, null));
        assertEquals(EditorCard.TERMINAL, EditorDoor.SETTINGS_TERMINAL.cardFor(null, null));
        assertEquals(EditorCard.KEYBOARD, EditorDoor.SETTINGS_KEYBOARD.cardFor(null, null));
        assertEquals(EditorCard.STATUS, EditorDoor.SETTINGS_STATUS.cardFor(null, null));
    }

    @Test
    public void thePageLabelOpensTheCardOfThePageItNames() {
        assertEquals(EditorCard.HOME,
            EditorDoor.EDITOR_PAGE_LABEL.cardFor(PageKind.HOME, null));
        assertEquals(EditorCard.TERMINAL,
            EditorDoor.EDITOR_PAGE_LABEL.cardFor(PageKind.TERMINAL, null));
        assertEquals(EditorCard.TERMINAL,
            EditorDoor.EDITOR_PAGE_LABEL.cardFor(PageKind.DISPLAY, null));
    }

    @Test
    public void thePageLedDoorsAreUnchanged() {
        for (EditorDoor door : new EditorDoor[] {EditorDoor.LAUNCHERCTL,
                EditorDoor.TERMINAL_CONTEXT_MENU}) {
            assertEquals(EditorCard.HOME, door.cardFor(PageKind.HOME, null));
            assertEquals(EditorCard.TERMINAL, door.cardFor(PageKind.TERMINAL, null));
            assertNull("at rest over the display", door.cardFor(PageKind.DISPLAY, null));
            assertNull(door.cardFor(null, EditorCard.DOCK));
        }
    }

    @Test
    public void paneControlsOpenTheTerminal() {
        assertEquals(EditorCard.TERMINAL, EditorDoor.PANE_CONTROLS.cardFor(PageKind.HOME, null));
        assertEquals(EditorCard.TERMINAL,
            EditorDoor.PANE_CONTROLS.cardFor(PageKind.TERMINAL, null));
        assertEquals(EditorCard.TERMINAL,
            EditorDoor.PANE_CONTROLS.cardFor(PageKind.DISPLAY, null));
        assertEquals(EditorCard.TERMINAL, EditorDoor.PANE_CONTROLS.cardFor(null, null));
    }

    @Test
    public void aHomeDoorFromTheTerminalPageEndsOnHomeWithTheHomeCard() {
        EditorCard card = EditorDoor.SETTINGS_HOME.cardFor(null, null);
        assertEquals(EditorCard.HOME, card);
        assertEquals(PageKind.HOME,
            EditorDoor.placeFor(card, null, PageKind.TERMINAL));
        assertEquals(PageKind.HOME,
            EditorDoor.placeFor(card, null, PageKind.DISPLAY));
        assertNull(EditorDoor.placeFor(card, null, PageKind.HOME));
    }

    @Test
    public void aTerminalCardFromHomeEndsOnTheTerminalPage() {
        assertEquals(PageKind.TERMINAL,
            EditorDoor.placeFor(EditorCard.TERMINAL, null, PageKind.HOME));
        assertNull("the display stays", EditorDoor.placeFor(EditorCard.TERMINAL, null,
            PageKind.DISPLAY));
        assertEquals("an explicit place wins", PageKind.DISPLAY,
            EditorDoor.placeFor(EditorCard.HOME, PageKind.DISPLAY, PageKind.TERMINAL));
        assertNull(EditorDoor.placeFor(EditorCard.APPEARANCE, null, PageKind.TERMINAL));
        assertNull(EditorDoor.placeFor(null, null, PageKind.TERMINAL));
    }

    @Test
    public void theNamingDoorsOpenWhatTheyName() {
        for (EditorDoor door : new EditorDoor[] {EditorDoor.EDITOR_OUTLINE,
                EditorDoor.ELEMENTS_LIST, EditorDoor.SETTINGS_SEARCH,
                EditorDoor.SECTION_INTENT}) {
            for (EditorCard card : EditorCard.values())
                assertEquals(door.doorName, card, door.cardFor(PageKind.HOME, card));
            assertNull(door.cardFor(PageKind.HOME, null));
        }
    }

    @Test
    public void theSectionIntentNamesTheTerminalCard() {
        assertEquals(EditorCard.TERMINAL, EditorDoor.SECTION_INTENT.cardFor(null,
            AppearanceEditorEntry.cardOf("workspace")));
    }

    @Test
    public void eachDoorLogsOneLineNamingItAndItsCard() {
        assertEquals("home-sheet-appearance -> APPEARANCE",
            EditorDoor.HOME_SHEET_APPEARANCE.line(EditorCard.APPEARANCE));
        assertEquals("terminal-sheet-appearance -> TERMINAL",
            EditorDoor.TERMINAL_SHEET_APPEARANCE.line(EditorCard.TERMINAL));
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

    private static PageKind[] pagesAndNone() {
        PageKind[] pages = PageKind.values();
        PageKind[] out = new PageKind[pages.length + 1];
        System.arraycopy(pages, 0, out, 0, pages.length);
        return out;
    }

}
