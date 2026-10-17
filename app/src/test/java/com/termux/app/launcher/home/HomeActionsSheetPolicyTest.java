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

    @Test public void notTheDefaultHomeOffersEveryRowInOrder() {
        assertEquals(Arrays.asList(TERMINAL, ADD_WIDGET, ADD_PAGE, EDIT_PAGES, LAYOUT,
                WALLPAPER, LOOK, LAUNCHER_SETTINGS, SET_DEFAULT_HOME, HELP),
            HomeActionsSheetPolicy.itemsFor(false, true, true));
    }

    @Test public void theDefaultHomeHidesSetAsDefault() {
        List<Item> items = HomeActionsSheetPolicy.itemsFor(true, true, true);
        assertFalse(items.contains(SET_DEFAULT_HOME));
        assertEquals(Arrays.asList(TERMINAL, ADD_WIDGET, ADD_PAGE, EDIT_PAGES, LAYOUT,
            WALLPAPER, LOOK, LAUNCHER_SETTINGS, HELP), items);
    }

    @Test public void terminalIsFirstWhenOffered() {
        assertEquals(TERMINAL, HomeActionsSheetPolicy.itemsFor(true, true, true).get(0));
        assertEquals(TERMINAL, HomeActionsSheetPolicy.itemsFor(false, false, true).get(0));
    }

    @Test public void noPanesNoTerminal() {
        List<Item> items = HomeActionsSheetPolicy.itemsFor(true, true, false);
        assertFalse(items.contains(TERMINAL));
        assertEquals(ADD_WIDGET, items.get(0));
    }

    @Test public void withoutPagesOnlyTheTerminalAndTheLauncherRowsStay() {
        assertEquals(Arrays.asList(TERMINAL, WALLPAPER, LOOK, LAUNCHER_SETTINGS, HELP),
            HomeActionsSheetPolicy.itemsFor(true, false, true));
        assertEquals(Arrays.asList(WALLPAPER, LOOK, LAUNCHER_SETTINGS, SET_DEFAULT_HOME, HELP),
            HomeActionsSheetPolicy.itemsFor(false, false, false));
    }

    @Test public void thePageGroupComesBeforeTheLauncherGroup() {
        List<Item> items = HomeActionsSheetPolicy.itemsFor(false, true, true);
        boolean seenLauncher = false;
        for (Item item : items) {
            if (item.group == Group.LAUNCHER) seenLauncher = true;
            else assertFalse("a page row after a launcher row: " + item, seenLauncher);
        }
        assertEquals(Arrays.asList(TERMINAL, ADD_WIDGET, ADD_PAGE, EDIT_PAGES, LAYOUT),
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
