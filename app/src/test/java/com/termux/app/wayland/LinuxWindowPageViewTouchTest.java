package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;

/**
 * A Linux window's page stands over the Terminal's pane host (#392): it takes every touch that
 * reaches it, so the terminal behind never sees one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LinuxWindowPageViewTouchTest {

    private static LinuxWindowPageView.Host quietHost() {
        return (LinuxWindowPageView.Host) Proxy.newProxyInstance(
            LinuxWindowPageView.Host.class.getClassLoader(),
            new Class<?>[] {LinuxWindowPageView.Host.class}, (proxy, method, args) -> {
                Class<?> type = method.getReturnType();
                return type.isPrimitive() && type != void.class
                    ? Array.get(Array.newInstance(type, 1), 0) : null;
            });
    }

    @Test
    public void itConsumesTheTouchesOfAGesture() {
        LinuxWindowPageView view = new LinuxWindowPageView(
            ApplicationProvider.getApplicationContext(), quietHost());
        view.layout(0, 0, 400, 800);
        for (int action : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(0L, 0L, action, 200f, 400f, 0);
            assertTrue("action " + action, view.onTouchEvent(event));
            event.recycle();
        }
    }

    @Test
    public void theAppsSlotStartsAtTheStatusRowsRoomWithNoBandAboveIt() {
        LinuxWindowPageView view = new LinuxWindowPageView(
            ApplicationProvider.getApplicationContext(), quietHost());
        assertEquals(0, ((android.widget.FrameLayout.LayoutParams) view.slot().getLayoutParams()).topMargin);
        view.setTopInsetPx(40);
        assertEquals(40, ((android.widget.FrameLayout.LayoutParams) view.slot().getLayoutParams()).topMargin);
        assertEquals(760, view.slotHeightFor(800));
    }
}
