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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import org.robolectric.Shadows;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusBarSwipeLayoutTest {

    @Test
    public void rightSwipe_expandsCollapsedPanel() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(true);
        view.setListener(requests::add);

        swipe(view, 30f, 160f, 40f, 40f);

        assertEquals(1, requests.size());
        assertEquals(Boolean.FALSE, requests.get(0));
    }

    @Test
    public void leftSwipe_collapsesExpandedPanel() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(false);
        view.setListener(requests::add);

        swipe(view, 160f, 30f, 40f, 40f);

        assertEquals(1, requests.size());
        assertEquals(Boolean.TRUE, requests.get(0));
    }

    @Test
    public void emptyTap_hasNoExpansionAction() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(true);
        view.setListener(requests::add);

        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 40f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 100f, 40f));

        assertEquals(0, requests.size());
    }

    @Test
    public void tap_isNotInterceptedFromChildWidgets() {
        StatusBarSwipeLayout view = createView();

        assertFalse(view.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 40f, 40f)));
        assertFalse(view.onInterceptTouchEvent(event(MotionEvent.ACTION_UP, 40f, 40f)));
    }

    @Test
    public void gestureStartingInsideWindowBarIsNeverIntercepted() {
        StatusBarSwipeLayout view = createView();
        TerminalWindowBar bar = new TerminalWindowBar(view.getContext(), null);
        bar.setId(R.id.terminal_window_bar);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(160, 30);
        params.leftMargin = 20;
        params.topMargin = 30;
        view.addView(bar, params);
        bar.layout(20, 30, 180, 60);

        assertFalse(view.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 40f, 40f)));
        assertFalse(view.onInterceptTouchEvent(event(MotionEvent.ACTION_MOVE, 170f, 40f)));
        assertFalse(view.onInterceptTouchEvent(event(MotionEvent.ACTION_UP, 170f, 40f)));
    }

    @Test
    public void windowBarEdgeOverswipeRemainsChildOwnedThroughRealDispatch() {
        StatusBarSwipeLayout view = createView();
        TerminalWindowBar bar = new TerminalWindowBar(view.getContext(), null);
        bar.setId(R.id.terminal_window_bar);
        List<Boolean> barRequests = new ArrayList<>();
        List<String> parentRequests = new ArrayList<>();
        bar.setStatusBarCollapsed(true);
        bar.setOnEdgeOverswipeListener(barRequests::add);
        view.setListener(new StatusBarSwipeLayout.Listener() {
            @Override public void onCollapsedStateRequested(boolean collapsed) {
                parentRequests.add("swipe");
            }
            @Override public void onFullStateRequested(TopStatusBarState prior) {
                parentRequests.add("full");
            }
        });
        view.addView(bar, new FrameLayout.LayoutParams(200, 30));
        bar.layout(0, 0, 200, 30);
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 20, 15));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 90, 15));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 90, 15));
        assertEquals(java.util.Collections.singletonList(Boolean.FALSE), barRequests);
        assertTrue(parentRequests.isEmpty());
    }

    @Test
    public void verticalOrAlreadySatisfiedSwipe_hasNoAction() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(true);
        view.setListener(requests::add);

        swipe(view, 100f, 105f, 15f, 70f);
        swipe(view, 160f, 30f, 40f, 40f);

        assertEquals(0, requests.size());
    }

    @Test
    public void longPressFromCompactAndExpandedCallsFullExactlyOnce() {
        for (boolean collapsed : new boolean[] {true, false}) {
            StatusBarSwipeLayout view = createView();
            List<TopStatusBarState> full = new ArrayList<>();
            view.setCollapsed(collapsed);
            view.setListener(new StatusBarSwipeLayout.Listener() {
                @Override public void onCollapsedStateRequested(boolean value) { }
                @Override public void onFullStateRequested(TopStatusBarState prior) { full.add(prior); }
            });
            view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 100, 40));
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(
                Duration.ofMillis(android.view.ViewConfiguration.getLongPressTimeout() + 1));
            view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 100, 40));
            assertEquals(1, full.size());
            assertEquals(collapsed ? TopStatusBarState.COMPACT : TopStatusBarState.EXPANDED,
                full.get(0));
        }
    }

    @Test
    public void nestedScrollConsumesZeroAndCancelsHold() {
        StatusBarSwipeLayout view = createView();
        List<TopStatusBarState> full = new ArrayList<>();
        view.setListener(new StatusBarSwipeLayout.Listener() {
            @Override public void onCollapsedStateRequested(boolean value) { }
            @Override public void onFullStateRequested(TopStatusBarState prior) { full.add(prior); }
        });
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
        assertTrue(full.isEmpty());
    }

    @Test
    public void nestedScrollingChildOwnsFrozenDownBeforeItStartsScrolling() {
        StatusBarSwipeLayout view = createView();
        List<TopStatusBarState> full = new ArrayList<>();
        view.setListener(new StatusBarSwipeLayout.Listener() {
            @Override public void onCollapsedStateRequested(boolean value) { }
            @Override public void onFullStateRequested(TopStatusBarState prior) { full.add(prior); }
        });
        View nested = new View(view.getContext());
        nested.setNestedScrollingEnabled(true);
        view.addView(nested, new FrameLayout.LayoutParams(80, 60));
        nested.layout(0, 0, 80, 60);
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 40, 30));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 40, 30));
        assertTrue(full.isEmpty());
    }

    @Test
    public void fullCallbackReentrantResetCannotAlsoFireSwipe() {
        StatusBarSwipeLayout view = createView();
        List<String> actions = new ArrayList<>();
        view.setListener(new StatusBarSwipeLayout.Listener() {
            @Override public void onCollapsedStateRequested(boolean value) { actions.add("swipe"); }
            @Override public void onFullStateRequested(TopStatusBarState prior) {
                actions.add("full");
                view.setStatusState(TopStatusBarState.FULL, prior);
            }
        });
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 30, 40));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 180, 40));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 180, 40));
        assertEquals(java.util.Collections.singletonList("full"), actions);
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
