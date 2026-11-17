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
}
