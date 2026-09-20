package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * A render hides the edit chrome, and the page's border tab follows the session the host is told
 * about. The grid-size wheels change the grid while a session is open, which renders - so the
 * session has to come back on its own, or every step of the wheel closed the panel it lives in.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPaneEditSessionRenderTest {

    @Test public void aGridChangeWhileEditingKeepsTheSessionAndTellsTheHostNothing() {
        Fixture fixture = new Fixture();
        assertTrue(fixture.repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1))));
        fixture.controller.onWidgetRepositoryChanged(
            LauncherWidgetHostController.AddResult.IGNORED);
        fixture.controller.menuEditWidgets();
        assertTrue(fixture.pane.widgetEditActive());
        assertEquals(List.of(true), fixture.announced);

        assertTrue(fixture.widgets.applyGrid(7, 6));

        assertTrue("the chrome returns on the re-laid grid", fixture.pane.widgetEditActive());
        assertNotNull(fixture.pane.grid().cellForId(1));
        assertEquals("the host never heard the session end", List.of(true), fixture.announced);
    }

    @Test public void aRenderThatDropsTheEditedWidgetEndsTheSessionOnce() {
        Fixture fixture = new Fixture();
        assertTrue(fixture.repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1))));
        fixture.controller.onWidgetRepositoryChanged(
            LauncherWidgetHostController.AddResult.IGNORED);
        fixture.controller.menuEditWidgets();
        assertEquals(List.of(true), fixture.announced);

        assertTrue(fixture.repository.removeRecord(1));
        fixture.controller.onWidgetRepositoryChanged(
            LauncherWidgetHostController.AddResult.IGNORED);

        assertFalse(fixture.pane.widgetEditActive());
        assertEquals(List.of(true, false), fixture.announced);
    }

    private static LauncherWidgetRecord record(int id, WidgetCellRect cell) {
        return new LauncherWidgetRecord(id, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.PROVIDER_MISSING, cell, 0, new Bundle(), null);
    }

    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final LauncherWidgetRepository repository;
        final LauncherWidgetHostController widgets;
        final WidgetPaneView pane;
        final WidgetPaneController controller;
        final List<Boolean> announced = new ArrayList<>();

        Fixture() {
            activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            repository = WidgetTestFixtures.repository();
            WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
            widgets = new LauncherWidgetHostController(activity, repository, platform);
            pane = new WidgetPaneView(activity);
            activity.setContentView(pane);
            controller = new WidgetPaneController(pane, widgets, new WidgetPaneController.Host() {
                @Override public boolean reducedMotion() { return true; }
                @Override public boolean isWidgetSurfaceShowing() { return true; }
                @Override public void captureWidgetSurfaceOrigin() { }
                @Override public void restoreWidgetSurfaceOrigin() { }
                @Override public void onWidgetEditSessionChanged(boolean editing) {
                    announced.add(editing);
                }
            });
        }
    }
}
