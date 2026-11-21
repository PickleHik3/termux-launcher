package com.termux.app.launcher.drawer;

import static com.termux.app.launcher.drawer.AppDrawerEditModel.BackStep.CLEAR_QUERY;
import static com.termux.app.launcher.drawer.AppDrawerEditModel.BackStep.CLOSE_DRAWER;
import static com.termux.app.launcher.drawer.AppDrawerEditModel.BackStep.COLLAPSE_CATEGORY;
import static com.termux.app.launcher.drawer.AppDrawerEditModel.BackStep.LEAVE_EDIT;
import static com.termux.app.launcher.drawer.AppDrawerEditModel.BackStep.UNCLAIMED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.drawer.AppDrawerEditModel.Entry;
import com.termux.app.launcher.drawer.AppDrawerEditModel.Leave;

import org.junit.Test;

public class AppDrawerEditModelTest {

    private static final AppDrawerSection APPS = AppDrawerSection.APPS;
    private static final AppDrawerSection CATS = AppDrawerSection.CATEGORIES;

    @Test public void startsOffAndNothingIsDressed() {
        AppDrawerEditModel m = AppDrawerEditModel.OFF;
        assertFalse(m.isEditing());
        assertEquals(Entry.NONE, m.entry());
        assertTrue(m.searchEnabled());
        assertFalse(m.searchDimmed());
        assertFalse(m.outlinesVisible());
        assertFalse(m.tabPinnedOpen());
        assertFalse(m.liftsTileOnEntry());
    }

    @Test public void theCornerButtonEntersWithoutLiftingATile() {
        AppDrawerEditModel m = AppDrawerEditModel.enter(Entry.CORNER_BUTTON);
        assertTrue(m.isEditing());
        assertEquals(Entry.CORNER_BUTTON, m.entry());
        assertFalse(m.liftsTileOnEntry());
    }

    @Test public void aTileLongPressEntersAndLiftsThatTile() {
        AppDrawerEditModel m = AppDrawerEditModel.enter(Entry.TILE_LONG_PRESS);
        assertTrue(m.isEditing());
        assertTrue(m.liftsTileOnEntry());
    }

    @Test public void enteringWithNoEntryStaysOff() {
        assertSame(AppDrawerEditModel.OFF, AppDrawerEditModel.enter(Entry.NONE));
    }

    @Test public void everyWayOutEndsEditMode() {
        for (Leave reason : Leave.values()) {
            assertFalse(reason.name(),
                AppDrawerEditModel.enter(Entry.CORNER_BUTTON).leave(reason).isEditing());
        }
    }

    @Test public void changingTheViewTypeLeavesEditMode() {
        AppDrawerEditModel m = AppDrawerEditModel.enter(Entry.CORNER_BUTTON);
        assertFalse(m.onViewTypeChanged(AppDrawerViewType.VERTICAL,
            AppDrawerViewType.CATEGORIES).isEditing());
    }

    @Test public void theSameViewTypeKeepsEditMode() {
        AppDrawerEditModel m = AppDrawerEditModel.enter(Entry.CORNER_BUTTON);
        assertSame(m, m.onViewTypeChanged(AppDrawerViewType.DEFAULT, AppDrawerViewType.DEFAULT));
    }

    @Test public void editModeDimsSearchAndDrawsOutlinesAndPinsTheTab() {
        AppDrawerEditModel m = AppDrawerEditModel.enter(Entry.CORNER_BUTTON);
        assertFalse(m.searchEnabled());
        assertTrue(m.searchDimmed());
        assertTrue(m.outlinesVisible());
        assertTrue(m.tabPinnedOpen());
    }

    @Test public void outsideEditTheTabShowsSettingsAndEdit() {
        AppDrawerEditModel m = AppDrawerEditModel.OFF;
        assertTrue(m.showsSettingsButton());
        assertTrue(m.showsEditButton());
        assertFalse(m.showsTickButton());
        for (AppDrawerViewType type : AppDrawerViewType.values()) {
            assertFalse(type.name(), m.showsGridSizeButton(type, APPS));
        }
    }

    @Test public void inEditTheTabShowsGridSizeAndTick() {
        AppDrawerEditModel m = AppDrawerEditModel.enter(Entry.CORNER_BUTTON);
        assertFalse(m.showsSettingsButton());
        assertFalse(m.showsEditButton());
        assertTrue(m.showsTickButton());
        assertTrue(m.showsGridSizeButton(AppDrawerViewType.VERTICAL, APPS));
        assertTrue(m.showsGridSizeButton(AppDrawerViewType.HORIZONTAL, APPS));
        assertTrue(m.showsGridSizeButton(AppDrawerViewType.DEFAULT, APPS));
    }

    @Test public void gridSizeIsHiddenOnCategories() {
        AppDrawerEditModel m = AppDrawerEditModel.enter(Entry.TILE_LONG_PRESS);
        assertFalse(m.showsGridSizeButton(AppDrawerViewType.CATEGORIES, APPS));
        assertTrue("the tick stays", m.showsTickButton());
    }

    @Test public void gridSizeIsHiddenOnDefaultsCategoriesSection() {
        AppDrawerEditModel m = AppDrawerEditModel.enter(Entry.CORNER_BUTTON);
        assertFalse(m.showsGridSizeButton(AppDrawerViewType.DEFAULT, CATS));
        assertTrue(m.showsGridSizeButton(AppDrawerViewType.DEFAULT, APPS));
    }

    // Back: leave edit, clear the query, collapse the category, close; one press one thing.

    @Test public void backLeavesEditModeBeforeAnythingElse() {
        assertEquals(LEAVE_EDIT, AppDrawerEditModel.backStep(true, true, false, false));
        assertEquals(LEAVE_EDIT, AppDrawerEditModel.backStep(true, true, true, true));
    }

    @Test public void outsideEditBackKeepsTheExistingOrder() {
        assertEquals(CLEAR_QUERY, AppDrawerEditModel.backStep(true, false, true, true));
        assertEquals(COLLAPSE_CATEGORY, AppDrawerEditModel.backStep(true, false, false, true));
        assertEquals(CLOSE_DRAWER, AppDrawerEditModel.backStep(true, false, false, false));
    }

    @Test public void backAtAClosedDrawerIsNotClaimed() {
        assertEquals(UNCLAIMED, AppDrawerEditModel.backStep(false, true, true, true));
        assertEquals(UNCLAIMED, AppDrawerEditModel.backStep(false, false, false, false));
    }

    @Test public void backWalksEditThenCloseAcrossPresses() {
        AppDrawerEditModel m = AppDrawerEditModel.enter(Entry.CORNER_BUTTON);
        assertEquals(LEAVE_EDIT, AppDrawerEditModel.backStep(true, m.isEditing(), false, false));
        m = m.leave(Leave.BACK);
        assertEquals(CLOSE_DRAWER, AppDrawerEditModel.backStep(true, m.isEditing(), false, false));
    }
}
