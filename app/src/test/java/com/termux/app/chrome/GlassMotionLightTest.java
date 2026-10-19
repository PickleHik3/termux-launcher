package com.termux.app.chrome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Motion light: velocity, not progress, lights a moving pane; it settles to exactly the resting
 * look and then asks for no more frames; the gate is asked once per motion; a press sends one
 * sweep toward the finger. Also the program's Java mirrors of the terms it adds.
 */
public class GlassMotionLightTest {

    /** Pong's density, and its 90 Hz frame. */
    private static final float DENSITY = 2.625f;
    private static final long FRAME_MS = 11L;
    private static final float EPS = 1e-5f;

    /** A gate answering a fixed multiplier, counting how often it is asked. */
    private static final class CountingGate implements GlassMotionLight.Gate {
        final float answer;
        int asked;

        CountingGate(float answer) {
            this.answer = answer;
        }

        @Override public float multiplier() {
            asked++;
            return answer;
        }
    }

    /** A pane 400 x 600 px at {@code x}, as a draw at {@code now} sees it. */
    private static boolean drawAt(GlassMotionLight light, long now, float x) {
        return light.onDraw(now, x, 100f, x + 400f, 700f);
    }

    /** Slides the pane {@code dpPerSecond} to the right for {@code frames} frames from {@code start}. */
    private static long slide(GlassMotionLight light, long start, float x0, float dpPerSecond,
                              int frames) {
        float pxPerFrame = dpPerSecond * DENSITY * FRAME_MS / 1000f;
        long now = start;
        for (int i = 0; i <= frames; i++) {
            drawAt(light, now, x0 + i * pxPerFrame);
            now += FRAME_MS;
        }
        return now - FRAME_MS;
    }

    @Test
    public void aPaneAtRestStaysDarkAndNeverAsksTheGate() {
        CountingGate gate = new CountingGate(1f);
        GlassMotionLight light = new GlassMotionLight(DENSITY, gate);
        for (int i = 0; i < 20; i++) assertFalse(drawAt(light, 1000L + i * FRAME_MS, 50f));
        assertEquals(0f, light.motion(), 0f);
        assertEquals(0f, light.sweepStrength(), 0f);
        assertEquals(0, gate.asked);
    }

    @Test
    public void aSlowDragStaysCalm() {
        CountingGate gate = new CountingGate(1f);
        GlassMotionLight light = new GlassMotionLight(DENSITY, gate);
        slide(light, 1000L, 0f, GlassMotionLight.CALM_DP_PER_S * 0.8f, 30);
        assertEquals(0f, light.motion(), 0f);
        assertFalse(light.isLit());
        assertEquals(0, gate.asked);
    }

    @Test
    public void aQuickSlideLightsTheLeadingEdgeAndAsksTheGateOnce() {
        CountingGate gate = new CountingGate(1f);
        GlassMotionLight light = new GlassMotionLight(DENSITY, gate);
        slide(light, 1000L, 0f, GlassMotionLight.FULL_DP_PER_S, 20);
        assertTrue(light.isLit());
        assertTrue("full speed for 20 frames lights nearly fully", light.motion() > 0.95f);
        assertEquals("a slide to the right leads right", 1f, light.dirX(), 1e-3f);
        assertEquals(0f, light.dirY(), 1e-3f);
        assertEquals("asked once for the whole motion, never per frame", 1, gate.asked);
    }

    @Test
    public void quickerMotionShowsMore() {
        GlassMotionLight slow = new GlassMotionLight(DENSITY, new CountingGate(1f));
        GlassMotionLight quick = new GlassMotionLight(DENSITY, new CountingGate(1f));
        slide(slow, 1000L, 0f, 900f, 20);
        slide(quick, 1000L, 0f, 1800f, 20);
        assertTrue(slow.motion() > 0f);
        assertTrue(quick.motion() > slow.motion());
    }

    @Test
    public void theLightSettlesToExactlyNothingAndThenStopsAskingForFrames() {
        CountingGate gate = new CountingGate(1f);
        GlassMotionLight light = new GlassMotionLight(DENSITY, gate);
        long now = slide(light, 1000L, 0f, GlassMotionLight.FULL_DP_PER_S, 20);
        float x = 20 * GlassMotionLight.FULL_DP_PER_S * DENSITY * FRAME_MS / 1000f;
        // The pane has landed: every later draw sees it still.
        boolean lit = true;
        long landed = now;
        while (lit && now - landed < 1000L) {
            now += FRAME_MS;
            lit = drawAt(light, now, x);
        }
        assertFalse(lit);
        long settle = now - landed;
        assertTrue("settles within the fall, give or take a frame: " + settle,
            settle <= (long) GlassMotionLight.FALL_MS + 2 * FRAME_MS);
        assertTrue("and not at once: " + settle, settle >= (long) (GlassMotionLight.FALL_MS * 0.5f));
        assertEquals(0f, light.motion(), 0f);
        // At rest from here: no frame asked for, no gate asked.
        for (int i = 1; i <= 10; i++) assertFalse(drawAt(light, now + i * FRAME_MS, x));
        assertEquals(1, gate.asked);
    }

    @Test
    public void slowerAnimationSpeedStretchesTheFall() {
        GlassMotionLight normal = new GlassMotionLight(DENSITY, new CountingGate(1f));
        GlassMotionLight slower = new GlassMotionLight(DENSITY, new CountingGate(1.5f));
        long now = slide(normal, 1000L, 0f, GlassMotionLight.FULL_DP_PER_S, 20);
        slide(slower, 1000L, 0f, GlassMotionLight.FULL_DP_PER_S, 20);
        float x = 20 * GlassMotionLight.FULL_DP_PER_S * DENSITY * FRAME_MS / 1000f;
        long at = now + (long) GlassMotionLight.FALL_MS + FRAME_MS;
        for (long t = now + FRAME_MS; t <= at; t += FRAME_MS) {
            drawAt(normal, t, x);
            drawAt(slower, t, x);
        }
        assertFalse(normal.isLit());
        assertTrue(slower.isLit());
    }

    @Test
    public void offOrAGateSayingNoKeepsItDarkAndIsAskedOncePerMotion() {
        CountingGate gate = new CountingGate(0f);
        GlassMotionLight light = new GlassMotionLight(DENSITY, gate);
        slide(light, 1000L, 0f, GlassMotionLight.FULL_DP_PER_S, 30);
        assertFalse(light.isLit());
        assertEquals(0f, light.motion(), 0f);
        assertEquals(1, gate.asked);
        // A pause long enough to end that motion, then another: asked again, once.
        slide(light, 3000L, 0f, GlassMotionLight.FULL_DP_PER_S, 30);
        assertEquals(2, gate.asked);
    }

    @Test
    public void drawsFarApartAreNotOneMotion() {
        CountingGate gate = new CountingGate(1f);
        GlassMotionLight light = new GlassMotionLight(DENSITY, gate);
        drawAt(light, 1000L, 0f);
        assertFalse(drawAt(light, 1000L + GlassMotionLight.MAX_GAP_MS + 1L, 60f));
        assertEquals(0, gate.asked);
    }

    @Test
    public void aPanePlacedInOneStepWasNotMoved() {
        CountingGate gate = new CountingGate(1f);
        GlassMotionLight light = new GlassMotionLight(DENSITY, gate);
        drawAt(light, 1000L, 0f);
        float jump = (GlassMotionLight.JUMP_DP + 10f) * DENSITY;
        assertFalse(drawAt(light, 1000L + FRAME_MS, jump));
        // The keyboard taking the bottom of a pane in one frame is a placement too.
        assertFalse(light.onDraw(1000L + 2 * FRAME_MS, jump, 100f, jump + 400f, 700f - jump));
        assertEquals(0, gate.asked);
    }

    @Test
    public void aPaneThatOnlyGrowsLightsAllRound() {
        GlassMotionLight light = new GlassMotionLight(DENSITY, new CountingGate(1f));
        float growPx = 1500f * DENSITY * FRAME_MS / 1000f;
        long now = 1000L;
        for (int i = 0; i <= 15; i++) {
            float g = i * growPx;
            light.onDraw(now, 100f - g, 100f - g, 500f + g, 700f + g);
            now += FRAME_MS;
        }
        assertTrue(light.motion() > 0f);
        assertEquals("no way leads when nothing travels", 0f,
            (float) Math.hypot(light.dirX(), light.dirY()), 1e-3f);
    }

    @Test
    public void aPressSweepsOnceTowardTheFingerAndEnds() {
        CountingGate gate = new CountingGate(1f);
        GlassMotionLight light = new GlassMotionLight(DENSITY, gate);
        drawAt(light, 1000L, 0f);
        // A press on the right half of a 400 x 600 pane.
        assertTrue(light.press(1000L, 380f, 300f, 400f, 600f));
        assertEquals(1f, light.sweepDirX(), EPS);
        assertEquals(0f, light.sweepDirY(), EPS);
        assertTrue(drawAt(light, 1000L + 20L, 0f));
        assertTrue("starts on the far side", light.sweepPosition() < -1f);
        long half = 1000L + (long) (GlassMotionLight.SWEEP_MS / 2f);
        assertTrue(drawAt(light, half, 0f));
        assertEquals("strongest half way", 1f, light.sweepStrength(), 1e-3f);
        assertEquals("at the centre half way", 0f, light.sweepPosition(), 1e-3f);
        assertFalse("over once its time is up",
            drawAt(light, 1000L + (long) GlassMotionLight.SWEEP_MS + 1L, 0f));
        assertEquals(0f, light.sweepStrength(), 0f);
        assertEquals(0f, light.motion(), 0f);
    }

    @Test
    public void aPressWhereMotionIsStoppedDoesNothing() {
        GlassMotionLight light = new GlassMotionLight(DENSITY, new CountingGate(0f));
        assertFalse(light.press(1000L, 10f, 10f, 400f, 600f));
        assertFalse(light.isLit());
    }

    @Test
    public void resetPutsEverythingAtRest() {
        GlassMotionLight light = new GlassMotionLight(DENSITY, new CountingGate(1f));
        slide(light, 1000L, 0f, GlassMotionLight.FULL_DP_PER_S, 10);
        light.press(1200L, 10f, 10f, 400f, 600f);
        light.reset();
        assertFalse(light.isLit());
        assertEquals(0f, light.motion(), 0f);
        assertEquals(0f, light.sweepStrength(), 0f);
    }

    @Test
    public void thePolicyCurves() {
        assertEquals(0f, GlassMotionLight.levelForSpeed(0f), 0f);
        assertEquals(0f, GlassMotionLight.levelForSpeed(GlassMotionLight.CALM_DP_PER_S), 0f);
        assertEquals(1f, GlassMotionLight.levelForSpeed(GlassMotionLight.FULL_DP_PER_S), 0f);
        assertEquals(1f, GlassMotionLight.levelForSpeed(10f * GlassMotionLight.FULL_DP_PER_S), 0f);
        assertEquals(0f, GlassMotionLight.ease(0f), 0f);
        assertEquals(1f, GlassMotionLight.ease(1f), 0f);
        assertEquals(0.5f, GlassMotionLight.ease(0.5f), EPS);
        // The fall is linear and reaches exactly nothing.
        assertEquals(0.5f, GlassMotionLight.advance(1f, 0f, (long) (GlassMotionLight.FALL_MS / 2f), 1f), 1e-3f);
        assertEquals(0f, GlassMotionLight.advance(1f, 0f, (long) GlassMotionLight.FALL_MS, 1f), 0f);
        // The rise is quick: most of the way in two time constants.
        assertTrue(GlassMotionLight.advance(0f, 1f, (long) (2f * GlassMotionLight.RISE_MS), 1f) > 0.85f);
        assertEquals(-GlassMotionLight.SWEEP_REACH, GlassMotionLight.sweepPositionAt(0f), EPS);
        assertEquals(GlassMotionLight.SWEEP_REACH, GlassMotionLight.sweepPositionAt(1f), EPS);
        assertEquals(0f, GlassMotionLight.sweepStrengthAt(0f), 0f);
        assertEquals(0f, GlassMotionLight.sweepStrengthAt(1f), 0f);
        assertEquals(5f, GlassMotionLight.leadingSpeed(3f, 0f, 0f, 4f), EPS);
    }

    @Test
    public void theGateStopsForEveryReasonAndOtherwiseCarriesTheSpeed() {
        assertEquals(1f, GlassMotionGate.multiplier(true, false, true, false, false, 1f), 0f);
        assertEquals(1.5f, GlassMotionGate.multiplier(true, false, true, false, false, 1.5f), 0f);
        assertEquals("Animation speed Off", 0f,
            GlassMotionGate.multiplier(true, false, true, false, false, 0f), 0f);
        assertEquals(0f, GlassMotionGate.multiplier(false, false, true, false, false, 1f), 0f);
        assertEquals(0f, GlassMotionGate.multiplier(true, true, true, false, false, 1f), 0f);
        assertEquals(0f, GlassMotionGate.multiplier(true, false, false, false, false, 1f), 0f);
        assertEquals(0f, GlassMotionGate.multiplier(true, false, true, true, false, 1f), 0f);
        assertEquals(0f, GlassMotionGate.multiplier(true, false, true, false, true, 1f), 0f);
    }

    // ------------------------------------------------------------------ the program's mirrors

    @Test
    public void noMotionIsTheRestingPullExactly() {
        float density = DENSITY;
        float band = 20f * density, strength = 9f * density, half = 200f;
        float rim = GlassRefraction.Look.rimWidthPx(density);
        for (float inside = 0f; inside <= band; inside += 0.5f) {
            assertEquals(GlassRefraction.pullPx(inside, band, strength, half, rim),
                GlassRefraction.motionPullPx(inside, band, strength, half, rim, 0f), 0f);
        }
    }

    @Test
    public void motionSwellsTheBendButKeepsItsCap() {
        float density = DENSITY;
        float band = 20f * density, strength = 9f * density;
        float rim = GlassRefraction.Look.rimWidthPx(density);
        float inside = rim * 2f;
        float rest = GlassRefraction.pullPx(inside, band, strength, 200f, rim);
        float moving = GlassRefraction.motionPullPx(inside, band, strength, 200f, rim, 1f);
        assertEquals(rest * (1f + GlassRefraction.MOTION_BEND), moving, rest * 1e-4f);
        // A small pane's half side still holds it.
        float smallHalf = strength * 0.5f;
        assertEquals(GlassRefraction.pullPx(inside, band, Float.MAX_VALUE, smallHalf, rim),
            GlassRefraction.motionPullPx(inside, band, strength, smallHalf, rim, 1f), EPS);
    }

    @Test
    public void theLeadingEdgeLeads() {
        assertEquals(1f, GlassRefraction.motionLead(1f, 0f, 1f, 0f), EPS);
        assertEquals(0f, GlassRefraction.motionLead(-1f, 0f, 1f, 0f), EPS);
        assertEquals(0f, GlassRefraction.motionLead(0f, 1f, 1f, 0f), EPS);
        // A pane that only grows leads all round.
        assertEquals(1f, GlassRefraction.motionLead(-1f, 0f, 0f, 0f), EPS);
        assertEquals(1f, GlassRefraction.motionLead(0f, -1f, 0f, 0f), EPS);
    }

    @Test
    public void theSweepIsABandAtItsPosition() {
        float bx = 200f, by = 300f;
        assertEquals(0f, GlassRefraction.sweepShare(0f, 0f, bx, by, 1f, 0f, 0f, 0f), 0f);
        assertEquals(0.8f, GlassRefraction.sweepShare(0f, 50f, bx, by, 1f, 0f, 0f, 0.8f), EPS);
        float atBand = GlassRefraction.sweepShare(100f, 0f, bx, by, 1f, 0f, 0.5f, 1f);
        float behind = GlassRefraction.sweepShare(-100f, 0f, bx, by, 1f, 0f, 0.5f, 1f);
        assertEquals(1f, atBand, EPS);
        assertTrue(behind < 0.01f);
    }
}
