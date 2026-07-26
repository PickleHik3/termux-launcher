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
        view.layout(0, 0, 36, 22);

        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 18f, 11f));
        assertEquals(0, requests.size());

        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 18f, 11f));
        assertEquals(1, requests.size());
        assertEquals(Boolean.TRUE, requests.get(0));
    }

    @Test
    public void movement_neverRequestsInteractiveResizeAndTogglesOnlyOnRelease() {
        StatusBarGrabHandleView view = new StatusBarGrabHandleView(
            ApplicationProvider.getApplicationContext(), null);
        List<Boolean> requests = new ArrayList<>();
        view.setListener(requests::add);
        view.setCollapsed(false);
        view.layout(0, 0, 36, 22);

        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 18f, 11f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 18f, 5f));

        assertEquals(0, requests.size());

        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 18f, 5f));
        assertEquals(1, requests.size());
        assertEquals(Boolean.TRUE, requests.get(0));
    }

    @Test
    public void cancelledTap_doesNotRequestStateChange() {
        StatusBarGrabHandleView view = new StatusBarGrabHandleView(
            ApplicationProvider.getApplicationContext(), null);
        List<Boolean> requests = new ArrayList<>();
        view.setListener(requests::add);

        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_CANCEL, 10f, 10f));

        assertEquals(0, requests.size());
    }

    @Test
    public void upwardMovementOutsideGlyph_doesNotToggle() {
        StatusBarGrabHandleView view = new StatusBarGrabHandleView(
            ApplicationProvider.getApplicationContext(), null);
        List<Boolean> requests = new ArrayList<>();
        view.setListener(requests::add);
        view.layout(0, 0, 36, 22);

        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 18f, 11f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 18f, -1f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 18f, -1f));

        assertEquals(0, requests.size());
    }

    private static MotionEvent event(int action, float x, float y) {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0);
    }
}
