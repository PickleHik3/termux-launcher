package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetProviderInfo;
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

/**
 * Pulling a widget's edge over its neighbour pushes that neighbour aside, exactly as dragging the
 * widget onto it does. The new span and every widget it moved go into the layout together, so a
 * refused write leaves the page untouched, and the cross puts all of them back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetResizeDisplacementTest {
    private static final int PANE_WIDTH = 600;
    private static final int PANE_HEIGHT = 800;

    @Test public void growingOverANeighbourPushesItAsideAndCommitsBothAtOnce() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1));
        fixture.put(2, new WidgetCellRect(2, 0, 3, 1));
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();
        assertEquals("the pencil selects the top-left widget",
            fixture.paneBounds(new WidgetCellRect(0, 0, 1, 1)),
            fixture.pane.widgetEditOverlay().frameBounds());

        Rect frame = fixture.paneBounds(new WidgetCellRect(0, 0, 1, 1));
        float edge = fixture.paneBounds(new WidgetCellRect(0, 0, 4, 1)).right;
        fixture.dispatch(MotionEvent.ACTION_DOWN, frame.right, frame.centerY(), 0);
        fixture.dispatch(MotionEvent.ACTION_MOVE, edge, frame.centerY(), 20);

        Rect was = fixture.pane.grid().metrics().boundsFor(new WidgetCellRect(2, 0, 3, 1));
        Rect willBe = fixture.pane.grid().metrics().boundsFor(new WidgetCellRect(2, 1, 3, 2));
        assertEquals("the neighbour slides to where it would go while the finger is down",
            (float) (willBe.top - was.top), fixture.pane.grid().cellForId(2).getTranslationY(),
            0.5f);
        assertEquals("and nothing is committed until it lifts", new WidgetCellRect(2, 0, 3, 1),
            fixture.repository.get(2).cell);

        fixture.dispatch(MotionEvent.ACTION_UP, edge, frame.centerY(), 40);

        assertEquals("the widget took the whole row", new WidgetCellRect(0, 0, 4, 1),
            fixture.repository.get(1).cell);
        assertEquals("and the preview gave way to the real layout", 0f,
            fixture.pane.grid().cellForId(2).getTranslationY(), 0.5f);
        assertNotEquals("and the widget that was in the way went somewhere else",
            new WidgetCellRect(2, 0, 3, 1), fixture.repository.get(2).cell);
        assertEquals("into the nearest hole under it", new WidgetCellRect(2, 1, 3, 2),
            fixture.repository.get(2).cell);
        assertTrue("the page the commit left behind is a layout the repository accepts",
            WidgetGridPlacementPolicy.validate(fixture.repository.gridDefinition(),
                fixture.repository.recordsOnPage(0)));
    }

    @Test public void shrinkingPushesNobody() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 3, 1));
        fixture.put(2, new WidgetCellRect(3, 0, 4, 1));
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();

        fixture.dragRightEdgeTo(new WidgetCellRect(0, 0, 3, 1), new WidgetCellRect(0, 0, 1, 1));

        assertEquals("the widget gave two columns back", new WidgetCellRect(0, 0, 1, 1),
            fixture.repository.get(1).cell);
        assertEquals("and its neighbour never moved", new WidgetCellRect(3, 0, 4, 1),
            fixture.repository.get(2).cell);
    }

    @Test public void aRefusedWriteLeavesNeitherTheWidgetNorItsNeighbourMoved() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1));
        fixture.put(2, new WidgetCellRect(2, 0, 3, 1));
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();

        fixture.memory.fail = true;
        fixture.dragRightEdgeTo(new WidgetCellRect(0, 0, 1, 1), new WidgetCellRect(0, 0, 4, 1));

        assertEquals("the resize went in as one write, and that write was refused",
            new WidgetCellRect(0, 0, 1, 1), fixture.repository.get(1).cell);
        assertEquals(new WidgetCellRect(2, 0, 3, 1), fixture.repository.get(2).cell);
        assertEquals("the frame is back on the span the widget still has",
            fixture.paneBounds(new WidgetCellRect(0, 0, 1, 1)),
            fixture.pane.widgetEditOverlay().frameBounds());
    }

    @Test public void theCrossPutsBackTheResizedWidgetAndEveryoneItPushed() {
        Fixture fixture = new Fixture();
        fixture.put(1, new WidgetCellRect(0, 0, 1, 1));
        fixture.put(2, new WidgetCellRect(2, 0, 3, 1));
        fixture.renderAndLayout();
        fixture.controller.menuEditWidgets();
        fixture.layout();

        fixture.dragRightEdgeTo(new WidgetCellRect(0, 0, 1, 1), new WidgetCellRect(0, 0, 4, 1));
        assertEquals(new WidgetCellRect(0, 0, 4, 1), fixture.repository.get(1).cell);
        assertEquals(new WidgetCellRect(2, 1, 3, 2), fixture.repository.get(2).cell);

        fixture.controller.discardEditSession();

        assertEquals("the widget has the span it started with", new WidgetCellRect(0, 0, 1, 1),
            fixture.repository.get(1).cell);
        assertEquals("and the neighbour is back where it was pushed from",
            new WidgetCellRect(2, 0, 3, 1), fixture.repository.get(2).cell);
    }

    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        final LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        final WidgetTestFixtures.Platform platform;
        final LauncherWidgetHostController widgets;
        final WidgetPaneView pane;
        final WidgetPaneController controller;

        Fixture() {
            activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            platform = new WidgetTestFixtures.Platform(activity);
            widgets = new LauncherWidgetHostController(activity, repository, platform);
            pane = new WidgetPaneView(activity);
            activity.setContentView(pane);
            controller = new WidgetPaneController(pane, widgets, new WidgetPaneController.Host() {
                @Override public boolean reducedMotion() { return true; }
                @Override public boolean isWidgetSurfaceShowing() { return true; }
                @Override public void captureWidgetSurfaceOrigin() { }
                @Override public void restoreWidgetSurfaceOrigin() { }
            });
        }

        /** A live widget whose provider says both edges may be pulled. */
        void put(int appWidgetId, WidgetCellRect cell) {
            AppWidgetProviderInfo info = WidgetTestFixtures.info(false);
            info.resizeMode = AppWidgetProviderInfo.RESIZE_HORIZONTAL
                | AppWidgetProviderInfo.RESIZE_VERTICAL;
            info.minResizeWidth = 1;
            info.minResizeHeight = 1;
            platform.info.put(appWidgetId, info);
            assertTrue(repository.putRecord(new LauncherWidgetRecord(appWidgetId,
                WidgetTestFixtures.PROVIDER, 0, LauncherWidgetRecord.State.ACTIVE,
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

        /** Takes the right-edge handle of {@code from} and pulls it to the right edge of {@code to}. */
        void dragRightEdgeTo(WidgetCellRect from, WidgetCellRect to) {
            Rect frame = paneBounds(from);
            float y = frame.centerY();
            dispatch(MotionEvent.ACTION_DOWN, frame.right, y, 0);
            dispatch(MotionEvent.ACTION_MOVE, paneBounds(to).right, y, 20);
            dispatch(MotionEvent.ACTION_UP, paneBounds(to).right, y, 40);
        }

        void dispatch(int action, float x, float y, long time) {
            MotionEvent event = MotionEvent.obtain(0, time, action, x, y, 0);
            pane.widgetEditOverlay().dispatchTouchEvent(event);
            event.recycle();
        }
    }
}
