package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusBarGrabHandleViewTest {

    @Test
    public void tap_requestsToggleOnlyOnRelease() {
        StatusBarGrabHandleView view = new StatusBarGrabHandleView(
            ApplicationProvider.getApplicationContext(), null);
        List<Boolean> requests = new ArrayList<>();
        view.setListener(collapsed -> {
            requests.add(collapsed);
            view.setCollapsed(collapsed);
        });
        view.setCollapsed(false);

        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 100f));
        assertEquals(0, requests.size());

        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 10f, 100f));
        assertEquals(1, requests.size());
        assertEquals(Boolean.TRUE, requests.get(0));
    }

    @Test
    public void drag_reportsContinuousMovementThenSettlesByDirection() {
        StatusBarGrabHandleView view = new StatusBarGrabHandleView(
            ApplicationProvider.getApplicationContext(), null);
        List<Float> progress = new ArrayList<>();
        List<Boolean> finishes = new ArrayList<>();
        int[] starts = new int[1];
        view.setListener(new StatusBarGrabHandleView.Listener() {
            @Override
            public void onCollapsedStateRequested(boolean collapsed) {}

            @Override
            public void onResizeDragStarted() {
                starts[0]++;
            }

            @Override
            public void onResizeDragProgress(float deltaY) {
                progress.add(deltaY);
            }

            @Override
            public void onResizeDragFinished(boolean collapsed) {
                finishes.add(collapsed);
            }
        });
        view.setCollapsed(false);

        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 100f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 10f, 96f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 10f, 80f));

        assertEquals(1, starts[0]);
        assertEquals(2, progress.size());
        assertEquals(-20f, progress.get(1), .01f);

        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 10f, 78f));
        assertEquals(1, finishes.size());
        assertEquals(Boolean.TRUE, finishes.get(0));
    }

    @Test
    public void extendedTarget_favorsAreaBelowTheStatusBarEdge() {
        StatusBarGrabHandleView view = new StatusBarGrabHandleView(
            ApplicationProvider.getApplicationContext(), null);
        float density = view.getResources().getDisplayMetrics().density;
        int width = Math.round(68 * density);
        int height = Math.round(6 * density);
        view.layout(0, 0, width, height);
        float centerX = width / 2f;
        float edgeY = height - .75f * density;

        assertTrue(view.containsExtendedTouchPoint(centerX, edgeY + 24f * density));
        assertTrue(view.containsExtendedTouchPoint(centerX, edgeY - 4f * density));
        assertFalse(view.containsExtendedTouchPoint(centerX, edgeY - 6f * density));
        assertFalse(view.containsExtendedTouchPoint(centerX, edgeY + 31f * density));
    }

    private static MotionEvent event(int action, float x, float y) {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0);
    }
}
