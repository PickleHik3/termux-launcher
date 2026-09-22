package com.termux.app.statusbar;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusBarDragHintTest {

    private static void dispatch(View view, int action, float x, float y, long time) {
        MotionEvent event = MotionEvent.obtain(0, time, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }

    private static StatusBarSwipeLayout layout(Activity activity) {
        StatusBarSwipeLayout layout = new StatusBarSwipeLayout(activity, null);
        layout.setCollapsed(true);
        activity.setContentView(layout);
        layout.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(96, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, 1080, 96);
        return layout;
    }

    private static void tap(StatusBarSwipeLayout layout, float x, float y) {
        dispatch(layout, MotionEvent.ACTION_DOWN, x, y, 0);
        dispatch(layout, MotionEvent.ACTION_UP, x, y, 40);
    }

    @Test public void tappingTheBarChromePlaysTheDragHint() {
        StatusBarSwipeLayout layout = layout(Robolectric.buildActivity(Activity.class).setup().get());

        tap(layout, 540f, 40f);

        assertEquals(1, layout.pullHintCount());
    }

    @Test public void tappingAnInteractiveChildStaysSilent() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        StatusBarSwipeLayout layout = layout(activity);
        Button chip = new Button(activity);
        chip.setOnClickListener(v -> { });
        layout.addView(chip, new FrameLayout.LayoutParams(200, 96));
        layout.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(96, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, 1080, 96);

        tap(layout, 100f, 40f);

        assertEquals(0, layout.pullHintCount());
    }
}
