package com.termux.app.statusbar;

import android.app.Application;
import android.app.Activity;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.R;
import com.termux.app.terminal.TerminalWindowBar;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import org.robolectric.Shadows;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusBarSwipeLayoutTest {

    @Test
    public void sidewaysSwipes_neverChangeTheForm() {
        // The bar is the pager: a sideways drag moves the wall or nothing. Folding and unfolding
        // belong to the vertical drag alone, whichever form the bar is in.
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(true);
        view.setListener(requests::add);
        swipe(view, 30f, 160f, 40f, 40f);
        assertEquals(0, requests.size());

        view.setCollapsed(false);
        swipe(view, 160f, 30f, 40f, 40f);
        assertEquals(0, requests.size());
    }

    @Test
    public void emptyTap_performsClickWithoutExpansionAction() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        AtomicInteger clicks = new AtomicInteger();
        view.setCollapsed(true);
        view.setListener(requests::add);
        view.setOnClickListener(ignored -> clicks.incrementAndGet());

        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 40f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 100f, 40f));

        assertEquals(0, requests.size());
        assertEquals(1, clicks.get());
    }

    @Test
    public void tap_isNotInterceptedFromChildWidgets() {
        StatusBarSwipeLayout view = createView();

        assertFalse(view.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 40f, 40f)));
        assertFalse(view.onInterceptTouchEvent(event(MotionEvent.ACTION_UP, 40f, 40f)));
    }

    @Test
    public void windowBarEdgeOverswipeStaysTheStripsOwnStreamThroughRealDispatch() {
        StatusBarSwipeLayout view = createView();
        TerminalWindowBar bar = new TerminalWindowBar(view.getContext(), null);
        bar.setId(R.id.terminal_window_bar);
        List<String> barRequests = new ArrayList<>();
        List<String> parentRequests = new ArrayList<>();
        bar.setOnEdgeOverswipeListener(new TerminalWindowBar.OnEdgeOverswipeListener() {
            @Override public boolean onEdgeOverswipeBegin() { barRequests.add("begin"); return true; }
            @Override public void onEdgeOverswipe(float dxPx) { }
            @Override public void onEdgeOverswipeEnd(float velocityPxPerSec) {
                barRequests.add("end");
            }
            @Override public void onEdgeOverswipeCancel() { barRequests.add("cancel"); }
        });
        view.setListener(collapsed -> parentRequests.add("swipe"));
        view.addView(bar, new FrameLayout.LayoutParams(200, 30));
        bar.layout(0, 0, 200, 30);
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 20, 15));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 90, 15));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 90, 15));
        assertEquals(java.util.Arrays.asList("begin", "end"), barRequests);
        assertTrue(parentRequests.isEmpty());
    }

    @Test
    public void aVerticalSwipeTogglesTheBarAndAnAlreadySatisfiedOneDoesNot() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(true);
        view.setListener(requests::add);

        // Compact bar, downward drag: expand. This is the only swipe that changes its form.
        swipe(view, 100f, 105f, 15f, 70f);
        assertEquals(java.util.Collections.singletonList(false), requests);

        // Compact bar, upward drag: nothing to collapse.
        requests.clear();
        swipe(view, 100f, 105f, 70f, 15f);
        assertEquals(0, requests.size());

        // A sideways drag with no wall to move is nobody's, and never a form change.
        requests.clear();
        swipe(view, 160f, 30f, 40f, 40f);
        assertEquals(0, requests.size());
    }

    @Test
    public void nestedScrollConsumesZeroAndCancelsHold() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> toggles = new ArrayList<>();
        view.setListener(toggles::add);
        View child = new View(view.getContext());
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 100, 40));
        assertTrue(view.onStartNestedScroll(child, child,
            androidx.core.view.ViewCompat.SCROLL_AXIS_VERTICAL,
            androidx.core.view.ViewCompat.TYPE_TOUCH));
        view.onNestedScrollAccepted(child, child, androidx.core.view.ViewCompat.SCROLL_AXIS_VERTICAL,
            androidx.core.view.ViewCompat.TYPE_TOUCH);
        int[] consumed = {0, 0};
        view.onNestedPreScroll(child, 0, 40, consumed, androidx.core.view.ViewCompat.TYPE_TOUCH);
        assertEquals(0, consumed[0]);
        assertEquals(0, consumed[1]);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertTrue(toggles.isEmpty());
    }

    @Test
    public void nestedScrollingChildOwnsFrozenDownBeforeItStartsScrolling() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> toggles = new ArrayList<>();
        view.setListener(toggles::add);
        View nested = new View(view.getContext());
        nested.setNestedScrollingEnabled(true);
        view.addView(nested, new FrameLayout.LayoutParams(80, 60));
        nested.layout(0, 0, 80, 60);
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 40, 30));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 40, 30));
        assertTrue(toggles.isEmpty());
    }

    @Test
    public void pullHintColorIsTheThemeAccentNotALiteralGrey() {
        StatusBarSwipeLayout view = createView();
        assertNotEquals(0xFFB0B0B0, view.pullHintColor());
        assertEquals(androidx.core.content.ContextCompat.getColor(view.getContext(),
            R.color.termux_primary), view.pullHintColor());
    }

    /** Records the wall-drag stream the bar sends. */
    private static final class WallListener implements StatusBarSwipeLayout.Listener {
        final List<String> events = new ArrayList<>();
        @Override public void onCollapsedStateRequested(boolean collapsed) { events.add("form"); }
        @Override public boolean onWallDragBegin() { events.add("begin"); return true; }
        @Override public void onWallDrag(float dxPx) { events.add("drag"); }
        @Override public void onWallDragEnd(float velocityPxPerSec) { events.add("end"); }
        @Override public void onWallDragCancel() { events.add("cancel"); }
    }

    @Test
    public void cancelWallDragEndsTheStreamWithoutAnEndOrCancel() {
        StatusBarSwipeLayout view = createView();
        view.setWallAvailable(true);
        WallListener listener = new WallListener();
        view.setListener(listener);
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 40f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 160f, 40f));
        assertEquals(java.util.Arrays.asList("begin", "drag"), listener.events);

        // The wall moved on its own (a tile tap, wall.go, Home) and says so.
        view.cancelWallDrag();
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 190f, 40f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 190f, 40f));

        assertEquals("the rest of that finger reaches nobody",
            java.util.Arrays.asList("begin", "drag"), listener.events);

        // The next touch starts clean.
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 40f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 160f, 40f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 160f, 40f));
        assertEquals(java.util.Arrays.asList("begin", "drag", "begin", "drag", "end"),
            listener.events);
    }

    @Test
    public void cancelWallDragWithNoDragUnderWayIsANoOp() {
        StatusBarSwipeLayout view = createView();
        view.setWallAvailable(true);
        WallListener listener = new WallListener();
        view.setListener(listener);

        view.cancelWallDrag();
        swipe(view, 100f, 160f, 40f, 40f);

        assertEquals(java.util.Arrays.asList("begin", "drag", "end"), listener.events);
    }

    private static StatusBarSwipeLayout createView() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        StatusBarSwipeLayout view = new StatusBarSwipeLayout(
            activity, null);
        activity.setContentView(view);
        view.measure(exact(200), exact(80));
        view.layout(0, 0, 200, 80);
        return view;
    }

    private static TerminalWindowBar addWindowBar(StatusBarSwipeLayout view,
                                                   boolean includeWindowChip) {
        TerminalWindowBar bar = new TerminalWindowBar(view.getContext(), null);
        bar.setId(R.id.terminal_window_bar);
        if (includeWindowChip) {
            bar.setWindows(Collections.singletonList(
                new TerminalWindowBar.WindowItem("shell", "shell")), 0);
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(160, 30);
        params.leftMargin = 20;
        params.topMargin = 30;
        view.addView(bar, params);
        bar.measure(exact(160), exact(30));
        bar.layout(20, 30, 180, 60);
        return bar;
    }

    private static void collectClickableDescendants(View view, List<View> result) {
        if (view.isClickable()) result.add(view);
        if (!(view instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectClickableDescendants(group.getChildAt(i), result);
        }
    }

    private static void swipe(StatusBarSwipeLayout view, float startX, float endX,
                              float startY, float endY) {
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, startX, startY));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, endX, endY));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, endX, endY));
    }

    private static MotionEvent event(int action, float x, float y) {
        return MotionEvent.obtain(0L, 10L, action, x, y, 0);
    }

    private static int exact(int size) {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
    }
}
