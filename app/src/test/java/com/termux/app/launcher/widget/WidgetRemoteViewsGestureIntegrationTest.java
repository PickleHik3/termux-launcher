package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetHostView;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.view.ViewCompat;

import com.termux.app.statusbar.StatusBarSwipeLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetRemoteViewsGestureIntegrationTest {
    @Test public void realStatusHierarchyKeepsProviderStreamAcrossCellBoundaryAndConsumesNoNestedDistance() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        StatusBarSwipeLayout status = new StatusBarSwipeLayout(activity, null);
        AtomicInteger statusClaims = new AtomicInteger();
        status.setListener(collapsed -> statusClaims.incrementAndGet());
        WidgetCellView cell = new WidgetCellView(activity);
        AppWidgetHostView host = new AppWidgetHostView(activity);
        View provider = new View(activity); provider.setClickable(true);
        ArrayList<Integer> actions = new ArrayList<>();
        provider.setOnTouchListener((view, event) -> { actions.add(event.getActionMasked()); return true; });
        host.addView(provider, new FrameLayout.LayoutParams(-1, -1)); cell.setContent(host);
        status.addView(cell, new FrameLayout.LayoutParams(-1, -1)); activity.setContentView(status);
        layout(status);
        dispatch(status, MotionEvent.ACTION_DOWN, 50, 50, 0); provider.setClickable(false);
        dispatch(status, MotionEvent.ACTION_MOVE, 350, 340, 10);
        dispatch(status, MotionEvent.ACTION_UP, 350, 340, 20);
        assertEquals(Arrays.asList(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP), actions);
        assertEquals(0, statusClaims.get());
        int[] consumed = {0, 0};
        assertTrue(status.onStartNestedScroll(cell, provider, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH));
        status.onNestedPreScroll(provider, 0, 40, consumed, ViewCompat.TYPE_TOUCH);
        assertArrayEquals(new int[] {0, 0}, consumed);
    }
    private static void dispatch(View view, int action, float x, float y, long time) {
        MotionEvent event = MotionEvent.obtain(0, time, action, x, y, 0);
        view.dispatchTouchEvent(event); event.recycle();
    }
    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY)); view.layout(0, 0, 300, 300);
    }
}
