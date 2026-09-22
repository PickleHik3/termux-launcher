package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetHostView;
import android.content.res.Configuration;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * A hosted widget keeps whatever day or night it was inflated in — AppWidgetHostView has no
 * configuration hook. These pin the host view being thrown away when the mode moves under it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetHostViewNightModeTest {
    @Test public void aHostViewBuiltInTheOtherModeIsMadeAgain() {
        Fixture fixture = new Fixture();
        AppWidgetHostView first = fixture.controller.createHostView(7);
        assertSame("precondition: a second render reuses the view", first,
            fixture.controller.createHostView(7));

        setNightMode(fixture.activity, Configuration.UI_MODE_NIGHT_YES);

        assertNotSame("the widget must be inflated again in the mode the launcher is now in",
            first, fixture.controller.createHostView(7));
    }

    @Test public void aHostViewSurvivesEverythingElse() {
        Fixture fixture = new Fixture();
        AppWidgetHostView first = fixture.controller.createHostView(7);

        Configuration configuration = fixture.activity.getResources().getConfiguration();
        configuration.fontScale = configuration.fontScale + 0.5f;
        fixture.controller.onStart();

        assertSame("only day and night rebuilds a widget", first,
            fixture.controller.createHostView(7));
    }

    private static void setNightMode(Activity activity, int night) {
        Configuration configuration = activity.getResources().getConfiguration();
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
    }

    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final LauncherWidgetHostController controller;

        Fixture() {
            setNightMode(activity, Configuration.UI_MODE_NIGHT_NO);
            LauncherWidgetRepository repository = WidgetTestFixtures.repository();
            repository.putRecord(new LauncherWidgetRecord(7, WidgetTestFixtures.PROVIDER, 0,
                LauncherWidgetRecord.State.ACTIVE, null, null));
            WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
            platform.info.put(7, WidgetTestFixtures.info(false));
            controller = new LauncherWidgetHostController(activity, repository, platform);
        }
    }
}
