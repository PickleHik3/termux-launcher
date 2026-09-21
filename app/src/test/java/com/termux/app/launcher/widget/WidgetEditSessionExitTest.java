package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
 * The two ways out of edit mode that the page's border tab offers: the tick keeps everything, the
 * cross puts the widgets back where the session found them — positions, sizes, pages and the page
 * count with them. Back and the swipe-away are unchanged, and still save.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetEditSessionExitTest {
    private static final int PANE_WIDTH = 600;
    private static final int PANE_HEIGHT = 800;

    @Test public void theTickClosesTheSessionAndKeepsTheMove() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 0);
        fixture.put(2, new WidgetCellRect(3, 4, 4, 5), 0);
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();
        fixture.dragWidgetTo(new WidgetCellRect(0, 0, 1, 1), new WidgetCellRect(1, 0, 2, 1));
        assertEquals(new WidgetCellRect(1, 0, 2, 1), fixture.repository.get(1).cell);

        fixture.pane.commitWidgetEdit();

        assertFalse(fixture.pane.widgetEditActive());
        assertEquals("the move stayed", new WidgetCellRect(1, 0, 2, 1),
            fixture.repository.get(1).cell);
        assertEquals(List.of(true, false), fixture.announced);
    }

    @Test public void theCrossPutsEveryMoveBack() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 0);
        fixture.put(2, new WidgetCellRect(3, 4, 4, 5), 0);
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();
        fixture.dragWidgetTo(new WidgetCellRect(0, 0, 1, 1), new WidgetCellRect(1, 0, 2, 1));
        assertEquals(new WidgetCellRect(1, 0, 2, 1), fixture.repository.get(1).cell);

        fixture.pane.discardWidgetEdit();

        assertFalse(fixture.pane.widgetEditActive());
        assertEquals("the widget is back where the session found it",
            new WidgetCellRect(0, 0, 1, 1), fixture.repository.get(1).cell);
        assertEquals(new WidgetCellRect(3, 4, 4, 5), fixture.repository.get(2).cell);
        assertEquals(List.of(true, false), fixture.announced);
    }

    @Test public void theCrossBringsAWidgetBackFromTheOtherPageAndRestoresTheCount() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 0);
        fixture.put(2, new WidgetCellRect(3, 4, 4, 5), 0);
        fixture.repository.trimEmptyPages();
        fixture.renderAndLayout();
        int pagesAtStart = fixture.repository.pageCount();
        assertEquals(1, pagesAtStart);
        fixture.controller.menuEditWidgets();
        fixture.layout();

        // The same crossing a finger makes, without the finger: a page past the last one, and the
        // widget carried onto it.
        assertEquals(1, fixture.repository.addPage());
        assertTrue(fixture.repository.putRecord(fixture.repository.get(1).withPage(1)));
        assertEquals(2, fixture.repository.pageCount());

        fixture.pane.discardWidgetEdit();

        assertEquals("the widget came back to its own page", 0, fixture.repository.get(1).page);
        assertEquals("and so did the page count", pagesAtStart, fixture.repository.pageCount());
    }

    /**
     * A page the user added by hand is part of the layout the session found, so the cross puts it
     * back as an empty page of theirs — not as an empty page the next trim takes away.
     */
    @Test public void theCrossPutsBackAPageAddedByHand() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 0);
        assertEquals(1, fixture.repository.addFreshPage());
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();

        // The widget is carried onto it, which spends its freshness.
        assertTrue(fixture.repository.putRecord(fixture.repository.get(1).withPage(1)));
        assertTrue(fixture.repository.freshPages().isEmpty());

        fixture.pane.discardWidgetEdit();

        assertEquals(0, fixture.repository.get(1).page);
        assertEquals(2, fixture.repository.pageCount());
        assertEquals("the empty page is the user's again",
            java.util.Collections.singleton(1), fixture.repository.freshPages());
        fixture.repository.trimEmptyPages();
        assertEquals(2, fixture.repository.pageCount());
    }

    @Test public void backStillSaves() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 0);
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();
        fixture.dragWidgetTo(new WidgetCellRect(0, 0, 1, 1), new WidgetCellRect(1, 0, 2, 1));

        assertTrue(fixture.controller.onBackPressed());

        assertFalse(fixture.pane.widgetEditActive());
        assertEquals("back is still a save", new WidgetCellRect(1, 0, 2, 1),
            fixture.repository.get(1).cell);
    }

    @Test public void aWidgetBinnedDuringTheSessionIsNotBroughtBack() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 0);
        fixture.put(2, new WidgetCellRect(3, 4, 4, 5), 0);
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();
        // The bin hands the widget to Android at once; nothing here still holds it.
        assertTrue(fixture.repository.removeRecord(2));

        fixture.pane.discardWidgetEdit();

        assertEquals(1, fixture.repository.records().size());
        assertEquals(new WidgetCellRect(0, 0, 1, 1), fixture.repository.get(1).cell);
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

        void put(int appWidgetId, WidgetCellRect cell, int page) {
            while (repository.pageCount() <= page) assertTrue(repository.addPage() >= 0);
            assertTrue(repository.putRecord(new LauncherWidgetRecord(appWidgetId,
                WidgetTestFixtures.PROVIDER, 0, LauncherWidgetRecord.State.PROVIDER_MISSING,
                cell, page, new Bundle(), null)));
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

        Rect paneBounds(WidgetCellRect cell) {
            Rect bounds = pane.grid().metrics().boundsFor(cell);
            bounds.offset(pane.grid().getLeft(), pane.grid().getTop());
            return bounds;
        }

        /** A whole move drag on the selected widget, through the chrome that carries it. */
        void dragWidgetTo(WidgetCellRect from, WidgetCellRect to) {
            Rect start = paneBounds(from);
            Rect end = paneBounds(to);
            dispatch(MotionEvent.ACTION_DOWN, start.centerX(), start.centerY(), 0L);
            dispatch(MotionEvent.ACTION_MOVE, end.centerX(), end.centerY(), 20L);
            dispatch(MotionEvent.ACTION_UP, end.centerX(), end.centerY(), 40L);
        }

        private void dispatch(int action, float x, float y, long time) {
            MotionEvent event = MotionEvent.obtain(0L, time, action, x, y, 0);
            pane.widgetEditOverlay().dispatchTouchEvent(event);
            event.recycle();
        }
    }
}
