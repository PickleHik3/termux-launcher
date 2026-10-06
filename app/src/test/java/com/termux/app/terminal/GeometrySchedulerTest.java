package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.terminal.GeometryScheduler.Reason;
import com.termux.app.terminal.GeometryScheduler.ResizePolicy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The counting {@link GeometryScheduler} promises: one pass per frame however many triggers, one
 * grid resize per transition, nothing per fold frame. The host is a fake window with a fake clock:
 * {@link FakeHost#frame} is "the dispatch ended", {@link FakeHost#layout} is "the traversal ran".
 */
public class GeometrySchedulerTest {

    private FakeHost mHost;
    private GeometryScheduler mScheduler;

    @Before
    public void setUp() {
        mHost = new FakeHost();
        mScheduler = new GeometryScheduler(mHost);
    }

    @After
    public void tearDown() {
        mScheduler.dispose();
    }

    @Test
    public void disposeWithdrawsEverythingItPostedAndIgnoresLaterRequests() {
        mScheduler.request(Reason.INSETS, ResizePolicy.NOW);
        GeometryScheduler.Transition fold = mScheduler.begin(Reason.STATUS_FOLD, true, true);
        mScheduler.requestNow(Reason.KEYBOARD, ResizePolicy.NOW);
        assertFalse(mHost.frames.isEmpty() && mHost.afterLayout.isEmpty()
            && mHost.delayed.isEmpty());

        mScheduler.dispose();

        assertTrue("no frame left on the looper", mHost.frames.isEmpty());
        assertTrue("no after-layout check left", mHost.afterLayout.isEmpty());
        assertTrue("the hold's fail-safe withdrawn", mHost.delayed.isEmpty());
        int passes = mHost.passes.size();
        mScheduler.request(Reason.KEYBOARD, ResizePolicy.NOW);
        mScheduler.requestNow(Reason.KEYBOARD, ResizePolicy.NOW);
        mScheduler.settle(fold, true);
        mScheduler.resizeGridAfterLayout();
        mScheduler.begin(Reason.DIVIDER, false, false);
        assertEquals(passes, mHost.passes.size());
        assertTrue(mHost.frames.isEmpty());
        assertTrue(mHost.afterLayout.isEmpty());
        assertTrue(mHost.delayed.isEmpty());
        assertFalse(mScheduler.isGridHeld());
    }

    @Test
    public void aPassThatKeepsAskingForAnotherIsCutOff() {
        mHost.onPass = () -> mScheduler.requestNow(Reason.KEYBOARD, ResizePolicy.NOW);
        mScheduler.requestNow(Reason.STYLING, ResizePolicy.NONE);
        for (int i = 0; i < 20 && !mHost.frames.isEmpty(); i++) mHost.frame();

        assertTrue(mHost.frames.isEmpty());
        assertEquals("the first pass and the chained ones allowed",
            1 + GeometryScheduler.MAX_CHAINED_PASSES, mHost.passes.size());
        mHost.onPass = null;
        mHost.layout();
        assertFalse("the grid is let go", mScheduler.isGridHeld());
    }

    @Test
    public void requestsInOneFrameShareOnePass() {
        mScheduler.request(Reason.KEYBOARD, ResizePolicy.NONE);
        mScheduler.request(Reason.INSETS, ResizePolicy.NOW);
        mScheduler.request(Reason.LAYOUT, ResizePolicy.NONE);
        mScheduler.request(Reason.METRICS, ResizePolicy.NONE);
        mScheduler.request(Reason.KEYBOARD, ResizePolicy.NOW);

        assertEquals("one frame booked for all of them", 1, mHost.frames.size());
        assertEquals(0, mHost.passes.size());

        mHost.frame();

        assertEquals(1, mHost.passes.size());
        assertEquals("the first real trigger names the pass", Reason.KEYBOARD, mHost.passes.get(0));
    }

    @Test
    public void keyboardShowRunsOnePassAndReflowsOnce() {
        mScheduler.requestNow(Reason.KEYBOARD, ResizePolicy.NOW);

        assertEquals("the reveal gate's traversal must see the new stack", 1, mHost.passes.size());
        assertEquals(1, mHost.holdsBegun);
        assertTrue(mScheduler.isGridHeld());

        // The traversal the pass booked: the pane host shrinks and says so. Nothing the pass reads
        // moved, so it is the pass's own echo.
        mScheduler.request(Reason.LAYOUT, ResizePolicy.NONE);
        mHost.layout();

        assertEquals(1, mHost.passes.size());
        assertEquals("the grid resizes once, after the pass's layout", 1, mHost.holdsFinished);
        assertFalse(mScheduler.isGridHeld());

        // The reflow it released reports its new rows: the same facts again.
        mScheduler.request(Reason.METRICS, ResizePolicy.NONE);
        mHost.layout();

        assertEquals(1, mHost.passes.size());
        assertEquals(1, mHost.holdsBegun);
        assertEquals(1, mHost.holdsFinished);
    }

    @Test
    public void anEchoWhoseFactsMovedRunsAPass() {
        mScheduler.requestNow(Reason.INSETS, ResizePolicy.NOW);
        mHost.key = 42L;

        mScheduler.request(Reason.LAYOUT, ResizePolicy.NONE);
        mHost.layout();

        assertEquals(2, mHost.passes.size());
        assertEquals(Reason.LAYOUT, mHost.passes.get(1));
        assertEquals("still one resize: the echo's pass ran under the same hold", 1,
            mHost.holdsFinished);
    }

    @Test
    public void foldFramesRunNoPassUntilSettleAndOneAtSettle() {
        mScheduler.requestNow(Reason.STYLING, ResizePolicy.NONE);
        int before = mHost.passes.size();

        GeometryScheduler.Transition fold = mScheduler.begin(Reason.STATUS_FOLD, true, true);
        assertEquals(1, mHost.displayBegun);
        for (int tick = 0; tick < 12; tick++) {
            // Each frame of the fold moves the terminal's top and relayouts the pane host.
            mHost.key++;
            mScheduler.request(Reason.LAYOUT, ResizePolicy.NONE);
            mScheduler.request(Reason.METRICS, ResizePolicy.NONE);
            mHost.layout();
            mHost.advance(16);
        }

        assertEquals("no pass while the fold runs", before, mHost.passes.size());
        assertEquals(0, mHost.holdsFinished);

        mScheduler.settle(fold, true);
        mHost.frame();
        assertEquals("not before the layout the fold ended on", before, mHost.passes.size());

        // The last frame's traversal: the pass runs behind it, reading the bar's last height.
        mHost.layout();

        assertEquals("one pass at settle", before + 1, mHost.passes.size());
        assertEquals(Reason.STATUS_FOLD, mHost.passes.get(before));
        assertEquals("the grid waits for the settle pass's own layout", 0, mHost.holdsFinished);

        mHost.layout();

        assertEquals(before + 1, mHost.passes.size());
        assertEquals(1, mHost.holdsFinished);
        assertEquals(1, mHost.displayFinished);
    }

    @Test
    public void aBatchRunsItsRequestsAsOnePassAtItsEnd() {
        mScheduler.beginBatch();
        mScheduler.requestNow(Reason.KEYBOARD, ResizePolicy.NOW);
        mScheduler.requestNow(Reason.KEYBOARD, ResizePolicy.NOW);
        mScheduler.requestNow(Reason.PLACE_SETTLE, ResizePolicy.NOW);
        assertEquals(0, mHost.passes.size());

        mScheduler.endBatch();

        assertEquals("ran before endBatch returned", 1, mHost.passes.size());
        mHost.layout();
        assertEquals(1, mHost.holdsFinished);
    }

    @Test
    public void aSlideHoldsTheGridThroughItsPassesUntilSettle() {
        GeometryScheduler.Transition slide = mScheduler.begin(Reason.PLACE_TRAVEL, false, false);
        mScheduler.requestNow(Reason.PLACE_TRAVEL, ResizePolicy.AT_SETTLE);
        mHost.layout();
        // A keyboard pre-rolled mid-slide: a pass of its own, and still no resize.
        mScheduler.requestNow(Reason.KEYBOARD, ResizePolicy.NOW);
        mHost.layout();

        assertEquals(2, mHost.passes.size());
        assertEquals(0, mHost.holdsFinished);

        mScheduler.beginBatch();
        mScheduler.requestNow(Reason.PLACE_SETTLE, ResizePolicy.NOW);
        mScheduler.endBatch();
        mScheduler.settle(slide, false);

        assertEquals(3, mHost.passes.size());
        assertEquals(0, mHost.holdsFinished);
        mHost.layout();
        assertEquals("one resize for the whole slide", 1, mHost.holdsFinished);
        assertEquals(1, mHost.holdsBegun);
    }

    @Test
    public void aDividerReleaseResizesAfterLayoutWithoutAPass() {
        GeometryScheduler.Transition drag = mScheduler.begin(Reason.DIVIDER, false, false);
        mHost.layout();
        mHost.layout();
        assertEquals(0, mHost.holdsFinished);

        mScheduler.settle(drag, false);
        mScheduler.settle(drag, false);
        assertEquals("not before the release's own layout", 0, mHost.holdsFinished);

        mHost.layout();

        assertEquals(0, mHost.passes.size());
        assertEquals(1, mHost.holdsFinished);
    }

    @Test
    public void aCheckPostedBeforeAPassIsMovedBehindThatPassesLayout() {
        GeometryScheduler.Transition drag = mScheduler.begin(Reason.DIVIDER, false, false);
        mScheduler.settle(drag, false);
        // Before the queued check runs, something books a pass: the check must not release the
        // grid ahead of that pass's layout.
        mScheduler.request(Reason.KEYBOARD, ResizePolicy.NOW);
        mHost.frame();
        assertEquals(1, mHost.passes.size());
        // On a looper the earlier check sits ahead of the barrier the pass's layout books; it is
        // taken out and posted again behind it.
        assertEquals(1, mHost.afterLayoutWithdrawn);
        assertEquals(1, mHost.afterLayout.size());
        assertEquals(0, mHost.holdsFinished);

        mHost.layout();

        assertEquals(1, mHost.holdsFinished);
    }

    @Test
    public void aHoldWhosePassNeverGetsAFrameIsReleasedByTheBackstop() {
        mScheduler.request(Reason.INSETS, ResizePolicy.NOW);
        assertTrue(mScheduler.isGridHeld());

        // The window stopped drawing: neither the frame nor any layout comes.
        mHost.advance(GeometryScheduler.HOLD_BACKSTOP_MS - 1);
        assertTrue(mScheduler.isGridHeld());
        mHost.advance(1);

        assertFalse(mScheduler.isGridHeld());
        assertEquals("the booked pass still ran", 1, mHost.passes.size());
        assertEquals(1, mHost.holdsFinished);
    }

    @Test
    public void aRequestDuringAPassFollowsItInsteadOfRecursing() {
        mHost.onPass = () -> mScheduler.requestNow(Reason.KEYBOARD, ResizePolicy.NOW);
        mScheduler.requestNow(Reason.STYLING, ResizePolicy.NONE);
        mHost.onPass = null;

        assertEquals(1, mHost.passes.size());
        assertEquals(1, mHost.frames.size());
        mHost.frame();
        assertEquals(2, mHost.passes.size());
    }

    // ------------------------------------------------------------------ fake window

    private static final class FakeHost implements GeometryScheduler.Host {

        private static final class Delayed {
            final long at;
            final Runnable runnable;

            Delayed(long at, Runnable runnable) {
                this.at = at;
                this.runnable = runnable;
            }
        }

        long now;
        long key = 7L;
        boolean alive = true;
        Runnable onPass;
        final List<Runnable> frames = new ArrayList<>();
        final List<Runnable> afterLayout = new ArrayList<>();
        final List<Delayed> delayed = new ArrayList<>();
        final List<Reason> passes = new ArrayList<>();
        int holdsBegun;
        int holdsFinished;
        int displayBegun;
        int displayFinished;
        int afterLayoutWithdrawn;

        @Override public void postFrame(@NonNull Runnable frame) {
            frames.add(frame);
        }

        @Override public void postAfterLayout(@NonNull Runnable runnable) {
            afterLayout.add(runnable);
        }

        @Override public void postDelayed(@NonNull Runnable runnable, long delayMs) {
            delayed.add(new Delayed(now + delayMs, runnable));
        }

        @Override public void removeCallbacks(@NonNull Runnable runnable) {
            frames.removeIf(r -> r == runnable);
            if (afterLayout.removeIf(r -> r == runnable)) afterLayoutWithdrawn++;
            delayed.removeIf(d -> d.runnable == runnable);
        }

        @Override public void runPass(@NonNull Reason reason) {
            passes.add(reason);
            if (onPass != null) onPass.run();
        }

        @Override public void beginGridHold() {
            holdsBegun++;
        }

        @Override public void finishGridHold() {
            holdsFinished++;
        }

        @Override public void beginDisplayHold() {
            displayBegun++;
        }

        @Override public void finishDisplayHold() {
            displayFinished++;
        }

        @Override public long layoutInputsKey() {
            return key;
        }

        @Override public boolean isAlive() {
            return alive;
        }

        /** The current dispatch ends: what was booked for it runs. */
        void frame() {
            while (!frames.isEmpty()) frames.remove(0).run();
        }

        /** The frame's traversal runs, then whatever waited behind it. */
        void layout() {
            frame();
            List<Runnable> ready = new ArrayList<>(afterLayout);
            afterLayout.clear();
            for (Runnable runnable : ready) runnable.run();
        }

        void advance(long ms) {
            now += ms;
            List<Delayed> due = new ArrayList<>();
            for (Delayed d : delayed) if (d.at <= now) due.add(d);
            delayed.removeAll(due);
            for (Delayed d : due) d.runnable.run();
        }
    }
}
