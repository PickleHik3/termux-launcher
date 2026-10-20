package com.termux.app.chrome;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** A dock icon's light: on at once, still under a resting finger, off in four bounded steps. */
public class DockTouchFadeTest {

    private static final float EPS = 1e-6f;

    /** A queue the test runs by hand: one pending step at most, as the fade schedules them. */
    private static final class ManualScheduler implements DockTouchFade.Scheduler {
        Runnable pending;
        long delayMs;
        int cancels;

        @Override public void postDelayed(@NonNull Runnable step, long delayMs) {
            this.pending = step;
            this.delayMs = delayMs;
        }

        @Override public void cancel(@NonNull Runnable step) {
            if (pending == step) pending = null;
            cancels++;
        }

        void run() {
            Runnable step = pending;
            pending = null;
            step.run();
        }
    }

    /** Every rebuild the fade asked for: an intensity, or -1 for a clear. */
    private static final class RecordingSink implements DockTouchFade.Sink {
        final List<Float> rebuilds = new ArrayList<>();
        float x, y;

        @Override public void light(float x, float y, float intensity) {
            this.x = x;
            this.y = y;
            rebuilds.add(intensity);
        }

        @Override public void clear() {
            rebuilds.add(-1f);
        }
    }

    private ManualScheduler mScheduler;
    private RecordingSink mSink;
    private boolean mAllowed;
    private DockTouchFade mFade;

    @Before
    public void setUp() {
        mScheduler = new ManualScheduler();
        mSink = new RecordingSink();
        mAllowed = true;
        mFade = new DockTouchFade(mSink, mScheduler, () -> mAllowed);
    }

    @Test
    public void downLightsAtFullStrengthAtOnceWithOneRebuild() {
        mFade.down(40f, 12f);

        assertEquals(1, mSink.rebuilds.size());
        assertEquals(1f, mSink.rebuilds.get(0), EPS);
        assertEquals(40f, mSink.x, EPS);
        assertEquals(12f, mSink.y, EPS);
        assertNull("nothing scheduled while the finger rests", mScheduler.pending);
        assertTrue(mFade.isLit());
    }

    @Test
    public void upFallsInFourStepsSixtyMillisecondsApartThenNothing() {
        mFade.down(40f, 12f);
        mFade.up();

        assertEquals("the lift itself rebuilds nothing", 1, mSink.rebuilds.size());
        float[] expected = {0.75f, 0.5f, 0.25f, -1f};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(DockTouchFade.STEP_MS, mScheduler.delayMs);
            mScheduler.run();
            assertEquals(expected[i], mSink.rebuilds.get(i + 1), EPS);
        }
        assertNull("idle after the last step", mScheduler.pending);
        assertEquals("five rebuilds a tap", 1 + DockTouchFade.STEP_COUNT, mSink.rebuilds.size());
        assertFalse(mFade.isLit());
        assertEquals(40f, mSink.x, EPS);
        assertEquals(12f, mSink.y, EPS);
    }

    @Test
    public void newDownCancelsTheFallAndLightsAgain() {
        mFade.down(40f, 12f);
        mFade.up();
        mScheduler.run();   // 0.75
        mFade.down(90f, 14f);

        assertNull("the fall is cancelled", mScheduler.pending);
        assertEquals(1f, mSink.rebuilds.get(mSink.rebuilds.size() - 1), EPS);
        assertEquals(90f, mSink.x, EPS);

        mFade.up();
        for (int i = 0; i < DockTouchFade.STEP_COUNT; i++) mScheduler.run();
        assertEquals(-1f, mSink.rebuilds.get(mSink.rebuilds.size() - 1), EPS);
        assertNull(mScheduler.pending);
    }

    @Test
    public void repeatedUpDoesNotRestartTheFall() {
        mFade.down(40f, 12f);
        mFade.up();
        mScheduler.run();   // 0.75
        mFade.up();

        mScheduler.run();
        assertEquals(0.5f, mSink.rebuilds.get(mSink.rebuilds.size() - 1), EPS);
    }

    @Test
    public void upWithoutALightDoesNothing() {
        mFade.up();

        assertTrue(mSink.rebuilds.isEmpty());
        assertNull(mScheduler.pending);
    }

    @Test
    public void stoppedMotionNeverCallsTheSink() {
        mAllowed = false;
        mFade.down(40f, 12f);
        mFade.up();

        assertTrue(mSink.rebuilds.isEmpty());
        assertNull(mScheduler.pending);
    }

    @Test
    public void motionStoppedWhileLitClearsOnTheNextDown() {
        mFade.down(40f, 12f);
        mAllowed = false;
        mFade.down(50f, 12f);

        assertEquals(-1f, mSink.rebuilds.get(mSink.rebuilds.size() - 1), EPS);
        assertFalse(mFade.isLit());
    }

    @Test
    public void resetClearsAtOnceAndCancelsTheFall() {
        mFade.down(40f, 12f);
        mFade.up();
        mFade.reset();

        assertNull(mScheduler.pending);
        assertEquals(-1f, mSink.rebuilds.get(mSink.rebuilds.size() - 1), EPS);
        assertEquals(2, mSink.rebuilds.size());

        mFade.reset();
        assertEquals("a second reset rebuilds nothing", 2, mSink.rebuilds.size());
    }
}
