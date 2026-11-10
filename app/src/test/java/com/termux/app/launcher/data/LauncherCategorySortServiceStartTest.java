package com.termux.app.launcher.data;

import android.app.Application;
import android.content.Intent;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowService;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/** A second start while a sort runs (the row tapped again) must leave the run and its service up. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class LauncherCategorySortServiceStartTest {

    private ServiceController<LauncherCategorySortService> controller;

    @After
    public void tearDown() {
        // onDestroy ends the run in the shared static state for the next test.
        if (controller != null) controller.destroy();
    }

    @Test
    public void secondStartDuringARun_doesNotStopTheService() {
        controller = Robolectric.buildService(LauncherCategorySortService.class).create();
        LauncherCategorySortService service = controller.get();

        // The main looper is paused, so the run cannot finish before the second start lands.
        service.onStartCommand(sortIntent(), 0, 1);
        assertTrue(LauncherCategorySortService.isRunning());

        service.onStartCommand(sortIntent(), 0, 2);

        ShadowService shadow = shadowOf(service);
        assertFalse("a second start stopped the service under its run", shadow.isStoppedBySelf());
        assertFalse("a second start dropped the run's foreground", shadow.isForegroundStopped());
        assertTrue(LauncherCategorySortService.isRunning());
    }

    private static Intent sortIntent() {
        return new Intent(LauncherCategorySortService.ACTION_SORT);
    }
}
