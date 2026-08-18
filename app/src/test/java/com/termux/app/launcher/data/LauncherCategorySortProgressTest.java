package com.termux.app.launcher.data;

import static org.junit.Assert.*;

import com.termux.R;

import org.junit.Test;

/** The wording and the bar the categorization run shows while it works. */
public class LauncherCategorySortProgressTest {

    @Test public void loadingOwnsAVisibleSliceRatherThanZero() {
        assertEquals(0, LauncherCategorySortProgress.percent(
            LauncherCategorySortProgress.PHASE_PREPARING, 0, 0));
        assertTrue(LauncherCategorySortProgress.percent(
            LauncherCategorySortProgress.PHASE_LOADING_MODEL, 0, 0) > 0);
        assertEquals(100, LauncherCategorySortProgress.percent(
            LauncherCategorySortProgress.PHASE_SAVING, 0, 0));
    }

    @Test public void sortingMapsProcessedOntoTheBar() {
        assertEquals(50, LauncherCategorySortProgress.percent(
            LauncherCategorySortProgress.PHASE_SORTING, 50, 100));
        assertEquals(100, LauncherCategorySortProgress.percent(
            LauncherCategorySortProgress.PHASE_SORTING, 100, 100));
        // An unknown total must not read as finished.
        assertTrue(LauncherCategorySortProgress.percent(
            LauncherCategorySortProgress.PHASE_SORTING, 0, 0) < 100);
    }

    @Test public void tailWordingIsReservedForTheLastStretch() {
        assertEquals(R.string.settings_app_drawer_category_sort_hint_categorizing,
            LauncherCategorySortProgress.hint(LauncherCategorySortProgress.PHASE_SORTING, 40, 100));
        assertEquals(R.string.settings_app_drawer_category_sort_hint_categorizing,
            LauncherCategorySortProgress.hint(LauncherCategorySortProgress.PHASE_SORTING, 84, 100));
        assertEquals(R.string.settings_app_drawer_category_sort_hint_almost_there,
            LauncherCategorySortProgress.hint(LauncherCategorySortProgress.PHASE_SORTING, 90, 100));
        assertEquals(R.string.settings_app_drawer_category_sort_hint_any_minute,
            LauncherCategorySortProgress.hint(LauncherCategorySortProgress.PHASE_SORTING, 97, 100));
    }

    @Test public void everyPhaseHasItsOwnLine() {
        assertEquals(R.string.settings_app_drawer_category_sort_hint_preparing,
            LauncherCategorySortProgress.hint(LauncherCategorySortProgress.PHASE_PREPARING, 0, 0));
        assertEquals(R.string.settings_app_drawer_category_sort_hint_loading_model,
            LauncherCategorySortProgress.hint(LauncherCategorySortProgress.PHASE_LOADING_MODEL, 0, 0));
        assertEquals(R.string.settings_app_drawer_category_sort_hint_saving,
            LauncherCategorySortProgress.hint(LauncherCategorySortProgress.PHASE_SAVING, 10, 10));
    }
}
