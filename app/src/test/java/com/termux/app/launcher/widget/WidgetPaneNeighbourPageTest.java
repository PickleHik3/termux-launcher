package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import com.termux.app.launcher.home.HomePageTransition;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** The Home pager draws the page the finger is bringing in beside the one it is carrying away. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPaneNeighbourPageTest {
    private final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    private LauncherWidgetRepository repository;
    private WidgetPaneView pane;
    private WidgetPaneController controller;

    private void setUp() {
        activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        repository = WidgetTestFixtures.repository();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        LauncherWidgetHostController widgets =
            new LauncherWidgetHostController(activity, repository, platform);
        assertEquals(1, repository.addPage());
        assertTrue(repository.putRecord(active(20, 0)));
        assertTrue(repository.putRecord(active(21, 1)));
        platform.info.put(20, WidgetTestFixtures.info(false));
        platform.info.put(21, WidgetTestFixtures.info(false));
        pane = new WidgetPaneView(activity);
        activity.setContentView(pane);
        controller = new WidgetPaneController(pane, widgets, new WidgetPaneController.Host() {
            @Override public boolean reducedMotion() { return false; }
            @Override public boolean isWidgetSurfaceShowing() { return true; }
            @Override public void captureWidgetSurfaceOrigin() { }
            @Override public void restoreWidgetSurfaceOrigin() { }
        });
        pane.setPageMotion(HomePageTransition.SLIDE, 1f);
        controller.setCurrentPage(0);
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static LauncherWidgetRecord active(int id, int page) {
        return new LauncherWidgetRecord(id, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(0, 0, 2, 2), page,
            new Bundle(), null);
    }

    private void touch(int action, long time, float x, float y) {
        MotionEvent event = MotionEvent.obtain(1000L, time, action, x, y, 0);
        pane.dispatchTouchEvent(event);
        event.recycle();
    }

    @Test public void theNextPageTravelsInWithTheDragAndIsTheGridAfterTheCommit() {
        setUp();
        int w = pane.getWidth();
        assertTrue(w > 0);
        float y = pane.getHeight() / 2f;
        float x0 = w * 7f / 8f;
        touch(MotionEvent.ACTION_DOWN, 1000L, x0, y);
        float drag = 0f;
        for (int i = 1; i <= 6; i++) {
            drag = -w * i / 12f;
            touch(MotionEvent.ACTION_MOVE, 1000L + i * 16L, x0 + drag, y + 1);
        }
        WidgetGridView layer = pane.neighbourGrid();
        assertNotNull("a drag toward page 2 draws page 2 beside page 1", layer);
        assertEquals(1, pane.neighbourPage());
        assertNotNull(layer.cellForId(21));
        assertNull(layer.cellForId(20));
        assertEquals("the neighbour stands one width to the side of the page",
            drag + w, layer.getTranslationX(), 1f);
        assertEquals(drag, pane.grid().getTranslationX(), 1f);
        View host = layer.cellForId(21).getChildAt(0);
        assertNotNull(host);

        // Further on the translation keeps following.
        touch(MotionEvent.ACTION_MOVE, 1200L, x0 - w * 0.6f, y + 1);
        assertEquals(-w * 0.6f + w, layer.getTranslationX(), 1f);

        // Let go past the commit: page 2 is the grid, with the very views the layer held.
        touch(MotionEvent.ACTION_UP, 1210L, x0 - w * 0.6f, y + 1);
        assertEquals(1, controller.currentPage());
        assertNotNull(pane.grid().cellForId(21));
        assertSame("the live widget view is moved, not made again", host,
            pane.grid().cellForId(21).getChildAt(0));
        WidgetGridView leaving = pane.neighbourGrid();
        assertNotNull("the page that leaves travels on beside it", leaving);
        assertEquals(0, pane.neighbourPage());
        assertNotNull(leaving.cellForId(20));
        assertNull("the new page is not also drawn on the layer", leaving.cellForId(21));
        float shift = pane.contentShiftFraction() * w;
        assertEquals(shift - w, leaving.getTranslationX(), 1f);
        assertEquals(shift, pane.grid().getTranslationX(), 1f);
    }

    @Test public void aPullAgainstTheEndHasNoNeighbour() {
        setUp();
        controller.setCurrentPage(1);
        shadowOf(Looper.getMainLooper()).idle();
        float y = pane.getHeight() / 2f;
        float x0 = pane.getWidth() * 3f / 4f;
        touch(MotionEvent.ACTION_DOWN, 1000L, x0, y);
        // Page 2 is the last: a drag to the left only resists, with no page beside it.
        for (int i = 1; i <= 4; i++) touch(MotionEvent.ACTION_MOVE, 1000L + i * 16L, x0 - i * 40f, y + 1);
        assertNull(pane.neighbourGrid());
    }
}
