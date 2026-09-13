package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.termux.app.chrome.CornerZones;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Which touches the Display page reports as taps. The text-focus policy reads a tap as "the user
 * pointed at something", so a drag, a two-finger gesture and the page's own corners must not
 * count — and nothing here may take a touch away from X. The edges between the corners are the
 * display's: a maximised window's own title bar and edge controls have to answer.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11PaneFrameTapTest {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 800;

    /**
     * The frame on its own, without the display view its layout carries: that view binds the
     * native server on construction, so it cannot exist on the JVM.
     */
    private static X11PaneFrame page(Activity activity) {
        X11PaneFrame page = new X11PaneFrame(activity);
        page.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, WIDTH, HEIGHT);
        page.applyRunning(true);
        return page;
    }

    private static MotionEvent event(int action, float x, float y) {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0);
    }

    private static int tapsFor(Activity activity, MotionEvent... events) {
        X11PaneFrame page = page(activity);
        int[] taps = {0};
        page.setTapListener(() -> taps[0]++);
        for (MotionEvent event : events) {
            page.dispatchTouchEvent(event);
            event.recycle();
        }
        return taps[0];
    }

    @Test
    public void aFingerDownAndUpInPlaceIsATap() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertEquals(1, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, 300f, 400f),
            event(MotionEvent.ACTION_UP, 300f, 400f)));
    }

    @Test
    public void aFingerThatDriftsWithinTheSlopIsStillATap() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        float slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        assertEquals(1, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, 300f, 400f),
            event(MotionEvent.ACTION_MOVE, 300f + slop / 2f, 400f),
            event(MotionEvent.ACTION_UP, 300f + slop / 2f, 400f)));
    }

    @Test
    public void aDragIsNotATap() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        float slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        assertEquals(0, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, 300f, 400f),
            event(MotionEvent.ACTION_MOVE, 300f, 400f + slop * 4f),
            event(MotionEvent.ACTION_UP, 300f, 400f + slop * 4f)));
    }

    @Test
    public void aSecondFingerMakesItAGesture() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertEquals(0, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, 300f, 400f),
            event(MotionEvent.ACTION_POINTER_DOWN, 320f, 420f),
            event(MotionEvent.ACTION_UP, 300f, 400f)));
    }

    @Test
    public void aCancelledTouchIsNotATap() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertEquals(0, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, 300f, 400f),
            event(MotionEvent.ACTION_CANCEL, 300f, 400f),
            event(MotionEvent.ACTION_UP, 300f, 400f)));
    }

    @Test
    public void theCornersBelongToThePage_notToTheDisplay() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertEquals(0, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, 2f, 2f),
            event(MotionEvent.ACTION_UP, 2f, 2f)));
    }

    /** The old 12dp band swallowed these; a maximised X window needs every one of them. */
    @Test
    public void theEdgesBetweenTheCornersReachTheDisplay() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertEquals("the middle of the leading edge", 1, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, 1f, HEIGHT / 2f),
            event(MotionEvent.ACTION_UP, 1f, HEIGHT / 2f)));
        assertEquals("the middle of the top edge", 1, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, WIDTH / 2f, 1f),
            event(MotionEvent.ACTION_UP, WIDTH / 2f, 1f)));
        assertEquals("the middle of the bottom edge", 1, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, WIDTH / 2f, HEIGHT - 1f),
            event(MotionEvent.ACTION_UP, WIDTH / 2f, HEIGHT - 1f)));
    }

    /** The rule itself, without a frame around it. */
    @Test
    public void aTouchIsTheDisplaysOnlyWhenNothingElseWantsIt() {
        assertTrue(X11PaneFrame.touchIsTheDisplays(true, false, CornerZones.NONE));
        assertFalse("no display running",
            X11PaneFrame.touchIsTheDisplays(false, false, CornerZones.NONE));
        assertFalse("the page's own tab or rail",
            X11PaneFrame.touchIsTheDisplays(true, true, CornerZones.NONE));
        assertFalse("a corner",
            X11PaneFrame.touchIsTheDisplays(true, false, CornerZones.BOTTOM_LEFT));
    }

    @Test
    public void aPageWithNoDisplayRunningReportsNothing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame page = new X11PaneFrame(activity);
        page.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, WIDTH, HEIGHT);
        int[] taps = {0};
        page.setTapListener(() -> taps[0]++);
        MotionEvent down = event(MotionEvent.ACTION_DOWN, 300f, 400f);
        MotionEvent up = event(MotionEvent.ACTION_UP, 300f, 400f);
        page.dispatchTouchEvent(down);
        page.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        assertEquals(0, taps[0]);
    }
}
