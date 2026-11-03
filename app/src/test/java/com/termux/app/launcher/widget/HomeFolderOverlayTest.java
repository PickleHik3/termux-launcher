package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Home folder popup's layer over the Home pane: it opens on top of the pane, a press on the
 * dim closes it and stops there, a press on the panel does not, and it keeps the gesture it was
 * pressed in from the pane's own swipes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HomeFolderOverlayTest {

    /** The Home pane: records whether a child asked it to keep its hands off a gesture. */
    private static final class Pane extends FrameLayout {
        boolean disallowed;
        Pane(Activity activity) { super(activity); }
        @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
            disallowed |= disallow;
            super.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private Pane pane;
    private View anchor;
    private View panel;
    private final AtomicInteger dismissed = new AtomicInteger();
    private HomeFolderOverlay overlay;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        pane = new Pane(activity);
        anchor = new View(activity);
        FrameLayout.LayoutParams anchorParams = new FrameLayout.LayoutParams(200, 200);
        anchorParams.leftMargin = 440;
        anchorParams.topMargin = 1400;
        pane.addView(anchor, anchorParams);
        pane.addView(new View(activity), new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activity.setContentView(pane, new ViewGroup.LayoutParams(1080, 1920));
        panel = new FrameLayout(activity);
        overlay = new HomeFolderOverlay(activity, panel, 400, 300, anchor, 8,
            dismissed::incrementAndGet);
        overlay.showIn(pane);
        layOut();
    }

    private void layOut() {
        pane.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        pane.layout(0, 0, 1080, 1920);
    }

    private static MotionEvent down(float x, float y) {
        long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
    }

    @Test public void opensOnTopOfThePaneWithThePanelAgainstItsAnchor() {
        assertTrue(overlay.isOpen());
        assertSame(overlay, pane.getChildAt(pane.getChildCount() - 1));
        assertEquals(400, panel.getWidth());
        assertEquals(300, panel.getHeight());
        // Above the anchor, a gap off it, inside the pane.
        assertTrue(panel.getBottom() <= anchor.getTop());
        assertTrue(panel.getLeft() >= 0 && panel.getRight() <= 1080);
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        assertEquals(1f, panel.getAlpha(), 0.01f);
        assertEquals(0, dismissed.get());
    }

    @Test public void aPressOnTheDimClosesItAndGoesNoFurther() {
        MotionEvent press = down(10f, 10f);
        assertFalse(overlay.panelContains(10f, 10f));
        assertTrue("the press stops at the dim", overlay.dispatchTouchEvent(press));
        press.recycle();
        assertFalse(overlay.isOpen());
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
        assertNull(overlay.getParent());
        assertEquals(1, dismissed.get());
    }

    @Test public void aPressOnThePanelLeavesItOpen() {
        int[] at = new int[2];
        panel.getLocationOnScreen(at);
        float x = at[0] + 200f;
        float y = at[1] + 150f;
        assertTrue(overlay.panelContains(x, y));
        MotionEvent press = down(x, y);
        overlay.dispatchTouchEvent(press);
        press.recycle();
        assertTrue(overlay.isOpen());
        assertEquals(0, dismissed.get());
    }

    @Test public void aPressOnItKeepsThePanesSwipesOffTheGesture() {
        pane.disallowed = false;
        MotionEvent press = down(10f, 10f);
        overlay.dispatchTouchEvent(press);
        press.recycle();
        assertTrue(pane.disallowed);
    }

    @Test public void dismissImmediateTakesItAwayOnceAndNow() {
        overlay.dismissImmediate();
        assertNull(overlay.getParent());
        assertFalse(overlay.isOpen());
        overlay.dismissImmediate();
        overlay.dismiss();
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        assertEquals(1, dismissed.get());
    }
}
