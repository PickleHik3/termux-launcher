package com.termux.app.chrome;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** A finger's light on glass: rises quickly, holds and follows, falls gently, never jumps. */
public class GlassTouchLightTest {

    private static final float RADIUS = 168f;
    private static final float EPS = 1e-4f;

    /** A ticker the test steps by hand: one run at a time, as the animator plays it. */
    private static final class ManualTicker implements GlassTouchLight.Ticker {
        GlassTouchLight.Frames frames;
        long durationMs;
        int starts;

        @Override public void start(long durationMs, @NonNull GlassTouchLight.Frames frames) {
            this.frames = frames;
            this.durationMs = durationMs;
            starts++;
        }

        @Override public void cancel() {
            frames = null;
        }

        void frame(float fraction) {
            assertNotNull("a run is playing", frames);
            frames.onFraction(fraction);
        }

        void finish() {
            GlassTouchLight.Frames f = frames;
            assertNotNull("a run is playing", f);
            f.onFraction(1f);
            frames = null;
            f.onEnd();
        }
    }

    private static final class RecordingSink implements GlassTouchLight.Sink {
        final List<float[]> touches = new ArrayList<>();
        int clears;

        @Override public void touch(float x, float y, float radiusPx, float intensity) {
            touches.add(new float[]{x, y, radiusPx, intensity});
        }

        @Override public void clear() {
            clears++;
        }

        int calls() {
            return touches.size() + clears;
        }

        float[] last() {
            return touches.get(touches.size() - 1);
        }
    }

    private ManualTicker mTicker;
    private RecordingSink mSink;
    private boolean mAllowed;
    private GlassTouchLight mLight;

    @Before
    public void setUp() {
        mTicker = new ManualTicker();
        mSink = new RecordingSink();
        mAllowed = true;
        mLight = new GlassTouchLight(mSink, RADIUS, () -> mAllowed, mTicker);
    }

    @Test
    public void downRisesQuicklyAndDecelerates() {
        mLight.down(40f, 20f);
        assertEquals(GlassTouchLight.RISE_MS, mTicker.durationMs);
        assertTrue(mLight.isHeld());

        mTicker.frame(0f);
        assertEquals(0f, mSink.last()[3], EPS);
        mTicker.frame(0.5f);
        // Decelerating: three quarters of the way at half the time.
        assertEquals(0.75f, mSink.last()[3], EPS);
        assertEquals(40f, mSink.last()[0], 0f);
        assertEquals(20f, mSink.last()[1], 0f);
        assertEquals(RADIUS, mSink.last()[2], 0f);

        mTicker.finish();
        assertEquals(1f, mSink.last()[3], EPS);
        assertEquals(1f, mLight.intensity(), EPS);
        assertEquals(0, mSink.clears);
    }

    @Test
    public void moveRestatesThePositionAtTheCurrentStrength() {
        mLight.down(10f, 10f);
        mTicker.finish();
        int before = mSink.touches.size();

        mLight.move(30f, 12f);
        assertEquals(before + 1, mSink.touches.size());
        float[] last = mSink.last();
        assertEquals(30f, last[0], 0f);
        assertEquals(12f, last[1], 0f);
        assertEquals(1f, last[3], EPS);

        // Mid-rise, a move restates at whatever strength the rise has reached.
        mLight.up();
        mTicker.finish();
        mLight.down(5f, 5f);
        mTicker.frame(0.5f);
        mLight.move(6f, 7f);
        assertEquals(0.75f, mSink.last()[3], EPS);
        assertEquals(6f, mSink.last()[0], 0f);
        // The next frame keeps the moved position.
        mTicker.frame(0.6f);
        assertEquals(6f, mSink.last()[0], 0f);
        assertEquals(7f, mSink.last()[1], 0f);
    }

    @Test
    public void aMoveWithoutAFingerDoesNothing() {
        mLight.move(3f, 4f);
        assertEquals(0, mSink.calls());
    }

    @Test
    public void upFallsGentlyThenClears() {
        mLight.down(50f, 50f);
        mTicker.finish();

        mLight.up();
        assertFalse(mLight.isHeld());
        assertEquals(GlassTouchLight.FALL_MS, mTicker.durationMs);
        mTicker.frame(0.1f);
        // Eased in: barely dimmer after the first tenth.
        float early = mSink.last()[3];
        assertTrue("gentle start, was " + early, early > 0.95f);
        mTicker.frame(0.5f);
        assertEquals(0.5f, mSink.last()[3], EPS);
        assertEquals(0, mSink.clears);

        mTicker.finish();
        assertEquals(1, mSink.clears);
        assertEquals(0f, mLight.intensity(), EPS);
        assertNull(mTicker.frames);
    }

    @Test
    public void aDownDuringTheFallRisesFromWhereTheFallWas() {
        mLight.down(50f, 50f);
        mTicker.finish();
        mLight.up();
        mTicker.frame(0.5f);
        assertEquals(0.5f, mLight.intensity(), EPS);
        int clears = mSink.clears;

        mLight.down(60f, 55f);
        // No jump: the new position at the strength the fall had reached, at once.
        float[] at = mSink.last();
        assertEquals(60f, at[0], 0f);
        assertEquals(55f, at[1], 0f);
        assertEquals(0.5f, at[3], EPS);
        assertEquals(clears, mSink.clears);
        // The rest of the rise takes the share of its time the distance left needs.
        assertEquals(Math.round(GlassTouchLight.RISE_MS * 0.5f), mTicker.durationMs);
        mTicker.frame(0f);
        assertEquals(0.5f, mSink.last()[3], EPS);
        mTicker.finish();
        assertEquals(1f, mSink.last()[3], EPS);
        assertEquals(clears, mSink.clears);
    }

    @Test
    public void aReleaseDuringTheRiseFallsFromWhereTheRiseWas() {
        mLight.down(0f, 0f);
        mTicker.frame(0.5f);  // 0.75
        mLight.up();
        assertEquals(Math.round(GlassTouchLight.FALL_MS * 0.75f), mTicker.durationMs);
        mTicker.frame(0f);
        assertEquals(0.75f, mSink.last()[3], EPS);
        mTicker.finish();
        assertEquals(1, mSink.clears);
    }

    @Test
    public void aReleaseBeforeTheFirstFrameNeverShowsAnything() {
        mLight.down(1f, 1f);
        mLight.up();
        assertNull(mTicker.frames);
        assertEquals(0, mSink.calls());
    }

    @Test
    public void offMeansTheSinkIsNeverCalled() {
        mAllowed = false;
        mLight.down(10f, 10f);
        mLight.move(12f, 12f);
        mLight.up();
        assertEquals(0, mTicker.starts);
        assertEquals(0, mSink.calls());
        assertFalse(mLight.isHeld());
    }

    @Test
    public void motionStoppedMidPressDropsTheLightAtOnce() {
        mLight.down(10f, 10f);
        mTicker.finish();
        mAllowed = false;
        mLight.up();
        assertEquals(1, mSink.clears);
        assertNull(mTicker.frames);
        assertEquals(0f, mLight.intensity(), EPS);
    }

    @Test
    public void resetClearsWithoutAFade() {
        mLight.down(10f, 10f);
        mTicker.frame(0.3f);
        mLight.reset();
        assertEquals(1, mSink.clears);
        assertNull(mTicker.frames);
        assertFalse(mLight.isHeld());
        // Nothing to clear twice.
        mLight.reset();
        assertEquals(1, mSink.clears);
    }

    @Test
    public void theGateNeedsAnimationsNoReducedMotionAndTheMotionsRule() {
        assertTrue(GlassTouchLight.allowed(true, false, true));
        assertFalse(GlassTouchLight.allowed(false, false, true));
        assertFalse(GlassTouchLight.allowed(true, true, true));
        assertFalse(GlassTouchLight.allowed(true, false, false));
    }

    @Test
    public void easingsMeetTheirEnds() {
        assertEquals(0f, GlassTouchLight.decelerate(0f), 0f);
        assertEquals(1f, GlassTouchLight.decelerate(1f), 0f);
        assertEquals(0f, GlassTouchLight.easeInOut(0f), 0f);
        assertEquals(1f, GlassTouchLight.easeInOut(1f), 0f);
        assertEquals(0.5f, GlassTouchLight.easeInOut(0.5f), EPS);
        // A sliver of distance still takes a frame.
        assertEquals(16L, GlassTouchLight.share(240L, 0.01f));
    }
}
