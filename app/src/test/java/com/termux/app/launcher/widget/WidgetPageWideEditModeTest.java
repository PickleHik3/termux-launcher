package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Edit mode belongs to the page, not to one widget: every other widget on it is outlined, and a
 * press on one of those outlines takes the selection and carries on as its move drag. Only a press
 * on empty space still ends the session.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPageWideEditModeTest {
    private static final int PANE_WIDTH = 600;
    private static final int PANE_HEIGHT = 800;

    @Test public void pressingAnotherWidgetSelectsItAndDragsItWithTheSameFinger() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1));
        fixture.put(2, new WidgetCellRect(1, 0, 2, 1));
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();

        WidgetEditOverlayView overlay = fixture.pane.widgetEditOverlay();
        assertEquals("the pencil selects the top-left widget",
            fixture.paneBounds(new WidgetCellRect(0, 0, 1, 1)), overlay.frameBounds());

        Rect second = fixture.paneBounds(new WidgetCellRect(1, 0, 2, 1));
        dispatch(overlay, MotionEvent.ACTION_DOWN, second.centerX(), second.centerY(), 0);

        assertEquals("the frame moved to the pressed widget", second, overlay.frameBounds());
        assertEquals("switching the selection is not a session change",
            List.of(true), fixture.announced);

        Rect target = fixture.paneBounds(new WidgetCellRect(1, 1, 2, 2));
        dispatch(overlay, MotionEvent.ACTION_MOVE, second.centerX(),
            second.centerY() + (target.top - second.top), 20);
        dispatch(overlay, MotionEvent.ACTION_UP, second.centerX(),
            second.centerY() + (target.top - second.top), 40);

        assertEquals("the pressed widget is the one that moved",
            new WidgetCellRect(1, 1, 2, 2), fixture.repository.get(2).cell);
        assertEquals("the widget the pencil had selected stayed put",
            new WidgetCellRect(0, 0, 1, 1), fixture.repository.get(1).cell);
        assertEquals(List.of(true), fixture.announced);
    }

    @Test public void pressingEmptySpaceStillEndsTheSessionOnce() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1));
        fixture.put(2, new WidgetCellRect(1, 0, 2, 1));
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();
        assertTrue(fixture.pane.widgetEditActive());

        Rect empty = fixture.paneBounds(new WidgetCellRect(0, 4, 1, 5));
        dispatch(fixture.pane.widgetEditOverlay(), MotionEvent.ACTION_DOWN,
            empty.centerX(), empty.centerY(), 0);

        assertFalse(fixture.pane.widgetEditActive());
        assertEquals(List.of(true, false), fixture.announced);
    }

    @Test public void everyOtherWidgetOnThePageIsOutlinedAndFollowsTheGrid() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1));
        fixture.put(2, new WidgetCellRect(1, 0, 2, 1));
        fixture.put(3, new WidgetCellRect(2, 0, 3, 1));
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();

        WidgetEditOverlayView overlay = fixture.pane.widgetEditOverlay();
        assertEquals("every widget but the selected one is outlined",
            List.of(2, 3), ids(overlay.outlines()));
        assertEquals(fixture.paneBounds(fixture.repository.get(2).cell),
            overlay.outlines().get(0).bounds);
        assertEquals(fixture.paneBounds(fixture.repository.get(3).cell),
            overlay.outlines().get(1).bounds);

        Rect before = overlay.outlines().get(0).bounds;
        assertTrue(fixture.widgets.applyGrid(8, 3));

        assertEquals(List.of(2, 3), ids(overlay.outlines()));
        assertNotEquals("a re-laid grid moves the outlines with it",
            before, overlay.outlines().get(0).bounds);
        assertEquals(fixture.paneBounds(fixture.repository.get(2).cell),
            overlay.outlines().get(0).bounds);
        assertEquals(fixture.paneBounds(fixture.repository.get(3).cell),
            overlay.outlines().get(1).bounds);
    }

    @Test public void anEmptyPageOffersNothingToEdit() {
        Fixture fixture = new Fixture();
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();

        assertFalse(fixture.pane.widgetEditActive());
        assertEquals(List.of(), fixture.announced);
    }

    private static List<Integer> ids(List<WidgetEditOverlayView.Outline> outlines) {
        List<Integer> ids = new ArrayList<>();
        for (WidgetEditOverlayView.Outline outline : outlines) ids.add(outline.appWidgetId);
        return ids;
    }

    private static void dispatch(View view, int action, float x, float y, long time) {
        MotionEvent event = MotionEvent.obtain(0, time, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
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

        void put(int appWidgetId, WidgetCellRect cell) {
            assertTrue(repository.putRecord(new LauncherWidgetRecord(appWidgetId,
                WidgetTestFixtures.PROVIDER, 0, LauncherWidgetRecord.State.PROVIDER_MISSING,
                cell, 0, new Bundle(), null)));
        }

        void renderAndLayout() {
            controller.onWidgetRepositoryChanged(
                LauncherWidgetHostController.AddResult.IGNORED);
            layout();
        }

        void layout() {
            pane.measure(View.MeasureSpec.makeMeasureSpec(PANE_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(PANE_HEIGHT, View.MeasureSpec.EXACTLY));
            pane.layout(0, 0, PANE_WIDTH, PANE_HEIGHT);
        }

        /** The same pane-space rectangle the controller hands the overlay. */
        Rect paneBounds(WidgetCellRect cell) {
            Rect bounds = pane.grid().metrics().boundsFor(cell);
            bounds.offset(pane.grid().getLeft(), pane.grid().getTop());
            return bounds;
        }
    }
}
