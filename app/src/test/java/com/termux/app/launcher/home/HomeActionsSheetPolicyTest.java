package com.termux.app.launcher.home;

import static com.termux.app.launcher.home.HomeActionsSheetPolicy.Item.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.home.HomeActionsSheetPolicy.Group;
import com.termux.app.launcher.home.HomeActionsSheetPolicy.Item;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class HomeActionsSheetPolicyTest {

    private static List<Item> items(boolean isDefault, boolean hasPages, boolean empty, int pages) {
        return HomeActionsSheetPolicy.itemsFor(isDefault, hasPages, empty, pages, false);
    }

    @Test public void notTheDefaultHomeOffersEveryRowInOrder() {
        assertEquals(Arrays.asList(TERMINAL, ADD_WIDGET, ADD_APP, ADD_PAGE, EDIT_WIDGETS, EDIT_PAGES, LAYOUT,
                LOCK_LAYOUT, WALLPAPER, LOOK, LAUNCHER_SETTINGS, SET_DEFAULT_HOME, HELP),
            items(false, true, false, 1));
    }

    @Test public void theDefaultHomeHidesSetAsDefault() {
        List<Item> items = items(true, true, false, 1);
        assertFalse(items.contains(SET_DEFAULT_HOME));
        assertEquals(Arrays.asList(TERMINAL, ADD_WIDGET, ADD_APP, ADD_PAGE, EDIT_WIDGETS, EDIT_PAGES, LAYOUT,
            LOCK_LAYOUT, WALLPAPER, LOOK, LAUNCHER_SETTINGS, HELP), items);
    }

    @Test public void terminalIsAlwaysFirst() {
        for (boolean isDefault : new boolean[] {false, true}) {
            for (boolean hasPages : new boolean[] {false, true}) {
                for (boolean empty : new boolean[] {false, true}) {
                    for (int pages = 0; pages <= 3; pages++) {
                        for (boolean locked : new boolean[] {false, true}) {
                            assertEquals(TERMINAL, HomeActionsSheetPolicy.itemsFor(
                                isDefault, hasPages, empty, pages, locked).get(0));
                        }
                    }
                }
            }
        }
    }

    @Test public void removePageOnlyForAnEmptyPageThatIsNotTheLast() {
        assertEquals(Arrays.asList(TERMINAL, ADD_WIDGET, ADD_APP, ADD_PAGE, EDIT_WIDGETS, EDIT_PAGES, LAYOUT,
                REMOVE_PAGE, LOCK_LAYOUT, WALLPAPER, LOOK, LAUNCHER_SETTINGS, HELP),
            items(true, true, true, 2));
        assertTrue(items(true, true, true, 5).contains(REMOVE_PAGE));
        assertFalse("the only page stays", items(true, true, true, 1).contains(REMOVE_PAGE));
        assertFalse("a page with widgets stays", items(true, true, false, 3).contains(REMOVE_PAGE));
        assertFalse("no pages, nothing to remove", items(true, false, true, 3).contains(REMOVE_PAGE));
    }

    @Test public void removePageStandsInThePageGroup() {
        assertEquals(Group.PAGE, REMOVE_PAGE.group);
    }

    @Test public void withoutPagesOnlyTheTerminalAndTheLauncherRowsStay() {
        assertEquals(Arrays.asList(TERMINAL, WALLPAPER, LOOK, LAUNCHER_SETTINGS, HELP),
            items(true, false, false, 0));
        assertEquals(Arrays.asList(TERMINAL, WALLPAPER, LOOK, LAUNCHER_SETTINGS,
                SET_DEFAULT_HOME, HELP),
            items(false, false, true, 2));
        assertEquals("nothing to lock without pages",
            Arrays.asList(TERMINAL, WALLPAPER, LOOK, LAUNCHER_SETTINGS, HELP),
            HomeActionsSheetPolicy.itemsFor(true, false, false, 0, true));
    }

    @Test public void aLockedLayoutOffersUnlockInsteadOfTheRowsThatChangeIt() {
        assertEquals(Arrays.asList(TERMINAL, UNLOCK_LAYOUT, LAYOUT, WALLPAPER, LOOK,
                LAUNCHER_SETTINGS, HELP),
            HomeActionsSheetPolicy.itemsFor(true, true, true, 3, true));
        List<Item> unlocked = items(true, true, true, 3);
        assertFalse(unlocked.contains(UNLOCK_LAYOUT));
        assertTrue(unlocked.contains(LOCK_LAYOUT));
    }

    @Test public void thePageGroupComesBeforeTheLauncherGroup() {
        List<Item> items = items(false, true, true, 2);
        boolean seenLauncher = false;
        for (Item item : items) {
            if (item.group == Group.LAUNCHER) seenLauncher = true;
            else assertFalse("a page row after a launcher row: " + item, seenLauncher);
        }
        assertEquals(Arrays.asList(TERMINAL, ADD_WIDGET, ADD_APP, ADD_PAGE, EDIT_WIDGETS, EDIT_PAGES, LAYOUT,
                REMOVE_PAGE, LOCK_LAYOUT),
            HomeActionsSheetPolicy.inGroup(items, Group.PAGE));
        assertEquals(Arrays.asList(WALLPAPER, LOOK, LAUNCHER_SETTINGS, SET_DEFAULT_HOME, HELP),
            HomeActionsSheetPolicy.inGroup(items, Group.LAUNCHER));
    }

    @Test public void everyItemBelongsToAGroup() {
        for (Item item : Item.values()) assertTrue(item.group != null);
    }

    @Test public void twoColumnsTakeHalfTheLinesRoundedUp() {
        assertEquals(0, HomeActionsSheetPolicy.lineCount(0));
        assertEquals(1, HomeActionsSheetPolicy.lineCount(1));
        assertEquals(1, HomeActionsSheetPolicy.lineCount(2));
        assertEquals(3, HomeActionsSheetPolicy.lineCount(5));
        assertEquals(2, HomeActionsSheetPolicy.lineCount(4));
    }
}
