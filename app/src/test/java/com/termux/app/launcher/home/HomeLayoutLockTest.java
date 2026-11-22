package com.termux.app.launcher.home;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.home.HomeActionsSheetPolicy.Item;

import org.junit.Test;

public class HomeLayoutLockTest {

    @Test
    public void unlockedEveryEditGoesAhead() {
        for (HomeLayoutLock.Edit edit : HomeLayoutLock.Edit.values()) {
            assertTrue(edit.name(), HomeLayoutLock.allows(false, edit));
        }
    }

    @Test
    public void lockedEveryEditIsRefused() {
        for (HomeLayoutLock.Edit edit : HomeLayoutLock.Edit.values()) {
            assertFalse(edit.name(), HomeLayoutLock.allows(true, edit));
        }
    }

    @Test
    public void theSheetAlwaysHasAWayInAndAWayOut() {
        assertTrue(HomeLayoutLock.sheetOffers(false, Item.LOCK_LAYOUT));
        assertTrue(HomeLayoutLock.sheetOffers(true, Item.LOCK_LAYOUT));
    }

    @Test
    public void lockedTheSheetKeepsWhatDoesNotMoveTheLayout() {
        for (Item item : new Item[] {Item.TERMINAL, Item.WALLPAPER, Item.APPEARANCE,
                Item.LAUNCHER_SETTINGS, Item.SET_DEFAULT_HOME, Item.HELP}) {
            assertTrue(item.name(), HomeLayoutLock.sheetOffers(true, item));
        }
        for (Item item : new Item[] {Item.ADD_WIDGET, Item.ADD_APP, Item.ADD_PAGE,
                Item.EDIT_WIDGETS, Item.EDIT_PAGES, Item.REMOVE_PAGE}) {
            assertFalse(item.name(), HomeLayoutLock.sheetOffers(true, item));
            assertTrue(item.name(), HomeLayoutLock.sheetOffers(false, item));
        }
    }
}
