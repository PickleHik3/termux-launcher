package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** When "Sort now" may start the sort itself, and when it hands over to the drawer's settings. */
public class CategorySortNowTest {

    @Test public void aUsableOnDeviceModelRuns() {
        assertTrue(CategorySortNow.isRunnable(true, false, false, false));
    }

    @Test public void aRemoteModelRunsDespiteTheRamWarning() {
        assertTrue(CategorySortNow.isRunnable(true, false, true, true));
    }

    @Test public void noModelWaitsForTheChooser() {
        assertFalse(CategorySortNow.isRunnable(false, false, false, false));
    }

    @Test public void anUnavailableRuntimeWaitsForTheChooser() {
        assertFalse(CategorySortNow.isRunnable(true, true, false, false));
        assertFalse(CategorySortNow.isRunnable(true, true, true, false));
    }

    @Test public void aModelTheRamAdvisesAgainstWaitsForATap() {
        assertFalse(CategorySortNow.isRunnable(true, false, false, true));
    }
}
