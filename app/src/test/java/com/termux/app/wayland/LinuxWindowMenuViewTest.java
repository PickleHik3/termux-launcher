package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.termux.view.HoldTiming;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Move on the rim tab's menu is a handle: a hold lifts the window, a tap does nothing (#386). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LinuxWindowMenuViewTest {

    private int mLifts;
    private int mClosed;

    private LinuxWindowMenuView menu() {
        LinuxWindowMenuView view = new LinuxWindowMenuView(
            ApplicationProvider.getApplicationContext(), new LinuxWindowMenuView.Listener() {
                @Override public void close() { mClosed++; }
                @Override public void minimise() { }
                @Override public void scale() { }
                @Override public void fullScreen() { }
                @Override public boolean lift(float x, float y, int pointerId) {
                    mLifts++;
                    return true;
                }
            });
        view.show(true);
        view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        return view;
    }

    private static void touch(View row, int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(now, now, action, x, y, 0);
        row.dispatchTouchEvent(ev);
        ev.recycle();
    }

    @Test
    public void holdingMoveLiftsTheWindowAndPutsTheCardAway() {
        LinuxWindowMenuView menu = menu();
        View move = menu.getChildAt(2);
        touch(move, MotionEvent.ACTION_DOWN, 10f, 10f);
        assertEquals(0, mLifts);
        ShadowLooper.idleMainLooper(HoldTiming.holdTimeoutMs() + 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(1, mLifts);
        assertFalse(menu.isOpen());
    }

    @Test
    public void aTapOnMoveDoesNothing() {
        LinuxWindowMenuView menu = menu();
        View move = menu.getChildAt(2);
        touch(move, MotionEvent.ACTION_DOWN, 10f, 10f);
        touch(move, MotionEvent.ACTION_UP, 10f, 10f);
        ShadowLooper.idleMainLooper(HoldTiming.holdTimeoutMs() + 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(0, mLifts);
        assertTrue(menu.isOpen());
    }

    @Test
    public void aFingerThatSetsOffBeforeTheHoldLiftsNothing() {
        LinuxWindowMenuView menu = menu();
        View move = menu.getChildAt(2);
        touch(move, MotionEvent.ACTION_DOWN, 10f, 10f);
        touch(move, MotionEvent.ACTION_MOVE, 200f, 10f);
        ShadowLooper.idleMainLooper(HoldTiming.holdTimeoutMs() + 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(0, mLifts);
    }

    @Test
    public void closeIsAnActionAndPutsTheCardAway() {
        LinuxWindowMenuView menu = menu();
        menu.getChildAt(0).performClick();
        assertEquals(1, mClosed);
        assertFalse(menu.isOpen());
    }
}
