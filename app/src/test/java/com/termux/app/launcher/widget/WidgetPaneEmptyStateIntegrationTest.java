package com.termux.app.launcher.widget;

import android.app.Application;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.statusbar.FullStatusBarController;
import com.termux.app.statusbar.TopStatusBarState;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class,
    qualifiers = "w411dp-h919dp-420dpi")
public class WidgetPaneEmptyStateIntegrationTest {
    @Test public void emptyFullHierarchyGridAndPickerBoundsDoNotIntersectStatusRowBounds() {
        assertFullBodyRectangles(new Fixture(false));
    }

    @Test public void placedWidgetFullHierarchyGridAndPickerBoundsDoNotIntersectStatusRowBounds() {
        assertFullBodyRectangles(new Fixture(true));
    }

    @Test public void fullPaneOwnersHardClipChromeAndBodyWhilePickerIsBodyModal() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        ViewGroup host = activity.findViewById(R.id.terminal_window_bar_host);
        ViewGroup top = activity.findViewById(R.id.terminal_top_widget_area);
        WidgetPaneView body = activity.findViewById(R.id.widget_pane);
        ViewGroup status = activity.findViewById(R.id.terminal_status_row);
        assertTrue(top.getClipChildren());
        assertTrue(body.getClipChildren());
        assertTrue(status.getClipChildren());
        assertSame(body, body.picker().getParent());
        assertEquals(body.getChildCount() - 1, body.indexOfChild(body.picker()));
        assertSame(host, top.getParent());
        assertSame(host, body.getParent());
        assertSame(host, status.getParent());
    }

    @Test public void fullBandsFillPaneAndRemainDisjointThroughoutSpring() {
        Fixture fixture = new Fixture(false);
        for (float progress : new float[] {0f, .5f, 1f}) {
            fixture.setFullProgress(progress);
            ViewGroup root = fixture.activity.findViewById(R.id.activity_termux_root_view);
            Rect host = boundsIn(root,
                fixture.activity.findViewById(R.id.terminal_window_bar_host));
            Rect top = boundsIn(root,
                fixture.activity.findViewById(R.id.terminal_top_widget_area));
            Rect body = boundsIn(root, fixture.activity.findViewById(R.id.widget_pane));
            Rect status = boundsIn(root,
                fixture.activity.findViewById(R.id.terminal_status_row));

            assertEquals("progress=" + progress, top.bottom, body.top);
            assertEquals("progress=" + progress, status.top, body.bottom);
            assertEquals("status row must ride the pane's moving bottom; progress=" + progress,
                Math.round(2f * fixture.activity.getResources().getDisplayMetrics().density),
                host.bottom - status.bottom);
            assertFalse("top/body overlap at progress=" + progress, Rect.intersects(top, body));
            assertFalse("body/status overlap at progress=" + progress,
                Rect.intersects(body, status));
            assertFalse("top/status overlap at progress=" + progress,
                Rect.intersects(top, status));
        }
    }

    private static void assertFullBodyRectangles(Fixture fixture) {
        ViewGroup root = fixture.activity.findViewById(R.id.activity_termux_root_view);
        View host = fixture.activity.findViewById(R.id.terminal_window_bar_host);
        View top = fixture.activity.findViewById(R.id.terminal_top_widget_area);
        View status = fixture.activity.findViewById(R.id.terminal_status_row);
        WidgetPaneView pane = fixture.activity.findViewById(R.id.widget_pane);
        View grid = fixture.activity.findViewById(R.id.widget_grid);

        pane.picker().setReducedMotion(true);
        pane.picker().open();
        layout(root);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Rect hostRect = boundsIn(root, host);
        Rect topRect = boundsIn(root, top);
        Rect statusRect = boundsIn(root, status);
        Rect bodyRect = boundsIn(root, pane);
        Rect gridRect = boundsIn(root, grid);
        Rect pickerRect = boundsIn(root, pane.picker());

        assertEquals(1080, root.getWidth());
        assertEquals(2412, root.getHeight());
        assertEquals(topRect.bottom, bodyRect.top);
        assertEquals(statusRect.top, bodyRect.bottom);
        assertEquals(Math.round(2f * fixture.activity.getResources().getDisplayMetrics().density),
            hostRect.bottom - statusRect.bottom);
        assertEquals(bodyRect, pickerRect);
        assertTrue(hostRect.contains(statusRect));
        assertTrue(bodyRect.contains(gridRect));
        assertFalse("widget grid " + gridRect + " intersects status row " + statusRect,
            Rect.intersects(gridRect, statusRect));
        assertFalse("widget picker " + pickerRect + " intersects status row " + statusRect,
            Rect.intersects(pickerRect, statusRect));
    }

    private static Rect boundsIn(ViewGroup ancestor, View descendant) {
        Rect bounds = new Rect(0, 0, descendant.getWidth(), descendant.getHeight());
        ancestor.offsetDescendantRectToMyCoords(descendant, bounds);
        return bounds;
    }

    private static final class Fixture {
        final TermuxActivity activity;
        final int fullHeight;

        Fixture(boolean populated) {
            activity = Robolectric.buildActivity(TermuxActivity.class).get();
            activity.setContentView(R.layout.activity_termux);
            LauncherWidgetRepository repository = WidgetTestFixtures.repository();
            WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
            if (populated) {
                repository.putRecord(new LauncherWidgetRecord(20, WidgetTestFixtures.PROVIDER, 0,
                    LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(0, 0, 2, 2),
                    new Bundle(), null));
                platform.info.put(20, WidgetTestFixtures.info(false));
            }
            LauncherWidgetHostController widgets = new LauncherWidgetHostController(activity,
                repository, platform);
            ReflectionHelpers.setField(activity, "mWidgetHostController", widgets);
            View host = activity.findViewById(R.id.terminal_window_bar_host);
            host.setVisibility(View.VISIBLE);
            layout(activity.findViewById(R.id.activity_termux_root_view));
            ReflectionHelpers.callInstanceMethod(activity, "createFullStatusBarController");
            ReflectionHelpers.callInstanceMethod(activity, "createWidgetPaneController");
            FullStatusBarController full = ReflectionHelpers.getField(
                activity, "mFullStatusBarController");
            full.restoreFullImmediate(TopStatusBarState.EXPANDED);
            layout(activity.findViewById(R.id.activity_termux_root_view));
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            fullHeight = host.getHeight();
        }

        void setFullProgress(float progress) {
            View host = activity.findViewById(R.id.terminal_window_bar_host);
            View top = activity.findViewById(R.id.terminal_top_widget_area);
            int expandedHeight = Math.round(96f
                * activity.getResources().getDisplayMetrics().density);
            int height = Math.round(expandedHeight + (fullHeight - expandedHeight) * progress);
            ReflectionHelpers.callInstanceMethod(activity, "applyTopStatusBarInteractiveHeight",
                ReflectionHelpers.ClassParameter.from(View.class, host),
                ReflectionHelpers.ClassParameter.from(View.class, top),
                ReflectionHelpers.ClassParameter.from(int.class, height),
                ReflectionHelpers.ClassParameter.from(boolean.class, false));
            layout(activity.findViewById(R.id.activity_termux_root_view));
        }
    }

    private static void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2412, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2412);
    }
}
