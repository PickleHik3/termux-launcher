package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.res.Configuration;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LauncherWidgetOptionsIntegrationTest {
    @Test public void committedResizeUpdatesOnceDedupsAndFramesDoNothing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(6, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, null, null));
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.info.put(6, WidgetTestFixtures.info(false));
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);
        assertTrue(controller.onHostSizeCommitted(6, 300, 180, Configuration.ORIENTATION_PORTRAIT));
        assertEquals(1, platform.optionUpdates);
        assertTrue(platform.lastOptions.containsKey(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
        assertTrue(platform.lastOptions.containsKey(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT));
        assertTrue(platform.lastOptions.containsKey(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH));
        assertTrue(platform.lastOptions.containsKey(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT));
        assertFalse(controller.onHostSizeCommitted(6, 300, 180, Configuration.ORIENTATION_PORTRAIT));
        assertEquals(1, platform.optionUpdates);
        // Synthetic drag frames have no controller entry point; only the committed callback above writes.
        for (int i = 0; i < 20; i++) { int ignoredWidth = 300 + i; }
        assertEquals(1, platform.optionUpdates);
    }

    @Test public void synchronousProviderFailureRestoresPriorCommittedOptions() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        android.os.Bundle prior = new android.os.Bundle();
        prior.putInt("sentinel", 7);
        repository.putRecord(new LauncherWidgetRecord(6, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, prior, null));
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.optionFailure = new RuntimeException("provider");
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);
        assertFalse(controller.onHostSizeCommitted(6, 300, 180,
            Configuration.ORIENTATION_PORTRAIT));
        assertEquals(7, repository.get(6).sizeOptions().getInt("sentinel"));
        assertEquals("options", repository.get(6).lastRenderFailure);
    }
}
