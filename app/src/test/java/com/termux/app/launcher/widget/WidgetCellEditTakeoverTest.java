package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WidgetCellEditTakeoverTest {

    private static void dispatch(View view, int action, float x, float y, long time) {
        MotionEvent event = MotionEvent.obtain(0, time, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 300, 300);
    }

    @Test public void longPressCancelsProviderStreamAndForwardsEditDrag() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetCellView cell = new WidgetCellView(activity);
        View provider = new View(activity);
        provider.setClickable(true);
        List<Integer> providerActions = new ArrayList<>();
        provider.setOnTouchListener((view, event) -> {
            providerActions.add(event.getActionMasked());
            return true;
        });
        FrameLayout host = new FrameLayout(activity);
        host.addView(provider, new FrameLayout.LayoutParams(-1, -1));
        cell.setContent(host);
        activity.setContentView(cell);
        layout(cell);

        AtomicInteger longPresses = new AtomicInteger();
        List<float[]> dragMoves = new ArrayList<>();
        AtomicBoolean dragEnded = new AtomicBoolean();
        AtomicBoolean dragCanceled = new AtomicBoolean(true);
        cell.setLongPressListener(new WidgetCellView.LongPressListener() {
            @Override public void onWidgetLongPress(float rawX, float rawY) {
                longPresses.incrementAndGet();
            }
            @Override public void onEditDragMove(float rawX, float rawY) {
                dragMoves.add(new float[] {rawX, rawY});
            }
            @Override public void onEditDragEnd(boolean canceled) {
                dragEnded.set(true);
                dragCanceled.set(canceled);
            }
        });

        dispatch(cell, MotionEvent.ACTION_DOWN, 50, 50, 0);
        assertEquals(List.of(MotionEvent.ACTION_DOWN), providerActions);

        ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout() + 50,
            java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(1, longPresses.get());
        // The provider's stream must end in CANCEL the moment the launcher takes over.
        assertEquals(List.of(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), providerActions);

        dispatch(cell, MotionEvent.ACTION_MOVE, 80, 90, 600);
        dispatch(cell, MotionEvent.ACTION_UP, 80, 90, 650);
        // Post-takeover events belong to the launcher, never the provider.
        assertEquals(List.of(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), providerActions);
        assertEquals(1, dragMoves.size());
        assertTrue(dragEnded.get());
        assertFalse(dragCanceled.get());
    }

    @Test public void movementBeyondSlopBeforeTimeoutKeepsProviderOwnership() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetCellView cell = new WidgetCellView(activity);
        View provider = new View(activity);
        provider.setClickable(true);
        List<Integer> providerActions = new ArrayList<>();
        provider.setOnTouchListener((view, event) -> {
            providerActions.add(event.getActionMasked());
            return true;
        });
        cell.setContent(provider);
        activity.setContentView(cell);
        layout(cell);
        AtomicInteger longPresses = new AtomicInteger();
        cell.setLongPressListener(new WidgetCellView.LongPressListener() {
            @Override public void onWidgetLongPress(float rawX, float rawY) {
                longPresses.incrementAndGet();
            }
        });

        dispatch(cell, MotionEvent.ACTION_DOWN, 50, 50, 0);
        dispatch(cell, MotionEvent.ACTION_MOVE, 200, 200, 30);
        ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout() + 50,
            java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(0, longPresses.get());
        dispatch(cell, MotionEvent.ACTION_UP, 200, 200, 60);
        assertEquals(List.of(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP), providerActions);
    }
}
