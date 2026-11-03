package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.notice.AppNotice;
import com.termux.app.notice.AppNoticeHostView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.Collections;

import static org.robolectric.Shadows.shadowOf;

/**
 * A Home icon taken off by a drop on Remove, or by the App menu's Remove from Home, comes back to
 * its own cell on its own page when the notice that says so is tapped.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class HomeRemoveUndoTest {
    private static final AppRef CHROME = new AppRef("com.android.chrome",
        "com.google.android.apps.chrome.Main");
    private static final AppRef CLOCK = new AppRef("com.clock", "com.clock.Main");

    private static WidgetCellRect cell(int column, int row) {
        return new WidgetCellRect(column, row, column + 1, row + 1);
    }

    @Test public void theMenusRemoveIsUndoneByTheNotice() {
        Fixture fixture = new Fixture();
        int chrome = fixture.chromeId;

        ReflectionHelpers.callInstanceMethod(fixture.controller, "removeFromHomeByMenu",
            ReflectionHelpers.ClassParameter.from(int.class, chrome));
        idle();
        assertNull("the menu took it off Home", fixture.repository.get(chrome));

        fixture.tapTheNotice();

        fixture.assertChromeBack();
    }

    @Test public void aDropOnRemoveIsUndoneByTheNotice() {
        Fixture fixture = new Fixture();
        int chrome = fixture.chromeId;
        View cell = fixture.pane.grid().cellForId(chrome);
        assertNotNull(cell);
        int[] at = new int[2];
        cell.getLocationOnScreen(at);
        float x = at[0] + cell.getWidth() / 2f, y = at[1] + cell.getHeight() / 2f;

        ReflectionHelpers.callInstanceMethod(fixture.controller, "liftIcon",
            ReflectionHelpers.ClassParameter.from(int.class, chrome),
            ReflectionHelpers.ClassParameter.from(float.class, x),
            ReflectionHelpers.ClassParameter.from(float.class, y),
            ReflectionHelpers.ClassParameter.from(float.class, x),
            ReflectionHelpers.ClassParameter.from(float.class, y));
        Object edit = ReflectionHelpers.getField(fixture.controller, "edit");
        assertNotNull("the icon lifted", edit);
        // The finger over Remove, as the drag's hit test leaves it.
        ReflectionHelpers.setField(edit, "dropTarget", 0);
        ReflectionHelpers.setField(fixture.controller, "dropKinds",
            Collections.singletonList(HomeDropTargets.Target.REMOVE));
        ReflectionHelpers.callInstanceMethod(fixture.controller, "endMoveDrag",
            ReflectionHelpers.ClassParameter.from(boolean.class, false));
        idle();
        assertNull("the drop took it off Home", fixture.repository.get(chrome));

        fixture.tapTheNotice();

        fixture.assertChromeBack();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Home of three pages, the last one shown: Chrome at (0, 0) beside a Clock icon. */
    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        final LauncherWidgetHostController widgets;
        final WidgetPaneView pane;
        final WidgetPaneController controller;
        final int chromeId;

        Fixture() {
            activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            assertNotNull(repository.addAppAt(CLOCK, 0, cell(1, 0), label("Clock")));
            repository.setPages(3, Collections.emptyList());
            LauncherWidgetRecord chrome = repository.addAppAt(CHROME, 2, cell(0, 0),
                label("Chrome"));
            assertNotNull(chrome);
            assertNotNull(repository.addAppAt(CLOCK, 1, cell(1, 1), label("Clock")));
            assertNotNull(repository.addAppAt(CLOCK, 2, cell(2, 1), label("Clock")));
            chromeId = chrome.appWidgetId;
            widgets = new LauncherWidgetHostController(activity, repository,
                new WidgetTestFixtures.Platform(activity));
            pane = new WidgetPaneView(activity);
            activity.setContentView(pane);
            controller = new WidgetPaneController(pane, widgets, new WidgetPaneController.Host() {
                @Override public boolean reducedMotion() { return true; }
                @Override public boolean isWidgetSurfaceShowing() { return true; }
                @Override public void captureWidgetSurfaceOrigin() { }
                @Override public void restoreWidgetSurfaceOrigin() { }
            });
            ReflectionHelpers.setField(controller, "currentPage", 2);
            controller.onWidgetRepositoryChanged(LauncherWidgetHostController.AddResult.IGNORED);
            layOut();
            assertEquals(3, repository.pageCount());
            assertEquals(2, repository.get(chromeId).page);
            assertNotNull(pane.grid().cellForId(chromeId));
        }

        void layOut() {
            View root = activity.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2200, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2200);
            idle();
        }

        /** A tap on the pill, as a finger makes one. */
        void tapTheNotice() {
            AppNoticeHostView notice = AppNotice.hostFor(activity);
            assertNotNull(notice);
            assertEquals("the notice is up", View.VISIBLE, notice.getVisibility());
            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0);
            MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, 10f, 10f, 0);
            notice.onTouchEvent(down);
            notice.onTouchEvent(up);
            down.recycle();
            up.recycle();
            idle();
            layOut();
        }

        void assertChromeBack() {
            LauncherWidgetRecord back = null;
            for (LauncherWidgetRecord record : repository.records()) {
                AppRef app = record.appRef();
                if (app != null && CHROME.packageName.equals(app.packageName)) back = record;
            }
            assertNotNull("Undo put Chrome back on Home", back);
            assertEquals(2, back.page);
            assertEquals(cell(0, 0), back.cell);
            assertEquals("the page it is on is the one shown", 2, controller.currentPage());
            View view = pane.grid().cellForId(back.appWidgetId);
            assertNotNull("its cell is on the page", view);
            assertEquals(View.VISIBLE, view.getVisibility());
            assertEquals(1f, view.getAlpha(), 0f);
        }

        private static Bundle label(String value) {
            Bundle options = new Bundle();
            options.putString(LauncherWidgetRecord.APP_LABEL, value);
            return options;
        }
    }
}
