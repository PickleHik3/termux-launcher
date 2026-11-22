package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerGridChoicesTest {

    @Test public void theStopsAreTheSettingsPagesChoicesWithAutoFirst() {
        assertEquals("[auto, 4, 5, 6]", java.util.Arrays.toString(AppDrawerGridChoices.COLUMN_VALUES));
        assertEquals("[auto, 3, 4, 5]", java.util.Arrays.toString(AppDrawerGridChoices.ROW_VALUES));
    }

    @Test public void aStoredWordStandsOnItsStopAndAnythingElseReadsAsAuto() {
        assertEquals(2, AppDrawerGridChoices.indexOf(AppDrawerGridChoices.COLUMN_VALUES, "5"));
        assertEquals(0, AppDrawerGridChoices.indexOf(AppDrawerGridChoices.COLUMN_VALUES, "auto"));
        assertEquals(0, AppDrawerGridChoices.indexOf(AppDrawerGridChoices.COLUMN_VALUES, "9"));
        assertEquals(0, AppDrawerGridChoices.indexOf(AppDrawerGridChoices.ROW_VALUES, null));
    }

    @Test public void aStopStoresItsWordAndIsHeldToTheStopsThereAre() {
        assertEquals("auto", AppDrawerGridChoices.valueAt(AppDrawerGridChoices.ROW_VALUES, 0));
        assertEquals("5", AppDrawerGridChoices.valueAt(AppDrawerGridChoices.ROW_VALUES, 3));
        assertEquals("5", AppDrawerGridChoices.valueAt(AppDrawerGridChoices.ROW_VALUES, 40));
        assertEquals("auto", AppDrawerGridChoices.valueAt(AppDrawerGridChoices.COLUMN_VALUES, -2));
    }

    @Test public void onlyThePagesTypeChoosesRows() {
        assertTrue(AppDrawerGridChoices.showsRows(AppDrawerViewType.HORIZONTAL));
        assertFalse(AppDrawerGridChoices.showsRows(AppDrawerViewType.VERTICAL));
        assertFalse(AppDrawerGridChoices.showsRows(AppDrawerViewType.DEFAULT));
        assertFalse(AppDrawerGridChoices.showsRows(AppDrawerViewType.CATEGORIES));
    }
}
