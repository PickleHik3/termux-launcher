package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The window strip's edge overswipe: travel the chips cannot spend is offered to the host once
 * it clears the slop, then streamed for the rest of the finger, and ended, cancelled or — when
 * the wall moves on without this finger — dropped with no end at all.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalWindowBarOverswipeTest {

    private static final class Host implements TerminalWindowBar.OnEdgeOverswipeListener {
        final List<String> events = new ArrayList<>();
        boolean accept = true;
        float lastDx;

        @Override public boolean onEdgeOverswipeBegin() { events.add("begin"); return accept; }
        @Override public void onEdgeOverswipe(float dxPx) { events.add("drag"); lastDx = dxPx; }
        @Override public void onEdgeOverswipeEnd(float velocityPxPerSec) { events.add("end"); }
        @Override public void onEdgeOverswipeCancel() { events.add("cancel"); }
    }

    private static TerminalWindowBar bar() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        // An empty strip has nothing to scroll, so every pixel of travel is surplus.
        TerminalWindowBar bar = new TerminalWindowBar(activity, null);
        activity.setContentView(bar);
        bar.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, 300, 40);
        return bar;
    }

    private static void touch(TerminalWindowBar bar, int action, float x) {
        bar.dispatchTouchEvent(MotionEvent.obtain(0L, 10L, action, x, 20f, 0));
    }

    @Test public void surplusTravelBeginsTheOverswipeAndStreamsFromRest() {
        TerminalWindowBar bar = bar();
        Host host = new Host();
        bar.setOnEdgeOverswipeListener(host);

        touch(bar, MotionEvent.ACTION_DOWN, 100f);
        touch(bar, MotionEvent.ACTION_MOVE, 140f);
        assertEquals("the slop that proved the intent is not travel",
            Arrays.asList("begin", "drag"), host.events);
        assertEquals(0f, host.lastDx, 0.01f);

        touch(bar, MotionEvent.ACTION_MOVE, 170f);
        assertEquals(30f, host.lastDx, 0.01f);
        touch(bar, MotionEvent.ACTION_UP, 170f);
        assertEquals(Arrays.asList("begin", "drag", "drag", "end"), host.events);
    }

    @Test public void aCancelledStreamCancelsTheOverswipe() {
        TerminalWindowBar bar = bar();
        Host host = new Host();
        bar.setOnEdgeOverswipeListener(host);

        touch(bar, MotionEvent.ACTION_DOWN, 100f);
        touch(bar, MotionEvent.ACTION_MOVE, 140f);
        touch(bar, MotionEvent.ACTION_CANCEL, 140f);

        assertEquals(Arrays.asList("begin", "drag", "cancel"), host.events);
    }

    @Test public void aHostThatDeclinesIsAskedAgainAsTheFingerGoesOn() {
        TerminalWindowBar bar = bar();
        Host host = new Host();
        host.accept = false;
        bar.setOnEdgeOverswipeListener(host);

        touch(bar, MotionEvent.ACTION_DOWN, 100f);
        touch(bar, MotionEvent.ACTION_MOVE, 140f);
        touch(bar, MotionEvent.ACTION_MOVE, 180f);
        touch(bar, MotionEvent.ACTION_UP, 180f);

        assertEquals("never owned, so never streamed or ended",
            Arrays.asList("begin", "begin"), host.events);
    }

    @Test public void aVerticalGestureNeverBeginsAnOverswipe() {
        TerminalWindowBar bar = bar();
        Host host = new Host();
        bar.setOnEdgeOverswipeListener(host);

        bar.dispatchTouchEvent(MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_DOWN, 100f, 5f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_MOVE, 110f, 39f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_MOVE, 150f, 39f, 0));
        bar.dispatchTouchEvent(MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_UP, 150f, 39f, 0));

        assertTrue(host.events.toString(), host.events.isEmpty());
    }

    @Test public void cancelOverswipeDropsTheRestOfTheFingerWithNoEnd() {
        TerminalWindowBar bar = bar();
        Host host = new Host();
        bar.setOnEdgeOverswipeListener(host);
        touch(bar, MotionEvent.ACTION_DOWN, 100f);
        touch(bar, MotionEvent.ACTION_MOVE, 140f);

        // The wall moved on (a tile tap, wall.go, Home) and told the strip to let go.
        bar.cancelOverswipe();
        touch(bar, MotionEvent.ACTION_MOVE, 200f);
        touch(bar, MotionEvent.ACTION_UP, 200f);

        assertEquals("not streamed, not ended, not cancelled",
            Arrays.asList("begin", "drag"), host.events);

        // The next finger starts clean.
        touch(bar, MotionEvent.ACTION_DOWN, 100f);
        touch(bar, MotionEvent.ACTION_MOVE, 140f);
        touch(bar, MotionEvent.ACTION_UP, 140f);
        assertEquals(Arrays.asList("begin", "drag", "begin", "drag", "end"), host.events);
    }

    @Test public void cancelOverswipeWithNothingOwnedChangesNothing() {
        TerminalWindowBar bar = bar();
        Host host = new Host();
        bar.setOnEdgeOverswipeListener(host);

        bar.cancelOverswipe();
        touch(bar, MotionEvent.ACTION_DOWN, 100f);
        touch(bar, MotionEvent.ACTION_MOVE, 140f);
        touch(bar, MotionEvent.ACTION_UP, 140f);

        assertEquals(Arrays.asList("begin", "drag", "end"), host.events);
    }
}
