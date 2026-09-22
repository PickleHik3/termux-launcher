package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The empty-page rule is applied to whatever the repository loaded, not only to later changes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPaneLoadTrimTest {
    @Test public void pagesSavedBeforeTheRuleAreTrimmedWhenTheControllerStarts() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertTrue(repository.addPage() >= 0);
        assertTrue(repository.addPage() >= 0);
        assertEquals("three empty pages, as an older build could have saved them",
            3, repository.pageCount());
        LauncherWidgetHostController widgets = new LauncherWidgetHostController(activity,
            repository, new WidgetTestFixtures.Platform(activity));
        WidgetPaneView pane = new WidgetPaneView(activity);
        activity.setContentView(pane);
        new WidgetPaneController(pane, widgets, new WidgetPaneController.Host() {
            @Override public boolean reducedMotion() { return true; }
            @Override public boolean isWidgetSurfaceShowing() { return true; }
            @Override public void captureWidgetSurfaceOrigin() { }
            @Override public void restoreWidgetSurfaceOrigin() { }
        });
        assertEquals("no widgets anywhere: one page", 1, repository.pageCount());
    }

    @Test public void aPageTheUserAddedByHandIsStillThereAfterARestart() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertTrue(repository.putRecord(new LauncherWidgetRecord(4,
            WidgetTestFixtures.PROVIDER, 0, LauncherWidgetRecord.State.PROVIDER_MISSING,
            new WidgetCellRect(0, 0, 1, 1), 0, new android.os.Bundle(), null)));
        assertEquals(1, repository.addFreshPage());
        LauncherWidgetHostController widgets = new LauncherWidgetHostController(activity,
            repository, new WidgetTestFixtures.Platform(activity));
        WidgetPaneView pane = new WidgetPaneView(activity);
        activity.setContentView(pane);
        new WidgetPaneController(pane, widgets, new WidgetPaneController.Host() {
            @Override public boolean reducedMotion() { return true; }
            @Override public boolean isWidgetSurfaceShowing() { return true; }
            @Override public void captureWidgetSurfaceOrigin() { }
            @Override public void restoreWidgetSurfaceOrigin() { }
        });
        assertEquals("the page the user asked for survives the load", 2, repository.pageCount());
    }
}
