package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * The rope.
 *
 * <p>Reference geometry is the 1080px panel at 3x with all 27 letters, so the anchor's full entry
 * offset is 78px and the chain is driven by the real {@link AppDrawerRopeMetrics#anchorPx} ramp rather
 * than by numbers invented for the test.
 *
 * <p>Two of these are the model's reason for existing. The <b>lag</b> — letter 0 moving before letter
 * 26 — is the whole difference between a wave on a chain and 27 independent springs, and it is tested
 * against exact zero rather than a threshold: a disturbance can only reach node {@code i} after
 * {@code i + 1} substeps, so at three frames in the tail must still be untouched. The <b>settle</b> is
 * what keeps the controller's Choreographer loop from running forever on an idle open drawer, which is
 * also why divergence and non-finite absorption are pinned here.
 */
public class AppDrawerRopeModelTest {

    private static final float EPS = 1e-4f;

    private static final float DENSITY = 3f;
    private static final float TRACK_HEIGHT = 1500f;
    private static final float FRAME = 1f / 60f;
    /** 1.2s at 60fps. */
    private static final int SETTLE_FRAME_BUDGET = 72;
    /** A brisk but human open: 0.3s of drag. */
    private static final int OPEN_FRAMES = 18;

    private final AppDrawerRopeMetrics mMetrics =
        AppDrawerRopeMetrics.resolve(TRACK_HEIGHT, AppDrawerRopeModel.MAX_LETTERS, DENSITY);

    /** Anchor for a linear open that reaches p = 1 at {@link #OPEN_FRAMES} and stays there. */
    private float openAnchor(int frame) {
        return mMetrics.anchorPx(Math.min(1f, (float) frame / OPEN_FRAMES));
    }

    private static float maxAbsOffset(AppDrawerRopeModel rope) {
        float max = 0f;
        for (int i = 0; i < AppDrawerRopeModel.MAX_LETTERS; i++) {
            max = Math.max(max, Math.abs(rope.offsetPx(i)));
        }
        return max;
    }

    private static float totalAbsOffset(AppDrawerRopeModel rope) {
        float total = 0f;
        for (int i = 0; i < AppDrawerRopeModel.MAX_LETTERS; i++) {
            total += Math.abs(rope.offsetPx(i));
        }
        return total;
    }

    @Test
    public void dampingIsDerivedFromTheCouplingAndTheRatio() {
        // Typed as a literal it would drift out of step with the stiffness and silently change the
        // rope's character; 0.16 of critical for K = 4000 is 20.24.
        assertEquals(2f * (float) Math.sqrt(AppDrawerRopeModel.COUPLING_STIFFNESS)
            * AppDrawerRopeModel.DAMPING_RATIO, AppDrawerRopeModel.DAMPING, EPS);
        assertEquals(20.239f, AppDrawerRopeModel.DAMPING, 1e-3f);
        // Semi-implicit Euler is stable while k*h^2 + 2*C*h < 4, for the stiffest node in the chain.
        float k = (2f * AppDrawerRopeModel.COUPLING_STIFFNESS) + AppDrawerRopeModel.REST_STIFFNESS;
        float h = AppDrawerRopeModel.MAX_STEP;
        assertTrue((k * h * h) + (2f * AppDrawerRopeModel.DAMPING * h) < 4f);
    }

    @Test
    public void restIsTheFixedPoint() {
        AppDrawerRopeModel rope = new AppDrawerRopeModel();
        // Nothing has been disturbed and the anchor is home, so no frame may produce motion.
        for (int frame = 0; frame < 240; frame++) {
            assertFalse("frame " + frame, rope.advance(0f, FRAME, false));
        }
        assertFalse(rope.isMoving());
        for (int i = 0; i < AppDrawerRopeModel.MAX_LETTERS; i++) {
            assertEquals(0f, rope.offsetPx(i), 0f);
            assertEquals(0f, rope.tiltDeg(i), 0f);
        }
    }

    @Test
    public void letterZeroMovesBeforeLetterTwentySix() {
        AppDrawerRopeModel rope = new AppDrawerRopeModel();
        float anchor = mMetrics.entryOffsetPx;
        for (int frame = 0; frame < 3; frame++) {
            rope.advance(anchor, FRAME, false);
        }
        // The head is away and the tail has not been reached at all yet — not "reached less", zero.
        assertNotEquals(0f, rope.offsetPx(0), 0f);
        assertEquals(0f, rope.offsetPx(26), 0f);
        assertEquals(0f, rope.offsetPx(20), 0f);
        // And the disturbance falls off monotonically along the part of the chain it has reached.
        assertTrue(Math.abs(rope.offsetPx(0)) > Math.abs(rope.offsetPx(1)));
        assertTrue(Math.abs(rope.offsetPx(1)) > Math.abs(rope.offsetPx(2)));

        int firstHead = -1;
        int firstMiddle = -1;
        int firstTail = -1;
        float headPeak = 0f;
        float tailPeak = 0f;
        int tailArrived = -1;
        AppDrawerRopeModel walk = new AppDrawerRopeModel();
        for (int frame = 0; frame < 120; frame++) {
            walk.advance(anchor, FRAME, false);
            if (firstHead < 0 && walk.offsetPx(0) != 0f) firstHead = frame;
            if (firstMiddle < 0 && walk.offsetPx(13) != 0f) firstMiddle = frame;
            if (firstTail < 0 && walk.offsetPx(26) != 0f) firstTail = frame;
            headPeak = Math.max(headPeak, Math.abs(walk.offsetPx(0)));
            float tail = Math.abs(walk.offsetPx(26));
            tailPeak = Math.max(tailPeak, tail);
            if (tailArrived < 0 && tail > AppDrawerRopeModel.SETTLE_OFFSET_PX) tailArrived = frame;
        }
        assertTrue("head " + firstHead, firstHead == 0);
        assertTrue("middle " + firstMiddle, firstMiddle > firstHead);
        assertTrue("tail " + firstTail, firstTail > firstMiddle);

        // Ordering on its own is not the property worth having. At K = 900 the tail did arrive last
        // and it arrived at about one pixel against the head's 78 — an ordering no eye can see, and a
        // column that reads as a static list with a wobbly top. So the amplitude is pinned too, and a
        // future retune that flattens the rope fails here instead of shipping.
        //
        // The ceiling is the static lean, which decays over sqrt(K/Kr) = 8.2 nodes: 26 nodes down
        // that is ~7.4% of the head, and 6% is that with the margin REST_STIFFNESS can afford while
        // still settling inside the budget the next test pins. K = 900 answers 0.9% and fails.
        assertTrue("tail peaked at " + tailPeak + "px against a head of " + headPeak + "px",
            tailPeak >= headPeak * 0.06f);
        // And it is a wave crossing a chain rather than a stiff bar: the tail arrives a time the eye
        // can read after the head, and still inside the settle. sqrt(4000) = 63 nodes/s over 27
        // nodes is ~0.43s, i.e. ~26 frames.
        assertTrue("tail arrived at frame " + tailArrived,
            tailArrived >= 6 && tailArrived <= 42);
    }

    @Test
    public void totalOffsetDecaysOnceTheAnchorStops() {
        AppDrawerRopeModel rope = new AppDrawerRopeModel();
        for (int frame = 0; frame <= OPEN_FRAMES; frame++) {
            rope.advance(openAnchor(frame), FRAME, false);
        }
        // Per-frame monotonicity is the wrong claim for an underdamped wave: letters that were at
        // rest start moving as the disturbance reaches them, so the sum rises within a swing. What
        // decays is the envelope, so successive windows are compared instead.
        final int windowFrames = 12;
        float previousPeak = Float.MAX_VALUE;
        for (int window = 0; window < 5; window++) {
            float peak = 0f;
            for (int frame = 0; frame < windowFrames; frame++) {
                rope.advance(mMetrics.anchorPx(1f), FRAME, false);
                peak = Math.max(peak, totalAbsOffset(rope));
            }
            assertTrue("window " + window + " peaked at " + peak + " after " + previousPeak,
                peak < previousPeak);
            previousPeak = peak;
        }
    }

    @Test
    public void isMovingGoesFalseWithinOnePointTwoSecondsAtSixtyFps() {
        AppDrawerRopeModel rope = new AppDrawerRopeModel();
        int settledAt = -1;
        for (int frame = 0; frame < SETTLE_FRAME_BUDGET; frame++) {
            boolean moving = rope.advance(openAnchor(frame), FRAME, false);
            if (!moving && frame > OPEN_FRAMES) {
                settledAt = frame;
                break;
            }
        }
        assertTrue("never settled inside " + SETTLE_FRAME_BUDGET + " frames", settledAt >= 0);
        assertFalse(rope.isMoving());
        // Settled means the column is straight, not merely slow.
        assertTrue(maxAbsOffset(rope) <= AppDrawerRopeModel.SETTLE_OFFSET_PX);
    }

    @Test
    public void reducedMotionCollapsesToRestInOneCall() {
        AppDrawerRopeModel rope = new AppDrawerRopeModel();
        for (int frame = 0; frame < 6; frame++) {
            rope.advance(mMetrics.entryOffsetPx, FRAME, false);
        }
        assertTrue(rope.isMoving());
        assertTrue(maxAbsOffset(rope) > AppDrawerRopeModel.SETTLE_OFFSET_PX);

        // One call, and the anchor goes with it: with animations off the column must draw dead
        // straight, and a retained anchor would leave letter 0 tilted for ever.
        assertFalse(rope.advance(mMetrics.entryOffsetPx, FRAME, true));
        assertFalse(rope.isMoving());
        for (int i = 0; i < AppDrawerRopeModel.MAX_LETTERS; i++) {
            assertEquals(0f, rope.offsetPx(i), 0f);
            assertEquals(0f, rope.tiltDeg(i), 0f);
        }
    }

    @Test
    public void tiltIsBoundedByItsMaximumAndTheBoundIsReachable() {
        AppDrawerRopeModel rope = new AppDrawerRopeModel();
        float minTilt = 0f;
        float maxTilt = 0f;
        // A hard flick: the anchor arrives in a single frame, the worst slope the rope can be asked
        // to draw. Raw slope here is 0.55 * 78 = 43 degrees.
        for (int frame = 0; frame < 240; frame++) {
            rope.advance(frame == 0 ? 0f : mMetrics.entryOffsetPx, FRAME, false);
            for (int i = 0; i < AppDrawerRopeModel.MAX_LETTERS; i++) {
                float tilt = rope.tiltDeg(i);
                assertTrue("letter " + i + " tilted " + tilt,
                    tilt >= -AppDrawerRopeModel.TILT_MAX_DEG
                        && tilt <= AppDrawerRopeModel.TILT_MAX_DEG);
                minTilt = Math.min(minTilt, tilt);
                maxTilt = Math.max(maxTilt, tilt);
            }
        }
        // The clamp is not decoration: it engages on any real flick, and it engages both ways,
        // because the chain swings back through the rest line.
        assertEquals(-AppDrawerRopeModel.TILT_MAX_DEG, minTilt, EPS);
        assertTrue("never tilted the other way: " + maxTilt, maxTilt > 0f);
        // Out-of-range indices answer flat rather than throwing at a draw call.
        assertEquals(0f, rope.tiltDeg(-1), 0f);
        assertEquals(0f, rope.tiltDeg(AppDrawerRopeModel.MAX_LETTERS), 0f);
        assertEquals(0f, rope.offsetPx(AppDrawerRopeModel.MAX_LETTERS), 0f);
    }

    @Test
    public void aThirtiethOfASecondFrameDoesNotDiverge() {
        AppDrawerRopeModel rope = new AppDrawerRopeModel();
        float peak = 0f;
        for (int frame = 0; frame < 400; frame++) {
            rope.advance(mMetrics.anchorPx(Math.min(1f, frame / 9f)), 1f / 30f, false);
            peak = Math.max(peak, maxAbsOffset(rope));
        }
        // A diverging chain reaches absurd magnitudes within a handful of frames; the honest bound
        // is a small multiple of the entry offset, which the swing-through legitimately exceeds.
        assertTrue("offset blew up to " + peak, peak < mMetrics.entryOffsetPx * 2f);
        assertFalse(rope.isMoving());
        assertTrue(maxAbsOffset(rope) <= AppDrawerRopeModel.SETTLE_OFFSET_PX);

        // A delta from a stalled process is clamped rather than integrated in one jump.
        AppDrawerRopeModel stalled = new AppDrawerRopeModel();
        stalled.advance(mMetrics.entryOffsetPx, 1f, false);
        assertTrue(Math.abs(stalled.offsetPx(0)) < mMetrics.entryOffsetPx * 2f);
        assertFalse(Float.isNaN(stalled.offsetPx(0)));
    }

    @Test
    public void nonFiniteInputIsAbsorbedRatherThanPropagated() {
        for (float anchor : new float[] {Float.NaN, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY}) {
            AppDrawerRopeModel rope = new AppDrawerRopeModel();
            for (int frame = 0; frame < 6; frame++) {
                rope.advance(mMetrics.entryOffsetPx, FRAME, false);
            }
            assertFalse("anchor " + anchor, rope.advance(anchor, FRAME, false));
            assertFalse(rope.isMoving());
            for (int i = 0; i < AppDrawerRopeModel.MAX_LETTERS; i++) {
                assertEquals(0f, rope.offsetPx(i), 0f);
                assertEquals(0f, rope.tiltDeg(i), 0f);
            }
            // And it recovers: the next good frame drives the chain again.
            assertTrue(rope.advance(mMetrics.entryOffsetPx, FRAME, false));
        }

        // A non-finite delta cannot be clamped by Spring.clampDelta either, so it lands the same way.
        AppDrawerRopeModel badDelta = new AppDrawerRopeModel();
        assertFalse(badDelta.advance(mMetrics.entryOffsetPx, Float.NaN, false));
        assertFalse(badDelta.isMoving());
        assertEquals(0f, badDelta.offsetPx(0), 0f);
    }

    @Test
    public void theModelHoldsNothingItCouldAllocatePerFrame() {
        // advance() runs for every frame of every transition and for the whole length of a scrub, so
        // the state is fixed-size arrays and primitives and nothing else. Structural, because that is
        // the only thing that keeps a List or a boxed result out of the frame path later.
        for (Field field : AppDrawerRopeModel.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Class<?> type = field.getType();
            boolean ok = type.isPrimitive() || type == float[].class;
            assertTrue("field " + field.getName() + " is a " + type.getName(), ok);
        }
    }
}
