package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

/**
 * Whether the keyboard comes up on a page (moved from the Home chrome policy's test): a window's
 * memory, Home's always on, and the rows always on takes from the widget grid.
 */
public class PlaceKeyboardTest {

    private static final PaneWallPage HOME = PaneWallPage.WIDGETS;
    private static final PaneWallPage WINDOW = PaneWallPage.TERMINAL;

    @Test
    public void homeHasNoKeyboardMemoryAndIsUpOnlyWhileAlwaysOn() {
        assertFalse("remembered open means nothing on Home",
            PlaceKeyboard.keyboardOnEnter(HOME, true, false, false));
        assertTrue(PlaceKeyboard.keyboardOnEnter(HOME, false, false, true));
        assertTrue("minimal mode is the Workspace's",
            PlaceKeyboard.keyboardOnEnter(HOME, false, true, true));
        // A window keeps its memory, and minimal mode still puts it away.
        assertTrue(PlaceKeyboard.keyboardOnEnter(WINDOW, true, false, false));
        assertFalse(PlaceKeyboard.keyboardOnEnter(WINDOW, true, true, true));
        assertFalse(PlaceKeyboard.keyboardOnEnter(WINDOW, false, false, true));
    }

    @Test
    public void alwaysOnTakesTheWholeRowsTheKeyboardCovers() {
        // A 1000 px grid of 5 rows: 200 px a row.
        assertEquals(2, PlaceKeyboard.keyboardRows(400, 1000, 5));
        assertEquals(3, PlaceKeyboard.keyboardRows(401, 1000, 5));
        assertEquals(1, PlaceKeyboard.keyboardRows(10, 1000, 5));
        assertEquals("never the whole grid", 4, PlaceKeyboard.keyboardRows(5000, 1000, 5));
        assertEquals("nothing measured yet", 0, PlaceKeyboard.keyboardRows(400, 0, 5));
        assertEquals(0, PlaceKeyboard.keyboardRows(0, 1000, 5));
        assertEquals("a one-row grid has none to give", 0,
            PlaceKeyboard.keyboardRows(400, 1000, 1));
    }

    /**
     * Device check 3, finding 1: the Keyboard chip brought out in the Layout editor on Home turns
     * always on on while the editor's own keyboard is up, so the arrangement pass leaves it; the
     * editor's end must not then take it down, or Home stays without it until a cold launch.
     */
    @Test
    public void theEditorsEndLeavesTheKeyboardUpOnHomeOnlyWhileAlwaysOnIsOn() {
        assertTrue(PlaceKeyboard.editorEndKeepsKeyboard(HOME, true));
        assertFalse(PlaceKeyboard.editorEndKeepsKeyboard(HOME, false));
        assertFalse(PlaceKeyboard.editorEndKeepsKeyboard(WINDOW, true));
        assertFalse(PlaceKeyboard.editorEndKeepsKeyboard(WINDOW, false));
        assertFalse(PlaceKeyboard.editorEndKeepsKeyboard(PaneWallPage.DISPLAY, true));
    }
}
