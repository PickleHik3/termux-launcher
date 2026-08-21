package com.termux.app.statusbar;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusBarPullDownIntegrationTest {

    private static void dispatch(View view, int action, float x, float y, long time) {
        MotionEvent event = MotionEvent.obtain(0, time, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }

    /**
     * The real begin path re-enters setStatusState (engagement flips the pane to FULL) from
     * inside onFullDragBegin. That structural reset must not kill the live drag: every
     * subsequent move still lands and the release still delivers the end callback.
     */
    @Test public void pullDownSurvivesReentrantStateFlipAndDeliversDragStream() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        StatusBarSwipeLayout layout = new StatusBarSwipeLayout(activity, null);
        layout.setStatusState(TopStatusBarState.COMPACT, TopStatusBarState.COMPACT);
        activity.setContentView(layout);
        layout.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(96, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, 1080, 96);

        AtomicInteger begins = new AtomicInteger();
        AtomicInteger ends = new AtomicInteger();
        List<Float> drags = new ArrayList<>();
        layout.setListener(new StatusBarSwipeLayout.Listener() {
            @Override public void onCollapsedStateRequested(boolean collapsed) { }
            @Override public boolean onFullDragBegin(TopStatusBarState priorState) {
                begins.incrementAndGet();
                assertEquals(TopStatusBarState.COMPACT, priorState);
                // Mimic FullStatusBarController engagement: the pane flips to FULL mid-callback.
                layout.setStatusState(TopStatusBarState.FULL, priorState);
                return true;
            }
            @Override public void onFullDrag(float dragPx) { drags.add(dragPx); }
            @Override public void onFullDragEnd(float velocityPxPerSec) { ends.incrementAndGet(); }
        });

        int slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        dispatch(layout, MotionEvent.ACTION_DOWN, 540, 40, 0);
        dispatch(layout, MotionEvent.ACTION_MOVE, 540, 40 + slop * 2, 20);
        assertEquals(1, begins.get());
        dispatch(layout, MotionEvent.ACTION_MOVE, 540, 40 + slop * 2 + 120, 40);
        dispatch(layout, MotionEvent.ACTION_MOVE, 540, 40 + slop * 2 + 260, 60);
        assertTrue("moves after the reentrant state flip must still drive the drag",
            drags.size() >= 2);
        assertTrue(drags.get(drags.size() - 1) > drags.get(0));
        dispatch(layout, MotionEvent.ACTION_UP, 540, 40 + slop * 2 + 260, 80);
        assertEquals(1, ends.get());
        assertEquals("one gesture, one begin", 1, begins.get());
    }

    @Test public void pullDownRejectedByListenerLeavesGestureDead() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        StatusBarSwipeLayout layout = new StatusBarSwipeLayout(activity, null);
        layout.setStatusState(TopStatusBarState.EXPANDED, TopStatusBarState.EXPANDED);
        activity.setContentView(layout);
        layout.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(96, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, 1080, 96);
        AtomicInteger drags = new AtomicInteger();
        layout.setListener(new StatusBarSwipeLayout.Listener() {
            @Override public void onCollapsedStateRequested(boolean collapsed) { }
            @Override public boolean onFullDragBegin(TopStatusBarState priorState) { return false; }
            @Override public void onFullDrag(float dragPx) { drags.incrementAndGet(); }
        });
        int slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        dispatch(layout, MotionEvent.ACTION_DOWN, 540, 40, 0);
        dispatch(layout, MotionEvent.ACTION_MOVE, 540, 40 + slop * 2, 20);
        dispatch(layout, MotionEvent.ACTION_MOVE, 540, 40 + slop * 2 + 120, 40);
        dispatch(layout, MotionEvent.ACTION_UP, 540, 40 + slop * 2 + 120, 60);
        assertEquals(0, drags.get());
    }
}
