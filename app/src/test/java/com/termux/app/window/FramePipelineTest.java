package com.termux.app.window;

import android.app.Application;
import android.os.Build;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The one frame pipeline (ADR 0028 sections 1, 3 and 4): one pre-draw hook runs the table's steps
 * in order, only on frames an input marked dirty; nothing cancels a draw but the window's first
 * frame, which has frame-count and time backstops; a named flush runs the whole table at once.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FramePipelineTest {

    private long now;
    private FramePipeline frames;
    private int chromeRuns;
    private Runnable chromeStep;
    private final List<String> log = new ArrayList<>();

    @Before
    public void setUp() {
        now = 1_000L;
        frames = new FramePipeline(() -> now);
        chromeStep = () -> chromeRuns++;
        frames.bind(FrameStep.CHROME_COMMIT, () -> chromeStep.run());
        frames.setLog((event, detail) -> log.add(event + " " + detail));
    }

    @After
    public void tearDown() {
        Counters.enable(false, null);
        Counters.clear();
    }

    private static ShadowLooper mainLooper() {
        return Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper());
    }

    private static View newRoot() {
        return new View(RuntimeEnvironment.getApplication());
    }

    /** One pre-draw of {@code root}; true when the frame draws (the observer reports a cancel). */
    private static boolean draws(View root) {
        return !root.getViewTreeObserver().dispatchOnPreDraw();
    }

    /** The root's first frame, let through, so later frames are ordinary ones. */
    private static void drawFirstFrame(View root) {
        for (int i = 0; i < FramePipeline.FIRST_FRAME_MAX_HELD + 1; i++) {
            if (draws(root)) return;
        }
    }

    @Test
    public void thePageSettleIsTheTablesFirstStepThenPageFrontGeometryAndTheChromeCommit() {
        assertEquals(0, FrameStep.PAGE_SETTLE.ordinal());
        assertEquals(1, FrameStep.PAGE_FRONT.ordinal());
        assertEquals(2, FrameStep.GEOMETRY.ordinal());
        assertEquals(3, FrameStep.CHROME_COMMIT.ordinal());
    }

    @Test
    public void aStepAskedForTheNextFrameDuringAPreDrawWaitsForTheNextBookedPass() {
        int[] runs = {0};
        frames.bind(FrameStep.PAGE_SETTLE, () -> runs[0]++);
        chromeStep = () -> frames.requestNextFrame(FrameStep.PAGE_SETTLE);
        View root = newRoot();
        frames.attach(root);
        drawFirstFrame(root);
        runs[0] = 0;

        frames.requestFrame(FrameStep.CHROME_COMMIT);
        assertTrue(draws(root));
        assertEquals("not in the frame that asked", 0, runs[0]);

        chromeStep = () -> { };
        frames.runBookedFrameForTest();
        assertEquals("in the next booked pass", 1, runs[0]);
        frames.runBookedFrameForTest();
        assertEquals("once", 1, runs[0]);
    }

    @Test
    public void aStepAskedForTheNextFrameAtRestRunsInTheNextBookedPass() {
        int[] runs = {0};
        frames.bind(FrameStep.PAGE_SETTLE, () -> runs[0]++);
        View root = newRoot();
        frames.attach(root);
        drawFirstFrame(root);
        runs[0] = 0;

        frames.requestNextFrame(FrameStep.PAGE_SETTLE);
        assertEquals(0, runs[0]);
        frames.runBookedFrameForTest();
        assertEquals(1, runs[0]);
    }

    @Test
    public void aStepAskedForTheNextFrameNeverRunsOnceDestroyed() {
        int[] runs = {0};
        frames.bind(FrameStep.PAGE_SETTLE, () -> runs[0]++);
        View root = newRoot();
        frames.attach(root);
        drawFirstFrame(root);
        runs[0] = 0;

        frames.requestNextFrame(FrameStep.PAGE_SETTLE);
        frames.destroy();
        frames.runBookedFrameForTest();
        frames.requestNextFrame(FrameStep.PAGE_SETTLE);
        frames.runBookedFrameForTest();
        assertEquals(0, runs[0]);
    }

    @Test
    public void theGlassCommitIsTheTablesLastStep() {
        assertEquals(FrameStep.TABLE.length - 1, FrameStep.GLASS_COMMIT.ordinal());
    }

    @Test
    public void theGlassCommitRunsAfterTheChromeCommitInOneFrame() {
        List<String> order = new ArrayList<>();
        chromeStep = () -> order.add("chrome");
        frames.bind(FrameStep.GLASS_COMMIT, () -> order.add("glass"));
        View root = newRoot();
        frames.attach(root);
        drawFirstFrame(root);
        order.clear();

        frames.requestFrame(FrameStep.GLASS_COMMIT);
        frames.requestFrame(FrameStep.CHROME_COMMIT);
        assertTrue(draws(root));

        assertEquals(java.util.Arrays.asList("chrome", "glass"), order);
    }

    @Test
    public void anArmedStepRunsOnEveryDrawnFrameAndAsksForNone() {
        int[] glassRuns = {0};
        frames.bind(FrameStep.GLASS_COMMIT, () -> glassRuns[0]++);
        View root = newRoot();
        frames.attach(root);
        drawFirstFrame(root);
        mainLooper().idle();
        glassRuns[0] = 0;
        int chromeBefore = chromeRuns;

        frames.arm(FrameStep.GLASS_COMMIT, true);
        assertFalse("arming asks for no frame", frames.isFrameBooked());
        assertTrue(draws(root));
        assertTrue(draws(root));
        assertEquals(2, glassRuns[0]);
        assertFalse("nothing is booked at rest", frames.isFrameBooked());
        assertEquals("the chrome commit is not run by it", chromeBefore, chromeRuns);

        frames.arm(FrameStep.GLASS_COMMIT, false);
        assertTrue(draws(root));
        assertEquals(2, glassRuns[0]);
    }

    @Test
    public void manyRequestsBeforeAFrameRunTheTableOnce() {
        frames.requestFrame(FrameStep.CHROME_COMMIT);
        frames.requestFrame(FrameStep.CHROME_COMMIT);
        frames.requestFrame(FrameStep.CHROME_COMMIT);
        assertEquals("a request only records and asks for a frame", 0, chromeRuns);

        mainLooper().idle();

        assertEquals(1, chromeRuns);
        mainLooper().idle();
        assertEquals("nothing is left circling", 1, chromeRuns);
    }

    @Test
    public void thePreDrawHookRunsADirtyTableBeforeTheBookedPass() {
        View root = newRoot();
        frames.attach(root);
        drawFirstFrame(root);
        chromeRuns = 0;

        frames.requestFrame(FrameStep.CHROME_COMMIT);
        assertTrue(draws(root));
        assertEquals(1, chromeRuns);

        mainLooper().idle();
        assertEquals("the booked pass finds nothing dirty", 1, chromeRuns);
    }

    @Test
    public void nothingRunsOrIsBookedOnAFrameNoInputMarked() {
        View root = newRoot();
        frames.attach(root);
        drawFirstFrame(root);
        mainLooper().idle();
        chromeRuns = 0;

        assertFalse("nothing is booked at rest", frames.isFrameBooked());
        assertTrue(draws(root));
        assertTrue(draws(root));
        assertEquals(0, chromeRuns);
        assertFalse(frames.isFrameBooked());
    }

    @Test
    public void aStepThatLaysOutAfterTheFirstFrameNeverCancelsTheDraw() {
        View root = newRoot();
        frames.attach(root);
        drawFirstFrame(root);
        chromeStep = root::requestLayout;

        frames.requestFrame(FrameStep.CHROME_COMMIT);

        assertTrue("nothing cancels a draw", draws(root));
    }

    @Test
    public void theFirstFrameRunsTheWholeTableEvenWithNothingAsked() {
        View root = newRoot();
        frames.attach(root);

        assertTrue(draws(root));

        assertEquals(1, chromeRuns);
        assertFalse(frames.isBeforeFirstFrame());
    }

    @Test
    public void theFirstFrameIsHeldWhileTheTableLaysOutUpToTheFrameBackstop() {
        View root = newRoot();
        frames.attach(root);
        root.layout(0, 0, 10, 10);
        chromeStep = () -> {
            chromeRuns++;
            root.requestLayout();
        };

        for (int i = 0; i < FramePipeline.FIRST_FRAME_MAX_HELD; i++) {
            assertFalse("held " + i, draws(root));
            root.layout(0, 0, 10, 10); // the traversal the cancel books lays the root out again
        }
        assertTrue("the frame backstop lets it draw", draws(root));
        assertFalse(frames.isBeforeFirstFrame());
        assertEquals(1, frames.firstFrameBackstops());
        assertTrue(log.toString(), log.get(log.size() - 1).startsWith(FramePipeline.LOG_FIRST_FRAME
            + " backstop"));
    }

    @Test
    public void theFirstFrameHoldEndsOnItsTimeBackstop() {
        View root = newRoot();
        frames.attach(root);
        root.layout(0, 0, 10, 10);
        chromeStep = root::requestLayout;

        assertFalse(draws(root));
        root.layout(0, 0, 10, 10);
        now += FramePipeline.FIRST_FRAME_BACKSTOP_MS;

        assertTrue(draws(root));
        assertEquals(1, frames.firstFrameBackstops());
    }

    @Test
    public void theFirstFrameDrawsAtOnceWhenTheTableSettlesIt() {
        View root = newRoot();
        frames.attach(root);
        root.layout(0, 0, 10, 10);

        assertTrue(draws(root));
        assertEquals(0, frames.firstFrameBackstops());
        assertEquals(FramePipeline.LOG_FIRST_FRAME + " signal", log.get(log.size() - 1));
    }

    @Test
    public void withoutARootThereIsNoFirstFrameToWaitFor() {
        assertFalse(frames.isBeforeFirstFrame());
        frames.attach(newRoot());
        assertTrue(frames.isBeforeFirstFrame());
    }

    @Test
    public void aNamedFlushRunsTheWholeTableNowAndSaysWhoAsked() {
        frames.requestFrame(FrameStep.CHROME_COMMIT);

        frames.flush("editor glass drag");

        assertEquals(1, chromeRuns);
        assertEquals(1, frames.flushCount());
        assertEquals("editor glass drag", frames.lastFlushBy());
        assertEquals(FramePipeline.LOG_FLUSH + " editor glass drag", log.get(log.size() - 1));
        mainLooper().idle();
        assertEquals("the booked pass was the flush", 1, chromeRuns);
    }

    @Test
    public void aStepAskingAgainFromInsideTheTableGetsTheNextFrame() {
        int[] asks = {1};
        chromeStep = () -> {
            chromeRuns++;
            if (asks[0]-- > 0) frames.requestFrame(FrameStep.CHROME_COMMIT);
        };
        frames.requestFrame(FrameStep.CHROME_COMMIT);

        mainLooper().idle();

        assertEquals(2, chromeRuns);
    }

    @Test
    public void aRelaunchHoldKeepsTheFirstFrameBackUntilItsSignal() {
        View root = newRoot();
        frames.attach(root);
        root.layout(0, 0, 10, 10);
        Hold hold = frames.holdFirstFrame("relaunch-reveal", 300L, 40);
        assertNotNull(hold);

        assertFalse("held", draws(root));
        assertFalse("still held", draws(root));
        assertTrue(frames.isBeforeFirstFrame());
        hold.signal();

        assertTrue("drawn once released", draws(root));
        assertFalse(frames.isBeforeFirstFrame());
        assertEquals(0, frames.firstFrameBackstops());
        assertEquals(FramePipeline.LOG_FIRST_FRAME + " signal", log.get(log.size() - 1));
    }

    @Test
    public void aRelaunchHoldNeverOutlastsItsFrameBackstop() {
        View root = newRoot();
        frames.attach(root);
        root.layout(0, 0, 10, 10);
        Hold hold = frames.holdFirstFrame("relaunch-reveal", 300L, 3);

        assertFalse(draws(root));
        assertFalse(draws(root));
        assertFalse(draws(root));
        assertTrue("the fourth frame draws", draws(root));

        assertEquals(Hold.BACKSTOP, hold.releaseReason());
        assertEquals(1, frames.firstFrameBackstops());
        assertEquals(FramePipeline.LOG_FIRST_FRAME + " backstop relaunch", log.get(log.size() - 1));
    }

    @Test
    public void aRelaunchHoldNeverOutlastsItsTimeBackstop() {
        View root = newRoot();
        frames.attach(root);
        root.layout(0, 0, 10, 10);
        Hold hold = frames.holdFirstFrame("relaunch-reveal", 300L, 40);

        assertFalse(draws(root));
        now += 300L;

        assertTrue(draws(root));
        assertEquals(Hold.BACKSTOP, hold.releaseReason());
    }

    @Test
    public void aRelaunchHoldReleasedByItsTimerWithNoFrameAsksForOne() {
        View root = newRoot();
        frames.attach(root);
        Hold hold = frames.holdFirstFrame("relaunch-reveal", 300L, 40);

        mainLooper().idleFor(java.time.Duration.ofMillis(301));

        assertFalse(hold.isOpen());
        assertEquals(Hold.BACKSTOP, hold.releaseReason());
    }

    @Test
    public void thereIsNoFirstFrameToHoldOnceItHasDrawn() {
        View root = newRoot();
        frames.attach(root);
        drawFirstFrame(root);

        assertNull(frames.holdFirstFrame("relaunch-reveal", 300L, 40));
        assertNull("nor without a window", new FramePipeline(() -> now)
            .holdFirstFrame("relaunch-reveal", 300L, 40));
    }

    @Test
    public void aStepThatLaysOutLateInTheFrameIsCountedByTheGuard() {
        Counters.enable(true, null);
        View root = newRoot();
        frames.attach(root);
        drawFirstFrame(root);
        root.layout(0, 0, 10, 10);
        chromeStep = root::requestLayout;

        frames.requestFrame(FrameStep.CHROME_COMMIT);
        assertTrue("never cancels the draw", draws(root));

        assertEquals(1, Counters.countOf("pre-draw-step:CHROME_COMMIT"));
        assertEquals(1, Counters.countOf(FrameGuards.CROSSING_LAID_OUT));
    }

    @Test
    public void aLateStepThatLaysOutNothingIsOnlyCounted() {
        Counters.enable(true, null);
        View root = newRoot();
        frames.attach(root);
        drawFirstFrame(root);
        root.layout(0, 0, 10, 10);

        frames.requestFrame(FrameStep.CHROME_COMMIT);
        draws(root);

        assertEquals(1, Counters.countOf("pre-draw-step:CHROME_COMMIT"));
        assertEquals(0, Counters.countOf(FrameGuards.CROSSING_LAID_OUT));
    }

    @Test
    public void aStepInTheAnimationPhaseIsNotLate() {
        Counters.enable(true, null);
        View root = newRoot();
        frames.attach(root);
        drawFirstFrame(root);
        root.layout(0, 0, 10, 10);
        chromeStep = root::requestLayout;

        frames.requestFrame(FrameStep.CHROME_COMMIT);
        mainLooper().idle();

        assertEquals(0, Counters.countOf("pre-draw-step:CHROME_COMMIT"));
        assertEquals(0, Counters.countOf(FrameGuards.CROSSING_LAID_OUT));
    }

    @Test
    public void aStructureStepAskedAfterTheAnimationPhaseWaitsForTheNextFramesAnimationPhase() {
        Counters.enable(true, null);
        int[] runs = {0};
        View root = newRoot();
        frames.bind(FrameStep.PAGE_FRONT, () -> {
            runs[0]++;
            root.requestLayout();
        });
        frames.attach(root);
        drawFirstFrame(root);
        root.layout(0, 0, 10, 10);
        runs[0] = 0;

        frames.requestFrame(FrameStep.PAGE_FRONT);
        assertTrue("never cancels the draw", draws(root));

        assertEquals("not in this frame's pre-draw", 0, runs[0]);
        assertEquals(0, Counters.countOf(FrameGuards.CROSSING_LAID_OUT));
        mainLooper().idle();
        assertEquals("in the next frame's animation phase", 1, runs[0]);
        assertEquals(0, Counters.countOf(FrameGuards.CROSSING_LAID_OUT));
    }

    @Test
    public void guardsCountNothingWhileTheMotionLogIsOff() {
        View root = newRoot();
        frames.attach(root);
        drawFirstFrame(root);
        root.layout(0, 0, 10, 10);
        chromeStep = root::requestLayout;

        frames.requestFrame(FrameStep.CHROME_COMMIT);
        draws(root);

        assertTrue(Counters.lines().isEmpty());
    }

    @Test
    public void nothingRunsAfterDestroy() {
        View root = newRoot();
        frames.attach(root);
        frames.requestFrame(FrameStep.CHROME_COMMIT);

        frames.destroy();
        root.getViewTreeObserver().dispatchOnPreDraw();
        mainLooper().idle();
        frames.flush("late caller");

        assertEquals(0, chromeRuns);
    }

    /** A root in a real window, so the pipeline books frames on it. */
    private static View attachedRoot() {
        android.app.Activity activity =
            org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        View root = new View(activity);
        activity.setContentView(root);
        return root;
    }

    /**
     * One frame, run directly: the looper's choreographer re-runs a frame that books the next at
     * the same instant, so idling it would never end.
     */
    private void nextFrame() {
        frames.runBookedFrameForTest();
    }

    @Test
    public void aSteppedHoldTicksOncePerFrameUntilItsOwnerIsDone() {
        View root = attachedRoot();
        frames.attach(root);
        Hold hold = Hold.take("x11-swap", 300L, Hold.NO_FRAME_LIMIT, () -> now);
        int[] left = {2};
        frames.stepHold(hold, () -> {
            if (--left[0] > 0) return true;
            hold.signal();
            return false;
        });
        nextFrame();
        assertEquals(1, left[0]);
        assertTrue(hold.isOpen());
        nextFrame();
        assertEquals(Hold.SIGNAL, hold.releaseReason());
        assertEquals(2, hold.heldFrames());
        assertEquals(0, frames.steppedHolds());
        nextFrame();
        assertEquals("nothing ticks once it is done", 0, left[0]);
        assertFalse(frames.isFrameBooked());
    }

    @Test
    public void aSteppedHoldEndsByItsTimeBackstopOnAFrame() {
        View root = attachedRoot();
        frames.attach(root);
        Hold hold = Hold.take("x11-cover", 160L, Hold.NO_FRAME_LIMIT, () -> now);
        int[] steps = {0};
        frames.stepHold(hold, () -> ++steps[0] > 0);
        nextFrame();
        now += 200L;
        nextFrame();
        assertEquals(Hold.BACKSTOP, hold.releaseReason());
        assertEquals("the frame past the backstop asks nothing of the owner", 1, steps[0]);
        assertEquals(0, frames.steppedHolds());
    }

    @Test
    public void aHoldReleasedElsewhereIsDropped() {
        View root = attachedRoot();
        frames.attach(root);
        Hold hold = Hold.take("x11-swap", 300L, Hold.NO_FRAME_LIMIT, () -> now);
        int[] steps = {0};
        frames.stepHold(hold, () -> ++steps[0] > 0);
        nextFrame();
        hold.landing();
        nextFrame();
        assertEquals(1, steps[0]);
        assertEquals(0, frames.steppedHolds());
    }

    @Test
    public void withoutAWindowNothingTicksAndNothingSpins() {
        View root = newRoot();
        frames.attach(root);
        Hold hold = Hold.take("x11-swap", 300L, Hold.NO_FRAME_LIMIT, () -> now);
        int[] steps = {0};
        frames.stepHold(hold, () -> ++steps[0] > 0);
        mainLooper().idle();
        assertEquals(0, steps[0]);
        assertFalse(frames.isFrameBooked());
        assertTrue("the owner's own timer is its backstop", hold.isOpen());
    }

    @Test
    public void destroyEndsSteppedHoldsAsLanded() {
        View root = attachedRoot();
        frames.attach(root);
        Hold hold = Hold.take("x11-swap", 300L, Hold.NO_FRAME_LIMIT, () -> now);
        frames.stepHold(hold, () -> true);
        frames.destroy();
        assertEquals(Hold.LANDING, hold.releaseReason());
        assertEquals(0, frames.steppedHolds());
    }
}
