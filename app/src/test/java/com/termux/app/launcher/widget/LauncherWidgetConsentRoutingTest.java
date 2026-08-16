package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.os.Build;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LauncherWidgetConsentRoutingTest {
    @Test public void requestCodesExtrasDeclineCleanupAndFallthrough() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);
        Bundle options = new Bundle(); options.putInt("seed", 4);
        assertEquals(LauncherWidgetHostController.AddResult.STARTED,
            controller.beginAdd(WidgetTestFixtures.info(false), options));
        assertEquals(4714, LauncherWidgetHostController.REQUEST_BIND_APPWIDGET);
        assertEquals(4715, LauncherWidgetHostController.REQUEST_CONFIGURE_APPWIDGET);
        assertNotNull(platform.bindIntent.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER));
        assertNotNull(platform.bindIntent.getParcelableExtra(
            AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE));
        assertEquals(4, platform.bindIntent.getBundleExtra(
            AppWidgetManager.EXTRA_APPWIDGET_OPTIONS).getInt("seed"));
        assertTrue(controller.handleActivityResult(4714, Activity.RESULT_CANCELED, null));
        assertEquals(1, platform.deleted.size());
        assertTrue(repository.records().isEmpty());
        assertNull(repository.pending());
        assertFalse(controller.handleActivityResult(4713, Activity.RESULT_OK, new Intent()));
    }

    @Test public void allocationIsDeletedWhenInitialPersistenceFails() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        storage.fail = true;
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            new LauncherWidgetRepository(storage), platform);
        assertEquals(LauncherWidgetHostController.AddResult.STORAGE_FAILURE,
            controller.beginAdd(WidgetTestFixtures.info(false), null));
        assertEquals(java.util.Collections.singletonList(20), platform.deleted);
    }

    @Test public void missingOrBlockedConsentUiDeletesAllocatedId() {
        for (RuntimeException failure : new RuntimeException[] {
            new ActivityNotFoundException(), new SecurityException("blocked")}) {
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
            platform.bindLaunchFailure = failure;
            LauncherWidgetRepository repository = WidgetTestFixtures.repository();
            LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
                repository, platform);
            assertEquals(LauncherWidgetHostController.AddResult.FAILED,
                controller.beginAdd(WidgetTestFixtures.info(false), null));
            assertEquals(failure.getClass().getSimpleName(),
                java.util.Collections.singletonList(20), platform.deleted);
            assertNull(repository.pending());
            assertTrue(repository.records().isEmpty());
        }
    }
}
