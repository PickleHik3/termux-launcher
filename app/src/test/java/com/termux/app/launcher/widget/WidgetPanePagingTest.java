package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPanePagingTest {
    @Test public void theAddedPageIsWhereASwipeGoesAndRemoveGivesItBack() {
        Fixture fixture = new Fixture();
        assertEquals(0, fixture.controller.currentPage());
        fixture.controller.menuAddPage();
        assertEquals("the + turns to the page it added", 1, fixture.controller.currentPage());
        fixture.controller.setCurrentPage(1);
        assertEquals(2, fixture.repository.pageCount());
        assertEquals(1, fixture.controller.currentPage());
        assertEquals(1, fixture.pane.currentPage());
        fixture.controller.menuRemovePage();
        assertEquals(1, fixture.repository.pageCount());
        assertEquals(0, fixture.controller.currentPage());
    }

    @Test public void renderShowsOnlyTheCurrentPagesCellsAndLeavingHomeWaitsOnTheLastPage() {
        Fixture fixture = new Fixture();
        assertEquals(1, fixture.repository.addPage());
        assertTrue(fixture.repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertTrue(fixture.repository.putRecord(record(2, new WidgetCellRect(0, 0, 1, 1), 1)));
        fixture.controller.onWidgetRepositoryChanged(
            LauncherWidgetHostController.AddResult.IGNORED);
        fixture.controller.setCurrentPage(0);
        assertNotNull(fixture.pane.grid().cellForId(1));
        assertNull("page-1 widget must not render on page 0",
            fixture.pane.grid().cellForId(2));
        fixture.controller.setCurrentPage(1);
        assertNotNull(fixture.pane.grid().cellForId(2));
        assertNull(fixture.pane.grid().cellForId(1));

        fixture.controller.setCurrentPage(0);
        fixture.controller.onWallPageShown(false);
        assertEquals("Home waits on its last page, next to the seam", 1,
            fixture.controller.currentPage());
        fixture.controller.showHomePage(0);
        assertEquals("the home press turns it to page 1", 0, fixture.controller.homePageIndex());
        assertEquals(2, fixture.controller.homePageCount());
    }

    @Test public void menuAddWidgetOpensPickerAndNewWidgetsLandOnTheVisiblePage() {
        Fixture fixture = new Fixture();
        fixture.controller.onMenuItemSelected(WidgetPaneMenuPolicy.Item.ADD_WIDGET);
        assertTrue(fixture.pane.picker().isOpen());
        fixture.pane.picker().closeImmediate();

        // Page 0 holds a widget of its own, so the page the new one lands on keeps its number.
        assertTrue(fixture.repository.putRecord(record(9, new WidgetCellRect(3, 4, 4, 5), 0)));
        assertEquals(1, fixture.repository.addPage());
        fixture.platform.directBind = true;
        LauncherWidgetHostController.AddResult result = fixture.widgets.beginAdd(
            WidgetTestFixtures.info(false), new WidgetCellRect(0, 0, 1, 1), 1,
            fixture.repository.revision(), new Bundle(), null);
        assertEquals(LauncherWidgetHostController.AddResult.READY, result);
        assertEquals(2, fixture.repository.records().size());
        assertEquals("the reservation's page is durable end-to-end",
            1, fixture.repository.records().get(1).page);
    }

    @Test public void cellRelaysProviderEditorFocusToTheHost() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        android.widget.LinearLayout root = new android.widget.LinearLayout(activity);
        WidgetCellView cell = new WidgetCellView(activity);
        root.addView(cell, new android.widget.LinearLayout.LayoutParams(200, 200));
        EditText outside = new EditText(activity);
        root.addView(outside, new android.widget.LinearLayout.LayoutParams(200, 200));
        activity.setContentView(root);
        assertEquals(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS,
            cell.getDescendantFocusability());
        AtomicReference<android.view.View> focused = new AtomicReference<>();
        cell.setEditorFocusListener(focused::set);
        EditText editor = new EditText(activity);
        cell.setContent(editor);
        assertTrue(editor.requestFocus());
        assertSame(editor, focused.get());
        // Focus moving to another subtree must end the relay even without an explicit clear.
        assertTrue(outside.requestFocus());
        assertNull(focused.get());
    }

    @Test public void aVerticalSwipeOnTheFreeAreaIsThePaletteGesture() {
        Fixture fixture = new Fixture();
        int x = fixture.pane.getWidth() / 2;
        int h = fixture.pane.getHeight();
        assertTrue(h > 0);
        fixture.swipe(x, h * 3 / 4, x, h / 4);
        assertEquals("an up swipe on the empty grid opens the palette",
            Collections.singletonList(true), fixture.paletteSwipes);
        fixture.swipe(x, h / 4, x, h * 3 / 4);
        assertEquals("a down swipe puts it away", Arrays.asList(true, false),
            fixture.paletteSwipes);
    }

    @Test public void aSidewaysSwipeOnTheFreeAreaStillPages() {
        Fixture fixture = new Fixture();
        fixture.controller.menuAddPage();
        fixture.controller.setCurrentPage(0);
        fixture.layout();
        int w = fixture.pane.getWidth();
        int y = fixture.pane.getHeight() / 2;
        fixture.swipe(w * 7 / 8, y, w / 16, y + 10);
        assertEquals(1, fixture.controller.currentPage());
        assertTrue(fixture.paletteSwipes.isEmpty());
    }

    @Test public void aVerticalSwipeOnAWidgetIsTheWidgets() {
        Fixture fixture = new Fixture();
        assertTrue(fixture.repository.putRecord(record(3, new WidgetCellRect(0, 0, 2, 2), 0)));
        fixture.controller.onWidgetRepositoryChanged(
            LauncherWidgetHostController.AddResult.IGNORED);
        fixture.controller.setCurrentPage(0);
        fixture.layout();
        View cell = fixture.pane.grid().cellForId(3);
        assertNotNull(cell);
        assertTrue(cell.getWidth() > 0 && cell.getHeight() > 0);
        int[] cellOnScreen = new int[2];
        cell.getLocationOnScreen(cellOnScreen);
        int[] paneOnScreen = new int[2];
        fixture.pane.getLocationOnScreen(paneOnScreen);
        int x = cellOnScreen[0] - paneOnScreen[0] + cell.getWidth() / 2;
        int bottom = cellOnScreen[1] - paneOnScreen[1] + cell.getHeight() - 2;
        fixture.swipe(x, bottom, x, bottom - 200);
        assertTrue("a swipe that starts on a widget is not the palette gesture",
            fixture.paletteSwipes.isEmpty());
    }

    private static LauncherWidgetRecord record(int id, WidgetCellRect cell, int page) {
        return new LauncherWidgetRecord(id, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.PROVIDER_MISSING, cell, page, new Bundle(), null);
    }

    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final LauncherWidgetRepository repository;
        final WidgetTestFixtures.Platform platform;
        final LauncherWidgetHostController widgets;
        final WidgetPaneView pane;
        final WidgetPaneController controller;
        final List<Boolean> paletteSwipes = new ArrayList<>();

        Fixture() {
            activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            repository = WidgetTestFixtures.repository();
            platform = new WidgetTestFixtures.Platform(activity);
            widgets = new LauncherWidgetHostController(activity, repository, platform);
            pane = new WidgetPaneView(activity);
            activity.setContentView(pane);
            controller = new WidgetPaneController(pane, widgets, new WidgetPaneController.Host() {
                @Override public boolean reducedMotion() { return true; }
                @Override public boolean isWidgetSurfaceShowing() { return true; }
                @Override public void captureWidgetSurfaceOrigin() { }
                @Override public void restoreWidgetSurfaceOrigin() { }
                @Override public void onHomePaletteSwipe(boolean up) { paletteSwipes.add(up); }
            });
            layout();
        }

        void layout() {
            shadowOf(Looper.getMainLooper()).idle();
        }

        /** A finger from (x0, y0) to (x1, y1) in the pane's coordinates, over ten moves. */
        void swipe(float x0, float y0, float x1, float y1) {
            int[] origin = new int[2];
            pane.getLocationOnScreen(origin);
            long down = 1000L;
            dispatch(down, down, MotionEvent.ACTION_DOWN, x0, y0, origin);
            for (int i = 1; i <= 10; i++) {
                float f = i / 10f;
                dispatch(down, down + i * 16L, MotionEvent.ACTION_MOVE,
                    x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, origin);
            }
            dispatch(down, down + 176L, MotionEvent.ACTION_UP, x1, y1, origin);
            layout();
        }

        private void dispatch(long down, long time, int action, float x, float y, int[] origin) {
            // Raw coordinates on screen, local ones in the pane, as the window would send them.
            MotionEvent event = MotionEvent.obtain(down, time, action,
                x + origin[0], y + origin[1], 0);
            event.offsetLocation(-origin[0], -origin[1]);
            pane.dispatchTouchEvent(event);
            event.recycle();
        }
    }
}
