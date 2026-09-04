package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.PaneWallPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

/** The place switch: it hugs its labels, a tap picks the segment under it, the thumb is clamped. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallPlaceSwitchViewTest {

    private static WallPlaceSwitchView build(Activity activity) {
        WallPlaceSwitchView view = new WallPlaceSwitchView(activity);
        activity.setContentView(view);
        view.setPlaces(PaneWallPolicy.availablePages(false, true, true),
            Arrays.asList("Widgets", "Terminal", "Display"));
        view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(36, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), 36);
        return view;
    }

    @Test public void itHugsItsLabelsAndGrowsForADot() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WallPlaceSwitchView view = build(activity);
        int width = view.getMeasuredWidth();
        assertTrue("three padded segments take real width", width >= 3 * 24);

        view.setDisplayRunning(true);
        view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(36, View.MeasureSpec.EXACTLY));
        assertTrue("the running dot needs room beside Display", view.getMeasuredWidth() > width);
    }

    @Test public void aTapPicksTheSegmentUnderIt() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WallPlaceSwitchView view = build(activity);
        PaneWallPage[] picked = {null};
        view.setListener(page -> picked[0] = page);
        float x = view.getWidth() - 4f; // inside the last segment

        view.dispatchTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, x, 18f, 0));
        view.dispatchTouchEvent(MotionEvent.obtain(0L, 50L, MotionEvent.ACTION_UP, x, 18f, 0));

        assertEquals(PaneWallPage.DISPLAY, picked[0]);
    }

    @Test public void aTapThatLeavesItsSegmentPicksNothing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WallPlaceSwitchView view = build(activity);
        PaneWallPage[] picked = {null};
        view.setListener(page -> picked[0] = page);

        view.dispatchTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 4f, 18f, 0));
        view.dispatchTouchEvent(MotionEvent.obtain(0L, 50L, MotionEvent.ACTION_UP,
            view.getWidth() - 4f, 18f, 0));

        assertNull(picked[0]);
    }

    @Test public void theThumbIsClampedToTheSegments() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WallPlaceSwitchView view = build(activity);
        view.setThumbPosition(-1f);
        assertEquals(0f, view.thumbPosition(), 0.001f);
        view.setThumbPosition(7f);
        assertEquals(2f, view.thumbPosition(), 0.001f);
        view.setThumbPosition(1.5f);
        assertEquals(1.5f, view.thumbPosition(), 0.001f);
    }
}
