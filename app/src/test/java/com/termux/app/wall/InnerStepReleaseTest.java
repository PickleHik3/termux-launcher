package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import android.app.Activity;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.Duration;
import android.app.Application;

/**
 * A released window swipe costs the release frame only what the next frame draws: the leaving
 * window comes on screen beside the arriving one without a layout pass, and the work its settle
 * does not need (the drawer, the window beyond the arriving one) runs once that settle is over.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
public class InnerStepReleaseTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1800;

    private PaneWallLayout wall;
    private FrameLayout previous;
    private FrameLayout next;
    /** How many times the step's deferred work ran (the nudge's end, as TermuxActivity hooks it). */
    private int deferredRuns;
    private int steps;

    @Before
    public void setUp() {
        FreshVelocityTrackers.drainPool();
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        wall = new PaneWallLayout(activity);
        View terminal = new FrameLayout(activity);
        wall.addView(terminal);
        wall.setReducedMotion(true);
        wall.setPages(PaneWallPolicy.availablePages(true, false, false));
        wall.setPageView(PaneWallPage.TERMINAL, terminal);
        previous = new FrameLayout(activity);
        previous.addView(new View(activity));
        next = new FrameLayout(activity);
        next.addView(new View(activity));
        wall.setInnerNeighbourViews(previous, next, null);
        wall.setListener(new PaneWallLayout.Listener() {
            @Override public boolean hasInnerPage(PaneWallPage page, int s) {
                return page == PaneWallPage.TERMINAL;
            }
            @Override public void onInnerStep(PaneWallPage page, int s, float fromPx) {
                steps = s;
                // What stepWindowFromWall does once the window is shown: the arrival nudge,
                // with the deferred work as its end.
                wall.nudgePage(PaneWallPage.TERMINAL, fromPx, 380L, null, () -> deferredRuns++);
            }
        });
        layOut();
        wall.setReducedMotion(false);
    }

    private void layOut() {
        wall.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        wall.layout(0, 0, WIDTH, HEIGHT);
    }

    /** A drag 600 px toward the next window, laid out as the frames under the finger are. */
    private void dragTowardNext() {
        wall.beginDrag();
        wall.dragTo(-600f);
        layOut();
        assertFalse(wall.isLayoutRequested());
        assertEquals(View.VISIBLE, next.getVisibility());
        assertEquals("the window before is laid out off screen", View.INVISIBLE,
            previous.getVisibility());
    }

    @Test
    public void theLeavingSlotComesOnScreenWithoutALayoutPass() {
        dragTowardNext();
        wall.endDrag(-3000f);
        assertEquals(1, steps);
        // The nudge stands the arriving window 480 px right of rest, so the slot before it (where
        // the leaving window rides) is on screen now.
        assertEquals(View.VISIBLE, previous.getVisibility());
        assertFalse("the slot was laid out already", previous.isLayoutRequested());
        assertFalse("the step asks the wall for no layout pass", wall.isLayoutRequested());
    }

    @Test
    public void theDeferredWorkRunsOnceAfterTheSettleAndNotInTheRelease() {
        dragTowardNext();
        wall.endDrag(-3000f);
        assertEquals("nothing deferred runs in the release call", 0, deferredRuns);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000L));
        assertEquals(1, deferredRuns);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000L));
        assertEquals(1, deferredRuns);
    }

    @Test
    public void aDragCuttingTheSettleShortRunsTheDeferredWorkOnce() {
        dragTowardNext();
        wall.endDrag(-3000f);
        assertEquals(0, deferredRuns);
        wall.beginDrag();
        assertEquals(1, deferredRuns);
        wall.cancelDrag();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000L));
        assertEquals(1, deferredRuns);
    }

    @Test
    public void theHeldSlotIsTheOneTheArrivingWindowCameFromNotTheLeavingOnes() {
        // Toward the next window: the next slot is held, the window leaving rides in the other.
        assertEquals(LiveNeighbourPolicy.slotOf(1), LiveNeighbourPolicy.heldSlotAfterStep(1));
        assertEquals(LiveNeighbourPolicy.slotOf(-1), LiveNeighbourPolicy.heldSlotAfterStep(-1));
        assertNotEquals(LiveNeighbourPolicy.heldSlotAfterStep(1),
            LiveNeighbourPolicy.heldSlotAfterStep(-1));
    }
}
