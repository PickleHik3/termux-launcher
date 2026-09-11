package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetProviderInfo;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Reopening a placed widget's own settings screen: who is offered it, and what a return does. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetReconfigurePolicyTest {
    private static final int RECONFIGURABLE =
        AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE;

    @Test public void onlyAProviderThatOptedInIsOfferedItsSettings() {
        assertTrue(WidgetConfigurePolicy.reconfigurable(WidgetTestFixtures.CONFIGURE,
            RECONFIGURABLE, Build.VERSION_CODES.S, true));
        assertFalse("a configure activity alone is a first-run screen, not settings",
            WidgetConfigurePolicy.reconfigurable(WidgetTestFixtures.CONFIGURE, 0,
                Build.VERSION_CODES.S, true));
        assertFalse("no configure activity at all",
            WidgetConfigurePolicy.reconfigurable(null, RECONFIGURABLE,
                Build.VERSION_CODES.S, true));
        assertFalse("the activity is gone",
            WidgetConfigurePolicy.reconfigurable(WidgetTestFixtures.CONFIGURE, RECONFIGURABLE,
                Build.VERSION_CODES.S, false));
        assertFalse("before API 28 a provider had no way to say it",
            WidgetConfigurePolicy.reconfigurable(WidgetTestFixtures.CONFIGURE, RECONFIGURABLE,
                Build.VERSION_CODES.O, true));
    }

    @Test public void theSettingsScreenReopensWithTheIdTheWidgetAlreadyHas() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(31, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, null, null));
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.info.put(31, WidgetTestFixtures.info(true, RECONFIGURABLE));
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);

        assertTrue(controller.canReconfigure(31));
        assertEquals(LauncherWidgetHostController.AddResult.STARTED,
            controller.reconfigureWidget(31));
        assertEquals(31, platform.lastConfigureId);
        assertEquals(LauncherWidgetHostController.REQUEST_RECONFIGURE_APPWIDGET,
            platform.lastConfigureRequestCode);
        assertEquals("no new allocation: the widget keeps the ID it has", 0, platform.allocations);
    }

    @Test public void backingOutOfTheSettingsScreenKeepsTheWidget() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(31, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, null, null));
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.info.put(31, WidgetTestFixtures.info(true, RECONFIGURABLE));
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);
        controller.reconfigureWidget(31);

        assertTrue(controller.handleActivityResult(
            LauncherWidgetHostController.REQUEST_RECONFIGURE_APPWIDGET, Activity.RESULT_CANCELED,
            null));

        assertTrue("the record survives a cancelled settings screen", platform.deleted.isEmpty());
        assertEquals(LauncherWidgetRecord.State.ACTIVE, repository.get(31).state);
    }

    @Test public void aProviderWithoutTheFlagHasNoSettingsToOffer() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(31, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, null, null));
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.info.put(31, WidgetTestFixtures.info(true, 0));
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);

        assertFalse(controller.canReconfigure(31));
        assertEquals(LauncherWidgetHostController.AddResult.IGNORED,
            controller.reconfigureWidget(31));
        assertEquals(0, platform.configureLaunches);
    }
}
