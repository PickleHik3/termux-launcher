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
        // A sideways drag on the bar is nobody's: the wall is paged from the pane's border, and
        // folding and unfolding belong to the vertical drag alone, whichever form the bar is in.
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
    public void emptyTap_performsClickAndDoesNotFold() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        AtomicInteger clicks = new AtomicInteger();
        view.setCollapsed(true);
        view.setListener(requests::add);
        view.setOnClickListener(ignored -> clicks.incrementAndGet());

        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 40f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 100f, 40f));

        assertTrue(requests.isEmpty());
        assertEquals(1, clicks.get());
    }

    @Test
    public void aDragIsNotConsumedAndNeverFoldsHoldOrNot() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        List<Float> drags = new ArrayList<>();
        view.setCollapsed(true);
        view.setListener(new StatusBarSwipeLayout.Listener() {
            @Override public void onCollapsedStateRequested(boolean collapsed) { requests.add(collapsed); }
            @Override public void onFoldDrag(float px) { drags.add(px); }
        });
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 15f));
        shadowLooperAdvance(100);
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 100f, 70f));
        assertFalse(view.onInterceptTouchEvent(event(MotionEvent.ACTION_MOVE, 100f, 75f)));
        assertFalse(view.onTouchEvent(event(MotionEvent.ACTION_MOVE, 100f, 75f)));
        shadowLooperAdvance(400);
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 100f, 90f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 100f, 90f));
        assertTrue(requests.isEmpty());
        assertTrue(drags.isEmpty());
        assertFalse(ticked(view));
    }

    @Test
    public void holdingThenDraggingDoesNotTickOrFold() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(true);
        view.setListener(requests::add);
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 15f));
        shadowLooperAdvance(500);
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 100f, 70f));
        assertFalse(view.onInterceptTouchEvent(event(MotionEvent.ACTION_MOVE, 100f, 70f)));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 100f, 70f));
        assertTrue(requests.isEmpty());
        assertFalse(ticked(view));
    }

    @Test
    public void holdingToTheLongPressTimeoutWithoutMovingNeverFolds() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(true);
        view.setListener(requests::add);
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 15f));
        shadowLooperAdvance(android.view.ViewConfiguration.getLongPressTimeout() + 50);
        // The popup's long-press cancels the view stream.
        view.dispatchTouchEvent(event(MotionEvent.ACTION_CANCEL, 100f, 15f));
        assertTrue(requests.isEmpty());
    }

    private static boolean ticked(View view) {
        return Shadows.shadowOf(view).lastHapticFeedbackPerformed()
            == android.view.HapticFeedbackConstants.CLOCK_TICK;
    }

    private static void shadowLooperAdvance(long ms) {
        org.robolectric.shadows.ShadowLooper.idleMainLooper(ms,
            java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Test
    public void tap_isNotInterceptedFromChildWidgets() {
        StatusBarSwipeLayout view = createView();

        assertFalse(view.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 40f, 40f)));
        assertFalse(view.onInterceptTouchEvent(event(MotionEvent.ACTION_UP, 40f, 40f)));
    }

    @Test
    public void aSidewaysStreamOnTheWindowBarNeverReachesTheSwipeHostThroughRealDispatch() {
        StatusBarSwipeLayout view = createView();
        TerminalWindowBar bar = new TerminalWindowBar(view.getContext(), null);
        bar.setId(R.id.terminal_window_bar);
        List<String> parentRequests = new ArrayList<>();
        view.setListener(collapsed -> parentRequests.add("swipe"));
        view.addView(bar, new FrameLayout.LayoutParams(200, 30));
        bar.layout(0, 0, 200, 30);
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 20, 15));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 90, 15));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 90, 15));
        assertTrue(parentRequests.isEmpty());
    }

    @Test
    public void noSwipeChangesTheForm() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(true);
        view.setListener(requests::add);

        swipe(view, 100f, 105f, 15f, 70f);
        assertEquals(0, requests.size());
        swipe(view, 100f, 105f, 70f, 15f);
        assertEquals(0, requests.size());
        swipe(view, 160f, 30f, 40f, 40f);
        assertEquals(0, requests.size());
        view.setCollapsed(false);
        swipe(view, 100f, 105f, 70f, 15f);
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
    public void thinRow_ownsAWindowStreamThatStartsOnItOutsideTheHost() {
        // The row lies over the top pane, below the host's own bounds: the views' dispatch never
        // offers it those presses, so the window asks the row first.
        StatusBarSwipeLayout view = createView();
        AtomicInteger rowTouches = new AtomicInteger();
        View row = new View(view.getContext()) {
            @Override public boolean onTouchEvent(MotionEvent e) {
                rowTouches.incrementAndGet();
                return true;
            }
        };
        row.setId(R.id.terminal_status_row);
        view.addView(row, new FrameLayout.LayoutParams(200, 24));
        view.setRowPresented(true);
        view.setRowRect(20, 120, 180, 160, 0);
        view.measure(exact(200), exact(80));
        view.layout(0, 0, 200, 80);
        int[] origin = new int[2];
        view.getLocationInWindow(origin);

        MotionEvent down = event(MotionEvent.ACTION_DOWN, origin[0] + 60f, origin[1] + 130f);
        assertTrue(view.dispatchWindowRowTouch(down));
        // The event goes back to the window's coordinates.
        assertEquals(origin[1] + 130f, down.getY(), 0.01f);
        assertTrue(view.dispatchWindowRowTouch(
            event(MotionEvent.ACTION_UP, origin[0] + 60f, origin[1] + 130f)));
        assertTrue(rowTouches.get() > 0);

        // Below the thin row is the terminal's first row of text, and outside the row is the pane.
        assertFalse(view.dispatchWindowRowTouch(
            event(MotionEvent.ACTION_DOWN, origin[0] + 60f, origin[1] + 400f)));
        assertFalse(view.dispatchWindowRowTouch(
            event(MotionEvent.ACTION_MOVE, origin[0] + 60f, origin[1] + 130f)));
        assertFalse(view.dispatchWindowRowTouch(
            event(MotionEvent.ACTION_DOWN, origin[0] + 5f, origin[1] + 130f)));

        // As the strip the row owns nothing.
        view.setRowPresented(false);
        assertFalse(view.dispatchWindowRowTouch(
            event(MotionEvent.ACTION_DOWN, origin[0] + 60f, origin[1] + 130f)));
    }

    @Test
    public void thinRow_theFirstTapOnAMarkChangesPage_andTheRowNeverGrows() {
        // The Marks look in the thin row: one gesture, no waiting. A tap on a mark acts at once
        // and the row keeps its size and every reading.
        StatusBarSwipeLayout view = createView();
        List<String> calls = new ArrayList<>();
        StatusRowLayout row = new StatusRowLayout(view.getContext(), null);
        row.setId(R.id.terminal_status_row);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        FrameLayout sides = new FrameLayout(view.getContext());
        sides.setId(R.id.terminal_status_sides);
        row.addView(sides, new android.widget.LinearLayout.LayoutParams(0,
            FrameLayout.LayoutParams.MATCH_PARENT, 1f));
        MarksLineView marks = new MarksLineView(view.getContext(), null);
        marks.setId(R.id.terminal_status_marks_row);
        sides.addView(marks, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        marks.setNavigator(new com.termux.app.wall.pageswitch.PageSwitchTargets.Navigator() {
            @Override public void goToPage(int pageId) { calls.add("page " + pageId); }
            @Override public void showTerminalWindow(int index) { calls.add("window " + index); }
            @Override public void goToTerminal() { calls.add("terminal"); }
            @Override public void openOverview() { calls.add("overview"); }
        });
        marks.setPages(marksModel(), null);
        android.widget.LinearLayout widgets = new android.widget.LinearLayout(view.getContext());
        widgets.setId(R.id.terminal_status_widgets);
        row.addView(widgets, new android.widget.LinearLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT));
        View battery = new View(view.getContext());
        widgets.addView(battery, new android.widget.LinearLayout.LayoutParams(100,
            FrameLayout.LayoutParams.MATCH_PARENT));
        view.addView(row, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            24));
        float density = view.getResources().getDisplayMetrics().density;
        int top = 120;
        int rowPx = PaneRowPolicy.rowHeightPx(density);
        view.setRowPresented(true);
        view.setRowRect(10, top, 400, top + rowPx, 0);
        view.measure(exact(420), exact(80));
        view.layout(0, 0, 420, 80);
        com.termux.app.wall.pageswitch.MarksRowLayout rest = marks.rowLayout();
        org.junit.Assert.assertNotNull(rest);
        // Overview's cell, then Home, the window and the Assistant.
        assertEquals(4, rest.slotCount());
        int[] at = new int[2];
        marks.getLocationInWindow(at);
        float y = at[1] + marks.getHeight() / 2f;

        windowTap(view, at[0] + rest.centreAt(3), y);
        assertEquals(Collections.singletonList("page 3"), calls);
        assertEquals("the row never grows", rowPx, row.getHeight());
        assertEquals(1f, widgets.getAlpha(), 0f);

        windowTap(view, at[0] + rest.centreAt(0), y);
        assertEquals("overview", calls.get(1));
    }

    private static com.termux.app.wall.pageswitch.PageBarModel marksModel() {
        com.termux.app.wall.pageswitch.PageBarModel.Input in =
            new com.termux.app.wall.pageswitch.PageBarModel.Input();
        List<com.termux.app.wall.pageswitch.PageBarModel.Page> pages = new ArrayList<>();
        pages.add(new com.termux.app.wall.pageswitch.PageBarModel.Page(1,
            com.termux.app.page.PageKind.HOME, null));
        pages.add(new com.termux.app.wall.pageswitch.PageBarModel.Page(2,
            com.termux.app.page.PageKind.TERMINAL, null));
        pages.add(new com.termux.app.wall.pageswitch.PageBarModel.Page(3,
            com.termux.app.page.PageKind.ASSISTANT, null));
        in.pages = pages;
        in.windowNames = Collections.singletonList(null);
        in.currentPageId = 2;
        in.currentWindow = 0;
        return com.termux.app.wall.pageswitch.PageBarModel.build(in);
    }

    /** A tap through the activity's route into the row; the click a view posts is run. */
    private static void windowTap(StatusBarSwipeLayout view, float x, float y) {
        long t = android.os.SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, x, y, 0);
        assertTrue(view.dispatchWindowRowTouch(down));
        assertTrue(view.dispatchWindowRowTouch(up));
        down.recycle();
        up.recycle();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
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
        shadowLooperAdvance(260);
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
