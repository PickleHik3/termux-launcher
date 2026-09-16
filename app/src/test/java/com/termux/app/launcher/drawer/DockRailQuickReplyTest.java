package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.termux.app.launcher.notifications.NotificationSwipePolicy;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;

/**
 * The rail's half of sharing its pull with a badged icon's quick reply. On a side the two run the
 * same way — the pull is sideways off the rail's edge and so is the reply — and the rail is the
 * outermost handler, so without this gate the drawer claimed every such drag before the icon under
 * the finger ever saw it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class DockRailQuickReplyTest {

    private Context context;
    private DockRailScrollView rail;
    private RecordingListener listener;
    private boolean badgedDown;
    private float handoffPx;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        rail = new DockRailScrollView(context);
        rail.addView(new View(context), new ViewGroup.LayoutParams(120, 800));
        rail.measure(View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        rail.layout(0, 0, 120, 800);
        listener = new RecordingListener();
        rail.setDrawerPullListener(listener);
        rail.setQuickReplyProbe((x, y) -> badgedDown);
        handoffPx = context.getResources().getDisplayMetrics().widthPixels
            * NotificationSwipePolicy.HANDOFF_TRAVEL_FRACTION;
    }

    @Test
    public void aBadgedIconHoldsTheRailsPullUntilTheDragIsLong() {
        badgedDown = true;
        dispatch(MotionEvent.ACTION_DOWN, 20f, 300f);
        dispatch(MotionEvent.ACTION_MOVE, 20f + handoffPx * 0.5f, 300f);
        assertEquals("the reply still has it", 0, listener.begins);

        float handoff = 20f + handoffPx + 40f;
        dispatch(MotionEvent.ACTION_MOVE, handoff, 300f);
        assertEquals(1, listener.begins);
        // The plane grows from where the finger got to, not from the icon the drag started on.
        assertEquals(handoff, listener.downPull, 1f);
        dispatch(MotionEvent.ACTION_UP, handoff, 300f);
        assertEquals(1, listener.ends);
    }

    @Test
    public void anUnbadgedDownIsTheDrawersAtTheFirstMoveAsEver() {
        badgedDown = false;
        dispatch(MotionEvent.ACTION_DOWN, 20f, 300f);
        dispatch(MotionEvent.ACTION_MOVE, 20f + 200f, 300f);
        assertEquals(1, listener.begins);
        assertEquals(20f, listener.downPull, 1f);
        dispatch(MotionEvent.ACTION_UP, 20f + 200f, 300f);
    }

    private void dispatch(int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
        try {
            rail.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static final class RecordingListener
        implements DockRailScrollView.DrawerPullListener {

        int begins;
        int ends;
        float downPull;

        @NonNull
        @Override
        public AppDrawerGestureArbiter.Eligibility captureDrawerEligibility() {
            return new AppDrawerGestureArbiter.Eligibility(true, true, true,
                AppDrawerGestureArbiter.Pull.RIGHT, true, true, true, true);
        }

        @Override public void onDrawerDragBegin(float downPull) {
            begins++;
            this.downPull = downPull;
        }

        @Override public void onDrawerDrag(float pull) { }

        @Override public void onDrawerDragEnd(float velocityPxPerSec) { ends++; }

        @Override public void onDrawerDragCancel() { }
    }
}
