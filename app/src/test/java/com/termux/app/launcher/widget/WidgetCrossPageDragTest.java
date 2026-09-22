package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A widget held against the pane's edge while it is being dragged turns the page under it, and the
 * drop lands it on whatever page it ended over. Past the last page it makes one, which the drop
 * keeps and anything else takes away again. A page with no room for it refuses in red and springs
 * the widget home.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetCrossPageDragTest {
    private static final int PANE_WIDTH = 600;
    private static final int PANE_HEIGHT = 800;
    /** Comfortably past the 350 ms the edge band waits before it turns the page. */
    private static final long PAST_THE_EDGE_PAUSE_MS = 400L;

    @Test public void draggingPastTheLastPageMakesAPageAndTheDropKeepsIt() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 0);
        // A second widget, so the page the first one leaves is not emptied by the drag.
        fixture.put(2, new WidgetCellRect(3, 4, 4, 5), 0);
        fixture.repository.trimEmptyPages();
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();
        assertEquals("one page, and nothing behind it", 1, fixture.repository.pageCount());

        Rect home = fixture.paneBounds(new WidgetCellRect(0, 0, 1, 1));
        fixture.down(home.centerX(), home.centerY());
        fixture.move(PANE_WIDTH - 5, home.centerY());
        fixture.settle();

        assertEquals("a page was made under the widget", 2, fixture.repository.pageCount());
        assertEquals("and the pane turned onto it", 1, fixture.controller.currentPage());
        assertTrue("the widget is in the air, not on either page",
            fixture.pane.widgetDragLayer().isLifted());
        assertNotNull("the new page shows where it would land",
            fixture.pane.widgetEditOverlay().ghostBounds());
        assertFalse(fixture.pane.widgetEditOverlay().ghostBlocked());
        assertTrue("the session is still the user's", fixture.pane.widgetEditActive());

        fixture.up(PANE_WIDTH - 5, home.centerY());

        assertEquals("the widget landed on the page it was carried to",
            1, fixture.repository.get(1).page);
        assertEquals("which is now a page like any other", 2, fixture.repository.pageCount());
        assertEquals(1, fixture.controller.currentPage());
        assertFalse(fixture.pane.widgetDragLayer().isLifted());
        assertEquals("the session never ended", List.of(true), fixture.announced);
    }

    @Test public void aPageMadeByTheDragGoesAgainWhenTheWidgetIsCarriedBack() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 0);
        fixture.put(2, new WidgetCellRect(3, 4, 4, 5), 0);
        fixture.repository.trimEmptyPages();
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();

        Rect home = fixture.paneBounds(new WidgetCellRect(0, 0, 1, 1));
        fixture.down(home.centerX(), home.centerY());
        fixture.move(PANE_WIDTH - 5, home.centerY());
        fixture.settle();
        assertEquals(2, fixture.repository.pageCount());

        // Back to the page it came from, and dropped there.
        fixture.move(5, home.centerY());
        fixture.settle();
        assertEquals(0, fixture.controller.currentPage());
        fixture.up(5, home.centerY());

        assertEquals("the widget is back on its own page", 0, fixture.repository.get(1).page);
        assertEquals("and the page the drag made went with it",
            1, fixture.repository.pageCount());
        assertEquals(0, fixture.controller.currentPage());
    }

    @Test public void oneDragMakesAtMostOnePage() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 0);
        fixture.put(2, new WidgetCellRect(3, 4, 4, 5), 0);
        fixture.repository.trimEmptyPages();
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();

        Rect home = fixture.paneBounds(new WidgetCellRect(0, 0, 1, 1));
        fixture.down(home.centerX(), home.centerY());
        // Held at the trailing edge long enough for three turns.
        fixture.move(PANE_WIDTH - 5, home.centerY());
        fixture.settle();
        fixture.settle();
        fixture.settle();

        assertEquals("one page was made, not three", 2, fixture.repository.pageCount());
        assertEquals(1, fixture.controller.currentPage());
        fixture.up(PANE_WIDTH - 5, home.centerY());
        assertEquals(1, fixture.repository.get(1).page);
        assertEquals(2, fixture.repository.pageCount());
    }

    @Test public void theLeadingEdgeNeverMakesAPage() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 0);
        fixture.repository.trimEmptyPages();
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();

        Rect home = fixture.paneBounds(new WidgetCellRect(0, 0, 1, 1));
        fixture.down(home.centerX(), home.centerY());
        fixture.move(5, home.centerY());
        fixture.settle();
        fixture.settle();

        assertEquals("nothing is made before the first page", 1, fixture.repository.pageCount());
        assertEquals(0, fixture.controller.currentPage());
        fixture.up(5, home.centerY());
        assertEquals(0, fixture.repository.get(1).page);
    }

    @Test public void aHoldThatBecomesADragIsNotStolenByThePageSwipe() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 0);
        fixture.put(2, new WidgetCellRect(0, 0, 1, 1), 1);
        fixture.repository.trimEmptyPages();
        fixture.renderAndLayout();
        assertEquals("two pages, so the page watches every press for a swipe",
            2, fixture.repository.pageCount());
        Rect home = fixture.paneBounds(new WidgetCellRect(0, 0, 1, 1));
        // The whole gesture goes in at the pane, the way the screen delivers it.
        long downTime = android.os.SystemClock.uptimeMillis();
        fixture.paneEvent(MotionEvent.ACTION_DOWN, home.centerX(), home.centerY(), downTime, 0L);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(
            android.view.ViewConfiguration.getLongPressTimeout() + 100, TimeUnit.MILLISECONDS);
        assertTrue("the hold opened the edit session", fixture.pane.widgetEditActive());
        fixture.paneEvent(MotionEvent.ACTION_MOVE, home.centerX() + home.width(),
            home.centerY(), downTime, 60L);
        fixture.paneEvent(MotionEvent.ACTION_MOVE, home.centerX() + home.width() + 4,
            home.centerY(), downTime, 80L);
        assertNotNull("the sideways move is the drag's, not a page swipe: a landing ghost shows",
            fixture.pane.widgetEditOverlay().ghostBounds());
        fixture.paneEvent(MotionEvent.ACTION_UP, home.centerX() + home.width() + 4,
            home.centerY(), downTime, 100L);
        assertTrue("and the drop moved the widget sideways instead of turning the page",
            fixture.repository.get(1).cell.left >= 1);
        assertEquals(0, fixture.controller.currentPage());
    }

    @Test public void leavingTheBandStopsThePageTurning() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 0);
        fixture.repository.trimEmptyPages();
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();

        Rect home = fixture.paneBounds(new WidgetCellRect(0, 0, 1, 1));
        fixture.down(home.centerX(), home.centerY());
        fixture.move(PANE_WIDTH - 5, home.centerY());
        fixture.move(PANE_WIDTH / 2, home.centerY());
        fixture.settle();

        assertEquals("the finger left the band before the pause was up",
            0, fixture.controller.currentPage());
        assertEquals("so no page was made either", 1, fixture.repository.pageCount());
        assertFalse(fixture.pane.widgetDragLayer().isLifted());

        fixture.up(PANE_WIDTH / 2, home.centerY());
        assertEquals(0, fixture.repository.get(1).page);
    }

    @Test public void aPageWithNoRoomRefusesInRedAndSendsTheWidgetHome() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 0);
        // Page 1 is full to its last cell, so nothing can be put down on it.
        fixture.put(2, new WidgetCellRect(0, 0, 4, 5), 1);
        fixture.repository.trimEmptyPages();
        fixture.renderAndLayout();
        fixture.controller.setCurrentPage(0);
        fixture.controller.menuEditWidgets();
        fixture.layout();

        Rect home = fixture.paneBounds(new WidgetCellRect(0, 0, 1, 1));
        fixture.down(home.centerX(), home.centerY());
        fixture.move(PANE_WIDTH - 5, home.centerY());
        fixture.settle();

        assertEquals(1, fixture.controller.currentPage());
        assertTrue("the ghost says the page has no room",
            fixture.pane.widgetEditOverlay().ghostBlocked());

        fixture.up(PANE_WIDTH - 5, home.centerY());

        assertEquals("the widget stayed where it was", 0, fixture.repository.get(1).page);
        assertEquals(new WidgetCellRect(0, 0, 1, 1), fixture.repository.get(1).cell);
        assertEquals("and the page came back with it", 0, fixture.controller.currentPage());
        assertEquals("No room on this page.", fixture.noticeText());
    }

    @Test public void theLeadingEdgeCarriesAWidgetBackAPage() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 1);
        // The page it is carried onto keeps a widget of its own, so it is there to be carried to.
        fixture.put(2, new WidgetCellRect(3, 4, 4, 5), 0);
        fixture.repository.trimEmptyPages();
        fixture.renderAndLayout();
        fixture.controller.setCurrentPage(1);
        fixture.controller.menuEditWidgets();
        fixture.layout();

        Rect home = fixture.paneBounds(new WidgetCellRect(0, 0, 1, 1));
        fixture.down(home.centerX(), home.centerY());
        fixture.move(5, home.centerY());
        fixture.settle();
        assertEquals(0, fixture.controller.currentPage());
        fixture.up(5, home.centerY());

        assertEquals(0, fixture.repository.get(1).page);
        assertEquals("the page it left had nothing else on it, so it went",
            1, fixture.repository.pageCount());
    }

    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final LauncherWidgetRepository repository;
        final LauncherWidgetHostController widgets;
        final WidgetPaneView pane;
        final WidgetPaneController controller;
        final List<Boolean> announced = new ArrayList<>();
        private final int[] paneLocation = new int[2];

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

        /** The edge bands are measured off the pane, so the raw stream has to be too. */
        private float raw(float paneX) {
            pane.getLocationOnScreen(paneLocation);
            return paneX + paneLocation[0];
        }

        void down(float x, float y) {
            dispatch(MotionEvent.ACTION_DOWN, x, y, 0L);
        }

        void move(float paneX, float y) {
            dispatch(MotionEvent.ACTION_MOVE, raw(paneX), y, 20L);
        }

        void up(float paneX, float y) {
            dispatch(MotionEvent.ACTION_UP, raw(paneX), y, 40L);
        }

        void paneEvent(int action, float x, float y, long downTime, long offset) {
            MotionEvent event = MotionEvent.obtain(downTime, downTime + offset, action, x, y, 0);
            pane.dispatchTouchEvent(event);
            event.recycle();
        }
        private void dispatch(int action, float x, float y, long time) {
            MotionEvent event = MotionEvent.obtain(0L, time, action, x, y, 0);
            pane.widgetEditOverlay().dispatchTouchEvent(event);
            event.recycle();
        }

        void settle() {
            Shadows.shadowOf(Looper.getMainLooper())
                .idleFor(PAST_THE_EDGE_PAUSE_MS, TimeUnit.MILLISECONDS);
        }

        String noticeText() {
            android.widget.TextView notice = pane.findViewById(com.termux.R.id.widget_pane_notice);
            return notice.getVisibility() == View.VISIBLE ? notice.getText().toString() : null;
        }
    }
}
