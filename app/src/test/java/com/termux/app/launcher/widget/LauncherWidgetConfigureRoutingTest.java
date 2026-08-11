package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LauncherWidgetConfigureRoutingTest {
    @Test public void directConfigureOkCancelAndProcessRecreation() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.directBind = true;
        platform.directlyBoundInfo = WidgetTestFixtures.info(true);
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);
        assertEquals(LauncherWidgetHostController.AddResult.STARTED,
            controller.beginAdd(WidgetTestFixtures.info(true), null));
        assertEquals(1, platform.configureLaunches);
        controller.handleActivityResult(4715, Activity.RESULT_OK, null);
        assertNotNull(repository.get(20));

        WidgetTestFixtures.Platform consent = new WidgetTestFixtures.Platform(activity);
        LauncherWidgetRepository durable = WidgetTestFixtures.repository();
        LauncherWidgetHostController first = new LauncherWidgetHostController(activity, durable, consent);
        first.beginAdd(WidgetTestFixtures.info(true), null);
        consent.info.put(20, WidgetTestFixtures.info(true));
        LauncherWidgetHostController recreated = new LauncherWidgetHostController(activity, durable, consent);
        recreated.handleActivityResult(4714, Activity.RESULT_OK, null);
        assertEquals(1, consent.configureLaunches);
        recreated.handleActivityResult(4715, Activity.RESULT_CANCELED, null);
        assertNull(durable.pending());
        assertEquals(1, consent.deleted.size());
    }

    @Test public void missingOrBlockedConfigureLaunchDeletesId() {
        for (RuntimeException failure : new RuntimeException[] {
            new ActivityNotFoundException(), new SecurityException("blocked")}) {
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
            LauncherWidgetRepository repository = WidgetTestFixtures.repository();
            LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
                repository, platform);
            controller.beginAdd(WidgetTestFixtures.info(true), null);
            platform.info.put(20, WidgetTestFixtures.info(true));
            platform.configureLaunchFailure = failure;
            controller.handleActivityResult(4714, Activity.RESULT_OK, new Intent());
            assertEquals(failure.getClass().getSimpleName(), 1, platform.deleted.size());
        }
    }

    @Test public void configureAvailabilityUsesProviderProfile() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.directBind = true;
        android.appwidget.AppWidgetProviderInfo info = WidgetTestFixtures.info(true);
        android.content.pm.ActivityInfo providerInfo =
            ReflectionHelpers.getField(info, "providerInfo");
        providerInfo.applicationInfo.uid = 10 * 100000
            + (android.os.Process.myUid() % 100000);
        platform.directlyBoundInfo = info;
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            WidgetTestFixtures.repository(), platform);
        assertEquals(LauncherWidgetHostController.AddResult.STARTED,
            controller.beginAdd(info, null));
        assertEquals(info.getProfile(), platform.configureProfile);
    }
}
