package com.termux.app.statusbar;

import org.junit.Test;

import java.util.ArrayDeque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FullStatusBarControllerTest {
    @Test public void eachSettleHasExactlyOneFinalResizeAndNonePerFrame() {
        FakeHost host = new FakeHost();
        FakeFrames frames = new FakeFrames();
        FullStatusBarController controller = new FullStatusBarController(host, frames);
        Object spring = controller.springForTests();

        assertTrue(controller.open(TopStatusBarState.EXPANDED));
        assertEquals(1, host.beginCount);
        assertEquals(0, host.finishCount);
        frames.runOne();
        assertTrue(host.frameCount > 0);
        assertEquals("no frame may finish/update the terminal", 0, host.finishCount);
        frames.runToIdle();
        assertEquals(1, host.finishCount);
        assertEquals(600, host.height);

        assertTrue(controller.onBackPressed());
        assertTrue(controller.onBackPressed());
        assertEquals(2, host.beginCount);
        frames.runOne();
        assertEquals(1, host.finishCount);
        frames.runToIdle();
        assertEquals(2, host.finishCount);
        assertEquals(96, host.height);
        assertFalse(controller.isEngaged());
        assertSame("the controller owns exactly one Spring", spring, controller.springForTests());
    }

    @Test public void takeoverRelayoutReducedMotionAndImmediateStop() {
        FakeHost host = new FakeHost();
        host.height = 70; // halfway through the old 32->96 animator: no snap to 96.
        host.snapOnCancelUnlessEngaged = true;
        FakeFrames frames = new FakeFrames();
        FullStatusBarController controller = new FullStatusBarController(host, frames);
        controller.open(TopStatusBarState.EXPANDED);
        assertEquals("reentrant animator cancellation must not snap", 70, host.height);
        frames.runOne();
        assertTrue(host.height >= 70);
        host.parentHeight = 700;
        controller.onParentLayoutChanged();
        frames.runToIdle();
        assertEquals(700, host.height);
        controller.closeImmediateToPrior();
        assertEquals(96, host.height);

        FakeHost reduced = new FakeHost();
        reduced.reduced = true;
        FullStatusBarController snapped = new FullStatusBarController(reduced, new FakeFrames());
        snapped.open(TopStatusBarState.COMPACT);
        assertEquals(600, reduced.height);
        assertEquals(1, reduced.finishCount);
    }

    @Test public void dragTracksFingerCommitsPastThresholdAndOpensFull() {
        FakeHost host = new FakeHost();
        FakeFrames frames = new FakeFrames();
        FullStatusBarController controller = new FullStatusBarController(host, frames);

        assertTrue(controller.dragBegin(TopStatusBarState.EXPANDED));
        assertTrue(controller.isEngaged());
        assertSame(FullStatusBarController.Motion.DRAGGING, controller.motion());
        controller.dragUpdate(100f);
        assertEquals(196, host.height);
        controller.dragUpdate(300f);
        assertEquals(396, host.height);
        // 300/504 travel is past the 0.35 threshold: release commits to FULL.
        controller.dragEnd(0f);
        assertSame(FullStatusBarController.Motion.OPENING, controller.motion());
        frames.runToIdle();
        assertSame(FullStatusBarController.Motion.FULL, controller.motion());
        assertEquals(600, host.height);
        assertEquals(1, host.finishCount);
    }

    @Test public void shortDragSpringsBackToPriorForm() {
        FakeHost host = new FakeHost();
        host.height = 32;
        FakeFrames frames = new FakeFrames();
        FullStatusBarController controller = new FullStatusBarController(host, frames);

        assertTrue(controller.dragBegin(TopStatusBarState.COMPACT));
        controller.dragUpdate(10f);
        assertEquals(42, host.height);
        // 10/568 travel, below half the way to the expanded form, no fling: release springs
        // back to the captured COMPACT form.
        controller.dragEnd(0f);
        assertSame(FullStatusBarController.Motion.CLOSING, controller.motion());
        frames.runToIdle();
        assertFalse(controller.isEngaged());
        assertEquals(32, host.height);
        assertFalse(host.engaged);
    }

    @Test public void compactDragReleasedNearTheExpandedFormRestsThere() {
        FakeHost host = new FakeHost();
        host.height = 32;
        FakeFrames frames = new FakeFrames();
        FullStatusBarController controller = new FullStatusBarController(host, frames);

        assertTrue(controller.dragBegin(TopStatusBarState.COMPACT));
        controller.dragUpdate(60f);
        assertEquals(92, host.height);
        // Past half the way to the expanded form (96) but short of the FULL commit line:
        // the release adopts EXPANDED as the resting form and persists it as the prior.
        controller.dragEnd(0f);
        assertSame(FullStatusBarController.Motion.CLOSING, controller.motion());
        frames.runToIdle();
        assertFalse(controller.isEngaged());
        assertEquals(96, host.height);
        assertSame(TopStatusBarState.EXPANDED, controller.priorState());
    }

    @Test public void compactDragFlungDownCommitsFullFromAnyProgress() {
        FakeHost host = new FakeHost();
        host.height = 32;
        FakeFrames frames = new FakeFrames();
        FullStatusBarController controller = new FullStatusBarController(host, frames);

        assertTrue(controller.dragBegin(TopStatusBarState.COMPACT));
        controller.dragUpdate(60f);
        controller.dragEnd(5000f);
        assertSame(FullStatusBarController.Motion.OPENING, controller.motion());
        frames.runToIdle();
        assertSame(FullStatusBarController.Motion.FULL, controller.motion());
        assertEquals(600, host.height);
        // A fling straight to FULL keeps the captured COMPACT prior for the eventual close.
        assertSame(TopStatusBarState.COMPACT, controller.priorState());
    }

    @Test public void fastFlingCommitsRegardlessOfProgressAndDragClampsToBounds() {
        FakeHost host = new FakeHost();
        FakeFrames frames = new FakeFrames();
        FullStatusBarController controller = new FullStatusBarController(host, frames);

        assertTrue(controller.dragBegin(TopStatusBarState.EXPANDED));
        controller.dragUpdate(-50f);
        assertEquals("drag can never shrink below the prior form", 96, host.height);
        controller.dragUpdate(9999f);
        assertEquals("drag can never overshoot FULL", 600, host.height);
        controller.dragUpdate(40f);
        // 40/504 travel but a hard downward fling: commits anyway.
        controller.dragEnd(5000f);
        assertSame(FullStatusBarController.Motion.OPENING, controller.motion());
        frames.runToIdle();
        assertSame(FullStatusBarController.Motion.FULL, controller.motion());

        assertFalse("second dragBegin while engaged must be refused",
            controller.dragBegin(TopStatusBarState.EXPANDED));
    }

    @Test public void closeDragFromFullCollapsesPastThresholdOrSpringsBackToFull() {
        FakeHost host = new FakeHost();
        FakeFrames frames = new FakeFrames();
        FullStatusBarController controller = new FullStatusBarController(host, frames);
        assertTrue(controller.open(TopStatusBarState.EXPANDED));
        frames.runToIdle();
        assertSame(FullStatusBarController.Motion.FULL, controller.motion());

        assertTrue(controller.dragBeginClose());
        controller.dragUpdate(-100f);
        assertEquals(500, host.height);
        // Still above the 0.35 commit line: release springs back to FULL.
        controller.dragEnd(0f);
        frames.runToIdle();
        assertSame(FullStatusBarController.Motion.FULL, controller.motion());
        assertEquals(600, host.height);

        assertTrue(controller.dragBeginClose());
        controller.dragUpdate(-450f);
        assertEquals(150, host.height);
        // 54/504 progress: release collapses to the captured prior form.
        controller.dragEnd(0f);
        frames.runToIdle();
        assertFalse(controller.isEngaged());
        assertEquals(96, host.height);

        assertFalse("close drag needs a settled FULL", controller.dragBeginClose());
    }

    @Test public void closeDragUpFlingCollapsesRegardlessOfProgress() {
        FakeHost host = new FakeHost();
        FakeFrames frames = new FakeFrames();
        FullStatusBarController controller = new FullStatusBarController(host, frames);
        assertTrue(controller.open(TopStatusBarState.COMPACT));
        frames.runToIdle();
        assertTrue(controller.dragBeginClose());
        controller.dragUpdate(-40f);
        controller.dragEnd(-5000f);
        frames.runToIdle();
        assertFalse(controller.isEngaged());
        assertEquals(32, host.height);
    }

    @Test public void dragCancelAlwaysSpringsBack() {
        FakeHost host = new FakeHost();
        FakeFrames frames = new FakeFrames();
        FullStatusBarController controller = new FullStatusBarController(host, frames);
        assertTrue(controller.dragBegin(TopStatusBarState.EXPANDED));
        controller.dragUpdate(400f);
        controller.dragCancel();
        frames.runToIdle();
        assertFalse(controller.isEngaged());
        assertEquals(96, host.height);
    }

    private static final class FakeHost implements FullStatusBarController.Host {
        int height = 96;
        int parentHeight = 600;
        int beginCount;
        int finishCount;
        int frameCount;
        boolean reduced;
        boolean engaged;
        boolean snapOnCancelUnlessEngaged;
        @Override public int currentHeight() { return height; }
        @Override public int normalHeight(TopStatusBarState state) {
            return state == TopStatusBarState.COMPACT ? 32 : 96;
        }
        @Override public int parentMeasuredHeight() { return parentHeight; }
        @Override public int parentPaddingTop() { return 0; }
        @Override public int parentPaddingBottom() { return 0; }
        @Override public int hostTopMargin() { return 0; }
        @Override public boolean reducedMotion() { return reduced; }
        @Override public void cancelNormalAnimatorKeepingCurrent() {
            if (snapOnCancelUnlessEngaged && !engaged) height = 96;
        }
        @Override public void beginTerminalResize() { beginCount++; }
        @Override public void applyFrame(int value, float progress) { height = value; frameCount++; }
        @Override public void finishTerminalResizeAfterLayout() { finishCount++; }
        @Override public void applyNormalState(TopStatusBarState state) { height = normalHeight(state); }
        @Override public void onEngagementChanged(boolean engaged, TopStatusBarState target) {
            this.engaged = engaged;
        }
    }

    private static final class FakeFrames implements FullStatusBarController.FrameScheduler {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        long nanos;
        @Override public void post(Runnable frame) { if (!queue.contains(frame)) queue.add(frame); }
        @Override public void remove(Runnable frame) { queue.remove(frame); }
        @Override public long nowNanos() { return nanos; }
        void runOne() {
            Runnable next = queue.poll();
            if (next != null) { nanos += 16_666_667L; next.run(); }
        }
        void runToIdle() {
            int guard = 1000;
            while (!queue.isEmpty() && guard-- > 0) runOne();
            assertTrue("spring did not settle", guard > 0);
        }
    }
}
