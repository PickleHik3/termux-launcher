package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.termux.R;
import com.termux.app.chrome.CornerZones;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.wall.PaneControlsView;
import com.termux.view.HoldTiming;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Which touches the Display page reports as taps, and which of them it keeps. The text-focus
 * policy reads a tap as "the user pointed at something", so a drag, a two-finger gesture and the
 * page's own corners must not count — and nothing here may take a touch away from X. The edges
 * between the corners are the display's, and so is a tap in a corner: the square is <em>held</em>,
 * so a maximised window's own controls answer everywhere until a finger rests in one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11PaneFrameTapTest {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 800;

    /**
     * The frame on its own, without the display view its layout carries: that view binds the
     * native server on construction, so it cannot exist on the JVM.
     */
    private static X11PaneFrame page(Activity activity) {
        X11PaneFrame page = new X11PaneFrame(activity);
        page.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, WIDTH, HEIGHT);
        page.applyRunning(true);
        return page;
    }

    private static MotionEvent event(int action, float x, float y) {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0);
    }

    private static int tapsFor(Activity activity, MotionEvent... events) {
        X11PaneFrame page = page(activity);
        int[] taps = {0};
        page.setTapListener(() -> taps[0]++);
        for (MotionEvent event : events) {
            page.dispatchTouchEvent(event);
            event.recycle();
        }
        return taps[0];
    }

    @Test
    public void aFingerDownAndUpInPlaceIsATap() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertEquals(1, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, 300f, 400f),
            event(MotionEvent.ACTION_UP, 300f, 400f)));
    }

    @Test
    public void aFingerThatDriftsWithinTheSlopIsStillATap() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        float slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        assertEquals(1, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, 300f, 400f),
            event(MotionEvent.ACTION_MOVE, 300f + slop / 2f, 400f),
            event(MotionEvent.ACTION_UP, 300f + slop / 2f, 400f)));
    }

    @Test
    public void aDragIsNotATap() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        float slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        assertEquals(0, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, 300f, 400f),
            event(MotionEvent.ACTION_MOVE, 300f, 400f + slop * 4f),
            event(MotionEvent.ACTION_UP, 300f, 400f + slop * 4f)));
    }

    @Test
    public void aSecondFingerMakesItAGesture() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertEquals(0, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, 300f, 400f),
            event(MotionEvent.ACTION_POINTER_DOWN, 320f, 420f),
            event(MotionEvent.ACTION_UP, 300f, 400f)));
    }

    @Test
    public void aCancelledTouchIsNotATap() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertEquals(0, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, 300f, 400f),
            event(MotionEvent.ACTION_CANCEL, 300f, 400f),
            event(MotionEvent.ACTION_UP, 300f, 400f)));
    }

    @Test
    public void theCornersBelongToThePage_notToTheDisplay() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertEquals(0, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, 2f, 2f),
            event(MotionEvent.ACTION_UP, 2f, 2f)));
    }

    /** The old 12dp band swallowed these; a maximised X window needs every one of them. */
    @Test
    public void theEdgesBetweenTheCornersReachTheDisplay() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        assertEquals("the middle of the leading edge", 1, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, 1f, HEIGHT / 2f),
            event(MotionEvent.ACTION_UP, 1f, HEIGHT / 2f)));
        assertEquals("the middle of the top edge", 1, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, WIDTH / 2f, 1f),
            event(MotionEvent.ACTION_UP, WIDTH / 2f, 1f)));
        assertEquals("the middle of the bottom edge", 1, tapsFor(activity,
            event(MotionEvent.ACTION_DOWN, WIDTH / 2f, HEIGHT - 1f),
            event(MotionEvent.ACTION_UP, WIDTH / 2f, HEIGHT - 1f)));
    }

    /** The rule itself, without a frame around it. */
    @Test
    public void aTouchIsTheDisplaysOnlyWhenNothingElseWantsIt() {
        assertTrue(X11PaneFrame.touchIsTheDisplays(true, false, CornerZones.NONE));
        assertFalse("no display running",
            X11PaneFrame.touchIsTheDisplays(false, false, CornerZones.NONE));
        assertFalse("the page's own tab or rail",
            X11PaneFrame.touchIsTheDisplays(true, true, CornerZones.NONE));
        assertFalse("a corner",
            X11PaneFrame.touchIsTheDisplays(true, false, CornerZones.BOTTOM_LEFT));
    }

    @Test
    public void aPageWithNoDisplayRunningReportsNothing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame page = new X11PaneFrame(activity);
        page.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, WIDTH, HEIGHT);
        int[] taps = {0};
        page.setTapListener(() -> taps[0]++);
        MotionEvent down = event(MotionEvent.ACTION_DOWN, 300f, 400f);
        MotionEvent up = event(MotionEvent.ACTION_UP, 300f, 400f);
        page.dispatchTouchEvent(down);
        page.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        assertEquals(0, taps[0]);
    }

    /**
     * The frame wired as inflation wires it, with the tab and the rail its corners drop — still
     * without the display view, which binds the native server.
     */
    private static X11PaneFrame wiredPage(Activity activity) {
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        X11PaneFrame page = new X11PaneFrame(activity);
        page.addView(backdrop(activity, R.id.x11_pane_glass));
        page.addView(backdrop(activity, R.id.x11_pane_corner_mask));
        page.onFinishInflate();
        page.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, WIDTH, HEIGHT);
        page.applyRunning(true);
        return page;
    }

    private static PaneGlassBackdropView backdrop(Activity activity, int id) {
        PaneGlassBackdropView backdrop = new PaneGlassBackdropView(activity);
        backdrop.setId(id);
        backdrop.setVisibility(View.GONE);
        backdrop.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return backdrop;
    }

    /** A child standing in for X: it keeps every gesture and remembers what it was sent. */
    private static class Recorder extends View {
        final List<Integer> actions = new ArrayList<>();

        Recorder(Context context) {
            super(context);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            actions.add(event.getActionMasked());
            return true;
        }
    }

    private static Recorder recordingChild(X11PaneFrame page) {
        Recorder recorder = new Recorder(page.getContext());
        page.addView(recorder, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        page.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, WIDTH, HEIGHT);
        return recorder;
    }

    private static void touch(X11PaneFrame page, int action, float x, float y) {
        MotionEvent event = event(action, x, y);
        page.dispatchTouchEvent(event);
        event.recycle();
    }

    private static void settle() {
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
    }

    private static void hold(X11PaneFrame page, float x, float y) {
        touch(page, MotionEvent.ACTION_DOWN, x, y);
        ShadowLooper.idleMainLooper(HoldTiming.holdTimeoutMs() + 50L, TimeUnit.MILLISECONDS);
        touch(page, MotionEvent.ACTION_UP, x, y);
        settle();
    }

    /** A tap in a corner square is X's: the child sees the whole gesture and no tab comes out. */
    @Test
    public void aTapInACornerReachesTheDisplayAndOpensNothing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame page = wiredPage(activity);
        Recorder child = recordingChild(page);

        touch(page, MotionEvent.ACTION_DOWN, WIDTH - 2f, 2f);
        touch(page, MotionEvent.ACTION_UP, WIDTH - 2f, 2f);
        settle();

        assertEquals("the child saw the whole tap",
            Arrays.asList(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), child.actions);
        assertFalse("no tab came out", page.isControlsTabShown());
        assertFalse(page.isScaleRailShown());
    }

    /** Held past the timer, the corner takes the gesture and X is told to forget it. */
    @Test
    public void aHoldTakesTheGestureFromTheDisplayAndOpensTheTab() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame page = wiredPage(activity);
        Recorder child = recordingChild(page);

        hold(page, WIDTH - 2f, 2f);

        assertTrue("the tab came out of the held corner", page.isControlsTabShown());
        assertEquals(CornerZones.TOP_RIGHT, page.controlsTab().corner());
        assertTrue("and the rail came with it", page.isScaleRailShown());
        assertEquals("the child saw a cancel, never the lift",
            Arrays.asList(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), child.actions);
    }

    /** Out of the square before the timer: the gesture stays X's and nothing opens. */
    @Test
    public void aDragOutOfTheSquareBeforeTheTimerOpensNothing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame page = wiredPage(activity);
        Recorder child = recordingChild(page);
        float slop = ViewConfiguration.get(activity).getScaledTouchSlop();

        touch(page, MotionEvent.ACTION_DOWN, WIDTH - 2f, 2f);
        touch(page, MotionEvent.ACTION_MOVE, WIDTH - 2f - slop * 4f, 2f);
        ShadowLooper.idleMainLooper(HoldTiming.holdTimeoutMs() + 50L, TimeUnit.MILLISECONDS);
        touch(page, MotionEvent.ACTION_UP, WIDTH - 2f - slop * 4f, 2f);
        settle();

        assertFalse("a drag never brought the tab out", page.isControlsTabShown());
        assertFalse("and X kept the whole gesture",
            child.actions.contains(MotionEvent.ACTION_CANCEL));
    }

    /** The tab's own buttons are tapped, not held: they answer the lift straight away. */
    @Test
    public void theTabsButtonsStillAnswerAPlainTap() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame page = wiredPage(activity);
        List<String> log = new ArrayList<>();
        page.setHost(new X11PaneFrame.Host() {
            @Override public void startDisplay() {}
            @Override public void openSurfaceEditor() { log.add("appearance"); }
        });
        hold(page, WIDTH - 2f, 2f);
        assertTrue(page.isControlsTabShown());

        PaneControlsView tab = page.controlsTab();
        RectF bounds = new RectF();
        tab.tabBounds(bounds);
        float y = bounds.centerY();
        for (float x = bounds.left; x <= bounds.right; x += 1f) {
            if (tab.actionAt(x, y) != X11PaneFrame.ACTION_EDITOR) continue;
            touch(page, MotionEvent.ACTION_DOWN, x, y);
            touch(page, MotionEvent.ACTION_UP, x, y);
            settle();
            assertEquals(Arrays.asList("appearance"), log);
            assertFalse(page.isControlsTabShown());
            return;
        }
        throw new AssertionError("no sliders button on the tab");
    }
}
