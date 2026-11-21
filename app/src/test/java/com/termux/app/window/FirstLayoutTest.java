package com.termux.app.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/**
 * The one first-layout helper (ADR 0028 section 3): a wait for a view's own first layout or draw
 * runs once, never cancels a draw and lets go of its listener.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FirstLayoutTest {

    private static View newView() {
        return new View(RuntimeEnvironment.getApplication());
    }

    /** A view on a window, which is where a post reaches the main loop. */
    private static View newAttachedView() {
        android.app.Activity activity = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        View view = new View(activity);
        activity.setContentView(view);
        return view;
    }

    private static boolean draws(View view) {
        return !view.getViewTreeObserver().dispatchOnPreDraw();
    }

    @Test
    public void beforeNextDrawRunsOnceAndNeverCancelsTheDraw() {
        View view = newView();
        int[] runs = {0};
        FirstLayout.Handle handle = FirstLayout.beforeNextDraw(view, () -> runs[0]++);
        assertTrue(handle.isActive());

        assertTrue("the draw goes ahead", draws(view));
        assertTrue(draws(view));
        assertTrue(draws(view));

        assertEquals(1, runs[0]);
        assertFalse(handle.isActive());
    }

    @Test
    public void aCancelledWaitNeverRuns() {
        View view = newView();
        int[] runs = {0};
        FirstLayout.Handle handle = FirstLayout.beforeNextDraw(view, () -> runs[0]++);

        handle.cancel();
        draws(view);

        assertEquals(0, runs[0]);
        assertFalse(handle.isActive());
    }

    @Test
    public void aWaitMayCancelItselfFromInsideItsOwnCallback() {
        View view = newView();
        FirstLayout.Handle[] handle = new FirstLayout.Handle[1];
        int[] runs = {0};
        handle[0] = FirstLayout.beforeNextDraw(view, () -> {
            runs[0]++;
            handle[0].cancel();
        });

        draws(view);
        draws(view);

        assertEquals(1, runs[0]);
    }

    @Test
    public void beforeEachDrawUntilAsksEveryPassUntilItIsTrue() {
        View view = newView();
        int[] asks = {0};
        FirstLayout.Handle handle = FirstLayout.beforeEachDrawUntil(view, () -> ++asks[0] == 3);

        assertTrue(draws(view));
        assertTrue(handle.isActive());
        assertTrue(draws(view));
        assertTrue(draws(view));
        assertFalse(handle.isActive());
        assertTrue(draws(view));

        assertEquals("it stopped asking once true", 3, asks[0]);
    }

    @Test
    public void afterNextDrawRunsOnTheDrawAndLetsGoOnTheNextTurn() {
        View view = newView();
        int[] runs = {0};
        FirstLayout.Handle handle = FirstLayout.afterNextDraw(view, () -> runs[0]++);

        view.getViewTreeObserver().dispatchOnDraw();
        view.getViewTreeObserver().dispatchOnDraw();

        assertEquals("once", 1, runs[0]);
        assertFalse(handle.isActive());
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper()).idle();
        view.getViewTreeObserver().dispatchOnDraw();
        assertEquals(1, runs[0]);
    }

    @Test
    public void afterNextLayoutRunsOnTheLoopAfterTheLayoutNotInsideIt() {
        View view = newAttachedView();
        int[] runs = {0};
        FirstLayout.afterNextLayout(view, 160L, () -> runs[0]++);

        view.layout(0, 0, 10, 10);
        assertEquals("a layout pass is no place to add views", 0, runs[0]);
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper()).idle();

        assertEquals(1, runs[0]);
        view.layout(0, 0, 20, 20);
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper()).idle();
        assertEquals(1, runs[0]);
    }

    @Test
    public void afterNextLayoutRunsOnItsBackstopWhenNoLayoutComes() {
        View view = newAttachedView();
        int[] runs = {0};
        FirstLayout.Handle handle = FirstLayout.afterNextLayout(view, 160L, () -> runs[0]++);

        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper())
            .idleFor(java.time.Duration.ofMillis(161));

        assertEquals(1, runs[0]);
        assertFalse(handle.isActive());
    }

    @Test
    public void aCancelledLayoutWaitNeverRuns() {
        View view = newAttachedView();
        int[] runs = {0};
        FirstLayout.afterNextLayout(view, 160L, () -> runs[0]++).cancel();

        view.layout(0, 0, 10, 10);
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper())
            .idleFor(java.time.Duration.ofMillis(500));

        assertEquals(0, runs[0]);
    }

    @Test
    public void aLayoutWaitTellsItsActionWhichWayItWasReleased() {
        View view = newAttachedView();
        // The window's own first layout comes before the wait starts.
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper()).idle();
        java.util.List<Boolean> ways = new java.util.ArrayList<>();
        FirstLayout.afterNextLayout(view, 160L, ways::add);
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper())
            .idleFor(java.time.Duration.ofMillis(500));
        assertEquals(java.util.Collections.singletonList(Boolean.TRUE), ways);

        View laidOut = newAttachedView();
        java.util.List<Boolean> byLayout = new java.util.ArrayList<>();
        FirstLayout.afterNextLayout(laidOut, 160L, byLayout::add);
        laidOut.layout(0, 0, 10, 10);
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper())
            .idleFor(java.time.Duration.ofMillis(500));
        assertEquals("once, by the layout, and the backstop never runs it again",
            java.util.Collections.singletonList(Boolean.FALSE), byLayout);
    }

    private static final java.time.Duration FRAME = java.time.Duration.ofMillis(20);

    /** Runs at most 10 frames (20 ms each, short of the backstop) until {@code done}; fails if never. */
    private static void framesUntil(String what, java.util.function.BooleanSupplier done) {
        for (int i = 0; i < 10 && !done.getAsBoolean(); i++) {
            Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper()).idleFor(FRAME);
        }
        assertTrue("never happened within 10 frames: " + what, done.getAsBoolean());
    }

    /** An attached view whose window has focus and has drawn its first frame. */
    private static View newFocusedSettledView() {
        View view = newAttachedView();
        // A view that draws something, as a real content view does.
        view.setBackgroundColor(0xff000000);
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper())
            .idleFor(java.time.Duration.ofMillis(60));
        setWindowFocus(view, true);
        assertTrue("the test window has focus", view.hasWindowFocus());
        return view;
    }

    /** Robolectric windows start unfocused; this is the framework's own focus dispatch. */
    private static void setWindowFocus(View view, boolean focused) {
        try {
            Object root = View.class.getMethod("getViewRootImpl").invoke(view);
            java.lang.reflect.Method m = root.getClass()
                .getDeclaredMethod("windowFocusChanged", boolean.class, boolean.class);
            m.setAccessible(true);
            m.invoke(root, focused, false);
            Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper()).idle();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot give the test window focus", e);
        }
    }

    @Test
    public void aReturnWaitIsReleasedBySignalOnAFrameWithNoLayout() {
        View view = newFocusedSettledView();
        java.util.List<Boolean> ways = new java.util.ArrayList<>();
        FirstLayout.afterSettledFrame(view, 160L, ways::add);

        scheduleFrame(view);
        framesUntil("the wait was released", () -> !ways.isEmpty());

        assertEquals(java.util.Collections.singletonList(Boolean.FALSE), ways);
    }

    /**
     * Asks the window for a frame with no layout request. A Robolectric window has no size, so
     * {@code invalidate()} schedules nothing; this is the call it would end in.
     */
    private static void scheduleFrame(View view) {
        try {
            Object root = View.class.getMethod("getViewRootImpl").invoke(view);
            java.lang.reflect.Method m = root.getClass().getDeclaredMethod("scheduleTraversals");
            m.setAccessible(true);
            m.invoke(root);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot schedule a frame", e);
        }
    }

    private static void idle(long ms) {
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper())
            .idleFor(java.time.Duration.ofMillis(ms));
    }

    @Test
    public void aSettledFrameWaitDoesNotReleaseWithoutWindowFocusUntilItComes() {
        View view = newFocusedSettledView();
        setWindowFocus(view, false);
        java.util.List<Boolean> ways = new java.util.ArrayList<>();
        FirstLayout.afterSettledFrame(view, 160L, ways::add);

        scheduleFrame(view);
        idle(60);
        assertTrue("a frame without focus waits", ways.isEmpty());

        setWindowFocus(view, true);
        // On a device the wait's own invalidate() asks for this frame; see scheduleFrame.
        scheduleFrame(view);
        framesUntil("focus gain then a frame released it", () -> !ways.isEmpty());
        assertEquals(java.util.Collections.singletonList(Boolean.FALSE), ways);
    }

    @Test
    public void aSettledFrameWaitSkipsAFrameWithALayoutPending() {
        View view = newFocusedSettledView();
        java.util.List<Boolean> ways = new java.util.ArrayList<>();
        FirstLayout.Handle handle = FirstLayout.afterSettledFrame(view, 160L, ways::add);

        // A pre-draw pass while a layout is requested does not release it.
        view.requestLayout();
        assertTrue(view.isLayoutRequested());
        assertTrue(draws(view));
        assertTrue("a pending layout waits", handle.isActive());

        framesUntil("the settled frame after the layout", () -> !ways.isEmpty());
        assertEquals(java.util.Collections.singletonList(Boolean.FALSE), ways);
    }

    @Test
    public void aSettledFrameWaitRunsOnItsBackstopWhenNoFrameComes() {
        View view = newFocusedSettledView();
        setWindowFocus(view, false);
        java.util.List<Boolean> ways = new java.util.ArrayList<>();
        FirstLayout.Handle handle = FirstLayout.afterSettledFrame(view, 160L, ways::add);

        idle(161);

        assertEquals(java.util.Collections.singletonList(Boolean.TRUE), ways);
        assertFalse(handle.isActive());
        setWindowFocus(view, true);
        idle(100);
        assertEquals("once only", 1, ways.size());
    }

    @Test
    public void aCancelledSettledFrameWaitDropsEverything() {
        View view = newFocusedSettledView();
        java.util.List<Boolean> ways = new java.util.ArrayList<>();
        FirstLayout.afterSettledFrame(view, 160L, ways::add).cancel();

        scheduleFrame(view);
        idle(500);

        assertTrue(ways.isEmpty());
    }

    @Test
    public void aSettledFrameWaitCancelledAfterItsReleaseWasPostedNeverRuns() {
        View view = newFocusedSettledView();
        java.util.List<Boolean> ways = new java.util.ArrayList<>();
        FirstLayout.Handle handle = FirstLayout.afterSettledFrame(view, 160L, ways::add);

        assertTrue(draws(view));
        assertFalse("released by the pre-draw", handle.isActive());
        handle.cancel();
        idle(500);

        assertTrue(ways.isEmpty());
    }
}
