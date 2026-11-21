package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class AppDrawerCornerTabButtonsTest {

    private static int[] ids(AppDrawerEditModel model, AppDrawerViewType type,
                             AppDrawerSection section, boolean editShipped) {
        return AppDrawerCornerTabButtons.ids(model, type, section, editShipped);
    }

    @Test public void editIsNotShownYetSoOnlyTheGearIs() {
        for (AppDrawerViewType type : AppDrawerViewType.values()) {
            for (AppDrawerSection section : AppDrawerSection.values()) {
                assertArrayEquals(new int[] {AppDrawerCornerTabButtons.SETTINGS},
                    ids(AppDrawerEditModel.OFF, type, section,
                        AppDrawerCornerTabButtons.EDIT_BUTTON_SHIPPED));
            }
        }
    }

    @Test public void onceShippedEditComesBeforeTheGear() {
        assertArrayEquals(
            new int[] {AppDrawerCornerTabButtons.EDIT, AppDrawerCornerTabButtons.SETTINGS},
            ids(AppDrawerEditModel.OFF, AppDrawerViewType.VERTICAL, AppDrawerSection.APPS, true));
    }

    @Test public void editingShowsGridSizeAndTickButNotTheGear() {
        AppDrawerEditModel editing =
            AppDrawerEditModel.enter(AppDrawerEditModel.Entry.CORNER_BUTTON);
        assertArrayEquals(
            new int[] {AppDrawerCornerTabButtons.GRID_SIZE, AppDrawerCornerTabButtons.TICK},
            ids(editing, AppDrawerViewType.VERTICAL, AppDrawerSection.APPS, true));
        assertArrayEquals(new int[] {AppDrawerCornerTabButtons.TICK},
            ids(editing, AppDrawerViewType.CATEGORIES, AppDrawerSection.APPS, true));
    }
}
