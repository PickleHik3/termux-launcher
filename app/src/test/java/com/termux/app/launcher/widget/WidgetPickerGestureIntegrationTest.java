package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetPickerGestureIntegrationTest {
    @Test public void listOwnsDragAndScrimRequiresTapWithoutClosingFull() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetPickerSheetView picker = new WidgetPickerSheetView(activity, item -> { });
        picker.setReducedMotion(true); activity.setContentView(picker); layout(picker); picker.open();
        assertTrue(picker.list().isNestedScrollingEnabled());
        drag(picker.list(), 40, 180, 40, 80); assertTrue(picker.isOpen());
        View scrim = ReflectionHelpers.getField(picker, "scrim");
        drag(scrim, 10, 10, 80, 80); assertTrue(picker.isOpen());
        tap(scrim, 10, 10); assertFalse(picker.isOpen());
    }
    private static void drag(View view, float x1, float y1, float x2, float y2) {
        send(view, MotionEvent.ACTION_DOWN, x1, y1); send(view, MotionEvent.ACTION_MOVE, x2, y2);
        send(view, MotionEvent.ACTION_UP, x2, y2);
    }
    private static void tap(View view, float x, float y) {
        send(view, MotionEvent.ACTION_DOWN, x, y); send(view, MotionEvent.ACTION_UP, x, y);
    }
    private static void send(View view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, action, action, x, y, 0);
        view.dispatchTouchEvent(event); event.recycle();
    }
    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)); view.layout(0, 0, 600, 800);
    }
}
