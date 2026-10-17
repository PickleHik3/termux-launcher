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

        // A sideways drag is nobody's, and never a form change.
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

    // ---------------------------------------------------------------- the capsule

    private static final StatusBarFormPolicy.Form CAPSULE = StatusBarFormPolicy.Form.CAPSULE;
    private static final StatusBarFormPolicy.Form COMPACT = StatusBarFormPolicy.Form.COMPACT;
    private static final StatusBarFormPolicy.Form EXPANDED = StatusBarFormPolicy.Form.EXPANDED;

    /** A host that lands what it is asked, as the activity does, and records the asks. */
    private static StatusBarSwipeLayout.Listener echoingHost(StatusBarSwipeLayout view,
                                                             List<Boolean> requests) {
        return collapsed -> {
            requests.add(collapsed);
            view.setCollapsed(collapsed);
        };
    }

    /** {@code value} dp in this view's pixels. */
    private static float dp(StatusBarSwipeLayout view, float value) {
        return value * view.getResources().getDisplayMetrics().density;
    }

    /**
     * A vertical drag from y0 to y1 at x 100: slow (a second, ending at rest) or quick (the
     * whole way in about 11 ms, over a thousand dp a second for the shortest drag here; the
     * test tracker reads only samples at least 10 ms after the oldest).
     */
    private static void drag(StatusBarSwipeLayout view, float y0, float y1, boolean quick) {
        long down = 1_000L;
        view.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, 100f, y0,
            0));
        if (quick) {
            view.dispatchTouchEvent(MotionEvent.obtain(down, down + 10L, MotionEvent.ACTION_MOVE,
                100f, (y0 + y1) / 2f, 0));
            view.dispatchTouchEvent(MotionEvent.obtain(down, down + 11L, MotionEvent.ACTION_MOVE,
                100f, y1, 0));
            view.dispatchTouchEvent(MotionEvent.obtain(down, down + 12L, MotionEvent.ACTION_UP,
                100f, y1, 0));
        } else {
            view.dispatchTouchEvent(MotionEvent.obtain(down, down + 400L, MotionEvent.ACTION_MOVE,
                100f, y1, 0));
            view.dispatchTouchEvent(MotionEvent.obtain(down, down + 900L, MotionEvent.ACTION_MOVE,
                100f, y1, 0));
            view.dispatchTouchEvent(MotionEvent.obtain(down, down + 1_400L, MotionEvent.ACTION_UP,
                100f, y1, 0));
        }
    }

    private static void idle(long millis) {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    @Test
    public void aSlowFoldPastHalfTheCapsulesStretchLandsInTheCapsuleWithoutTheHost() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(true);
        view.setListener(echoingHost(view, requests));
        // The capsule's stretch is 40 dp; 30 up from compact is nearer the capsule.
        drag(view, 60f, 60f - dp(view, 30f), false);
        assertEquals(CAPSULE, view.form());
        assertTrue("the capsule is the bar's own: the host's height never moved",
            requests.isEmpty());
    }

    @Test
    public void aSlowShortFoldSpringsBackToCompact() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(true);
        view.setListener(echoingHost(view, requests));
        // 18 dp: past the test runtime's 16 px touch slop, short of half the capsule's 40 dp.
        drag(view, 60f, 60f - dp(view, 18f), false);
        assertEquals(COMPACT, view.form());
        assertTrue(requests.isEmpty());
    }

    @Test
    public void aQuickShortFoldStepsIntoTheCapsule() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(true);
        view.setListener(echoingHost(view, requests));
        drag(view, 60f, 60f - dp(view, 18f), true);
        assertEquals(CAPSULE, view.form());
    }

    @Test
    public void aTapOnTheCapsuleOpensCompactAndOneBesideItDoesNot() {
        StatusBarSwipeLayout view = createView();
        view.setCollapsed(true);
        view.setMinimalMode(true);
        assertEquals(CAPSULE, view.form());
        // Beside the capsule, at the band's far end: nobody's.
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 3f, 40f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 3f, 40f));
        assertEquals(CAPSULE, view.form());
        // On it, at the bar's centre.
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 40f));
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 100f, 40f));
        assertEquals(COMPACT, view.form());
    }

    @Test
    public void aSlowLongDragFromTheCapsuleCrossesCompactToExpanded() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(true);
        view.setMinimalMode(true);
        view.setFoldTravelPx(Math.round(dp(view, 60f)));
        view.setListener(echoingHost(view, requests));
        // Capsule 0, compact 40, expanded 100 dp on the finger's axis: 95 is nearest expanded.
        drag(view, 10f, 10f + dp(view, 95f), false);
        assertEquals(Collections.singletonList(false), requests);
        assertEquals(EXPANDED, view.form());
    }

    @Test
    public void aQuickDragFromTheCapsuleStepsOnlyToCompactAndLandsTheHostThere() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(true);
        view.setMinimalMode(true);
        view.setFoldTravelPx(Math.round(dp(view, 60f)));
        view.setListener(echoingHost(view, requests));
        drag(view, 10f, 10f + dp(view, 65f), true);
        assertEquals(COMPACT, view.form());
        // The finger had the host's height past compact; it is landed back there.
        assertEquals(Collections.singletonList(true), requests);
    }

    @Test
    public void theBarFoldsToTheCapsuleAfterFourQuietSecondsButNotUnderAFinger() {
        StatusBarSwipeLayout view = createView();
        List<Boolean> requests = new ArrayList<>();
        view.setCollapsed(false);
        view.setListener(echoingHost(view, requests));
        // A finger resting on the bar holds it as long as it stays.
        view.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 40f));
        idle(10_000L);
        assertEquals(EXPANDED, view.form());
        view.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 100f, 40f));
        view.setCollapsed(false);
        idle(3_900L);
        assertEquals(EXPANDED, view.form());
        idle(200L);
        // The host folded its height first; the capsule is the bar's.
        assertEquals(Collections.singletonList(true), requests);
        assertEquals(CAPSULE, view.form());
    }

    @Test
    public void minimalModeStartsAsTheCapsuleAndLeavingItGivesCompact() {
        StatusBarSwipeLayout view = createView();
        view.setCollapsed(true);
        view.setMinimalMode(true);
        assertEquals(CAPSULE, view.form());
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, view.getImportantForAccessibility());
        assertTrue(String.valueOf(view.getContentDescription()).startsWith("Status bar folded"));
        ArrayList<View> children = new ArrayList<>();
        view.addChildrenForAccessibility(children);
        assertTrue("the folded content is not read", children.isEmpty());
        view.setMinimalMode(false);
        assertEquals(COMPACT, view.form());
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, view.getImportantForAccessibility());
    }

    @Test
    public void aBarDownASideNeverBecomesTheCapsule() {
        StatusBarSwipeLayout view = createView();
        view.setEdge(com.termux.app.place.PlaceLayout.Edge.LEFT);
        view.setExpansionAllowed(false);
        view.setCollapsed(true);
        view.setMinimalMode(true);
        assertEquals(COMPACT, view.form());
        idle(10_000L);
        assertEquals(COMPACT, view.form());
    }

    private static int exact(int size) {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
    }
}
