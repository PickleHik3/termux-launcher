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

    @Test public void aSecondFingerSwipeTurnsThePageUnderTheDraggedWidget() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1), 0);
        fixture.put(2, new WidgetCellRect(3, 4, 4, 5), 0);
        fixture.put(3, new WidgetCellRect(3, 4, 4, 5), 1);
        fixture.repository.trimEmptyPages();
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();
        assertEquals(2, fixture.repository.pageCount());

        Rect home = fixture.paneBounds(new WidgetCellRect(0, 0, 1, 1));
        float x = home.centerX() + 2f, y = home.centerY();
        fixture.finger(MotionEvent.ACTION_DOWN, 0, new int[] {0}, new float[][] {{x - 2f, y}});
        fixture.finger(MotionEvent.ACTION_MOVE, 0, new int[] {0}, new float[][] {{x, y}});
        // The second finger lands on the right and swipes left past the pager's distance.
        float sx = PANE_WIDTH * 0.9f, sy = PANE_HEIGHT * 0.7f;
        fixture.finger(MotionEvent.ACTION_POINTER_DOWN, 1, new int[] {0, 1},
            new float[][] {{x, y}, {sx, sy}});
        for (float travel = 40f; travel <= PANE_WIDTH * 0.5f; travel += 40f) {
            fixture.finger(MotionEvent.ACTION_MOVE, 0, new int[] {0, 1},
                new float[][] {{x, y}, {sx - travel, sy}});
        }
        assertEquals("the swipe turned the page once", 1, fixture.controller.currentPage());
        assertTrue("the widget is in the air", fixture.pane.widgetDragLayer().isLifted());

        // The second finger lifts first: that is not the drop.
        fixture.finger(MotionEvent.ACTION_POINTER_UP, 1, new int[] {0, 1},
            new float[][] {{x, y}, {sx - PANE_WIDTH * 0.5f, sy}});
        assertTrue("still carried", fixture.pane.widgetDragLayer().isLifted());
        assertEquals(1, fixture.controller.currentPage());

        fixture.finger(MotionEvent.ACTION_UP, 0, new int[] {0}, new float[][] {{x, y}});
        assertFalse(fixture.pane.widgetDragLayer().isLifted());
        assertEquals("dropped on the page the swipe brought in", 1, fixture.repository.get(1).page);
        assertEquals("under the carrying finger", new WidgetCellRect(0, 0, 1, 1),
            fixture.repository.get(1).cell);
    }

    @Test public void theCarryingFingerLiftingFirstIsTheDrop() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(3, 4, 4, 5), 0);
        fixture.put(4, new WidgetCellRect(0, 0, 1, 1), 1);
        fixture.repository.trimEmptyPages();
        fixture.renderAndLayout();
        fixture.controller.setCurrentPage(1);
        fixture.controller.menuEditWidgets();
        fixture.layout();

        Rect home = fixture.paneBounds(new WidgetCellRect(0, 0, 1, 1));
        float x = home.centerX(), y = home.centerY();
        fixture.finger(MotionEvent.ACTION_DOWN, 0, new int[] {0}, new float[][] {{x, y}});
        float sx = PANE_WIDTH * 0.1f, sy = PANE_HEIGHT * 0.7f;
        fixture.finger(MotionEvent.ACTION_POINTER_DOWN, 1, new int[] {0, 1},
            new float[][] {{x, y}, {sx, sy}});
        for (float travel = 40f; travel <= PANE_WIDTH * 0.5f; travel += 40f) {
            fixture.finger(MotionEvent.ACTION_MOVE, 0, new int[] {0, 1},
                new float[][] {{x, y}, {sx + travel, sy}});
        }
        assertEquals("a right swipe brings the previous page", 0, fixture.controller.currentPage());

        Rect target = fixture.paneBounds(new WidgetCellRect(1, 1, 2, 2));
        float tx = target.centerX(), ty = target.centerY(), ex = sx + PANE_WIDTH * 0.5f;
        fixture.finger(MotionEvent.ACTION_MOVE, 0, new int[] {0, 1},
            new float[][] {{tx, ty}, {ex, sy}});
        // The carrying finger lifts while the second is still down: that is the drop.
        fixture.finger(MotionEvent.ACTION_POINTER_UP, 0, new int[] {0, 1},
            new float[][] {{tx, ty}, {ex, sy}});
        assertFalse("dropped", fixture.pane.widgetDragLayer().isLifted());
        assertEquals(0, fixture.repository.get(4).page);
        assertEquals("where the carrying finger lifted, not the swiping one",
            new WidgetCellRect(1, 1, 2, 2), fixture.repository.get(4).cell);

        // The second finger goes on, swipes and lifts; it moves nothing and turns nothing.
        fixture.finger(MotionEvent.ACTION_MOVE, 0, new int[] {1}, new float[][] {{30f, sy}});
        fixture.finger(MotionEvent.ACTION_UP, 0, new int[] {1}, new float[][] {{30f, sy}});
        assertEquals(new WidgetCellRect(1, 1, 2, 2), fixture.repository.get(4).cell);
        assertEquals(0, fixture.repository.get(4).page);
        assertEquals(0, fixture.controller.currentPage());
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

        private long fingerTime;

        /**
         * One event of a several-finger gesture delivered at the pane, as the screen would:
         * {@code ids} and {@code points} are every finger in the event, and for a pointer event
         * {@code actionIndex} is the one that lands or lifts.
         */
        void finger(int action, int actionIndex, int[] ids, float[][] points) {
            int count = ids.length;
            MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
            MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
            for (int i = 0; i < count; i++) {
                properties[i] = new MotionEvent.PointerProperties();
                properties[i].id = ids[i];
                properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
                coords[i] = new MotionEvent.PointerCoords();
                coords[i].x = points[i][0];
                coords[i].y = points[i][1];
                coords[i].pressure = 1f;
                coords[i].size = 1f;
            }
            int full = action;
            if (action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_POINTER_UP) {
                full = action | (actionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            }
            if (action == MotionEvent.ACTION_DOWN) fingerTime = 0L;
            fingerTime += 16L;
            MotionEvent event = MotionEvent.obtain(0L, fingerTime, full, count, properties,
                coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
            pane.dispatchTouchEvent(event);
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
