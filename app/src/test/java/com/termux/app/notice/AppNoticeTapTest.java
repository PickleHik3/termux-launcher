package com.termux.app.notice;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A tap on an actionable pill runs its action. The pill can rest over the thin status row, which
 * takes window streams ahead of the views; the activity asks
 * {@link AppNotice#pillClaimsWindowDown} first and hands a stream that starts on the pill to the
 * views whole, where the pill's own tap path runs.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@LooperMode(LooperMode.Mode.LEGACY)
public class AppNoticeTapTest {

    @Test
    public void aDownOnTheShownPillIsThePills() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);
        AtomicInteger ran = new AtomicInteger();
        AppNotice.shell(activity, "Finish setting up the desktop", null, (String) null, false,
            ran::incrementAndGet, AppNoticeHostView.HOLD_LONG_MS);
        layOut(activity);

        int[] at = new int[2];
        host.getLocationInWindow(at);
        float x = at[0] + host.getWidth() / 2f;
        float y = at[1] + host.getHeight() / 2f;
        assertTrue(host.getWidth() > 0 && host.getHeight() > 0);
        assertTrue(AppNotice.pillClaimsWindowDown(activity, x, y));
        assertFalse(AppNotice.pillClaimsWindowDown(activity, x, at[1] + host.getHeight() + 40f));

        tap(activity, x, y);
        assertEquals(1, ran.get());
    }

    @Test
    public void anOfferRaisedFromTheApplicationRunsOnATapThroughTheActivity() {
        Application app = org.robolectric.RuntimeEnvironment.getApplication();
        AppNotice.install(app);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AtomicInteger ran = new AtomicInteger();
        // As X11LinuxAppRunner raises the desktop offer: the application context, a long hold.
        AppNotice.shell(app, "Finish setting up the desktop", "Tap to add the missing pieces",
            (String) null, true, ran::incrementAndGet, AppNoticeItem.Hold.UNDO.ms);
        layOut(activity);

        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);
        assertNotNull(host.activeItem());
        int[] at = new int[2];
        host.getLocationInWindow(at);
        float x = at[0] + host.getWidth() / 2f;
        float y = at[1] + host.getHeight() / 2f;
        assertTrue(AppNotice.pillClaimsWindowDown(activity, x, y));

        tap(activity, x, y);
        assertEquals(1, ran.get());
    }

    @Test
    public void nothingIsClaimedWithoutANotice() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        // No pill created yet: the question never creates one.
        assertFalse(AppNotice.pillClaimsWindowDown(activity, 10f, 10f));
        AppNoticeHostView host = AppNotice.hostFor(activity);
        assertNotNull(host);
        AppNotice.shell(activity, "Offer", null, (String) null, false, () -> { },
            AppNoticeHostView.HOLD_LONG_MS);
        layOut(activity);
        int[] at = new int[2];
        host.getLocationInWindow(at);
        float x = at[0] + host.getWidth() / 2f;
        float y = at[1] + host.getHeight() / 2f;
        host.clear();
        assertFalse(AppNotice.pillClaimsWindowDown(activity, x, y));
    }

    private static void layOut(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1080, 2400);
    }

    private static void tap(Activity activity, float x, float y) {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0);
        try {
            activity.dispatchTouchEvent(down);
            activity.dispatchTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }
}
