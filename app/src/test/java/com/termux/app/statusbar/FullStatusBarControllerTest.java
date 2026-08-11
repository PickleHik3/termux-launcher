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
