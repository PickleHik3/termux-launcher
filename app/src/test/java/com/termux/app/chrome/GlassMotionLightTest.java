package com.termux.app.chrome;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Living glass: a pane's bend lags it on an underdamped spring, rocks back once or twice as it
 * lands and settles to exactly the resting look; a press dents the glass until it lifts; placement
 * never kicks; at rest nothing is asked for. Also the program's Java mirrors of the terms it adds,
 * and that the program text stays well formed.
 */
public class GlassMotionLightTest {

    /** Pong's density, and its 90 Hz frame. */
    private static final float DENSITY = 2.625f;
    private static final long FRAME_MS = 11L;
    /** What reads as visible: 2% of a whole lean. */
    private static final float VISIBLE_DP = 0.02f * GlassMotionLight.LAG_MAX_DP;

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

    private static float lagDp(GlassMotionLight light) {
        return light.lagXPx() / DENSITY;
    }

    /** Slides the pane at {@code dpPerSecond} for {@code frames} frames; the frame time after. */
    private static long slide(GlassMotionLight light, long start, float x0, float dpPerSecond,
                              int frames, List<Float> lags) {
        float pxPerFrame = dpPerSecond * DENSITY * FRAME_MS / 1000f;
        long now = start;
        for (int i = 0; i <= frames; i++) {
            drawAt(light, now, x0 + i * pxPerFrame);
            if (lags != null) lags.add(lagDp(light));
            now += FRAME_MS;
        }
        return now;
    }

    private static float endOf(float x0, float dpPerSecond, int frames) {
        return x0 + frames * dpPerSecond * DENSITY * FRAME_MS / 1000f;
    }

    // ------------------------------------------------------------------------------- at rest

    @Test
    public void aStillPaneStaysExactlyAtRestAndNeverAsksTheGate() {
        CountingGate gate = new CountingGate(1f);
        GlassMotionLight light = new GlassMotionLight(DENSITY, gate);
        for (int i = 0; i < 30; i++) assertFalse(drawAt(light, 1000L + i * FRAME_MS, 50f));
        assertEquals(0f, light.lagXPx(), 0f);
        assertEquals(0f, light.lagYPx(), 0f);
        assertEquals(0f, light.dentDepthPx(), 0f);
        assertFalse(light.isMoving());
        assertEquals(0, gate.asked);
    }

    @Test
    public void afterAnyMotionItLandsOnExactZeroAndStopsAskingForFrames() {
        CountingGate gate = new CountingGate(1f);
        GlassMotionLight light = new GlassMotionLight(DENSITY, gate);
        long now = slide(light, 1000L, 0f, 2500f, 25, null);
        float x = endOf(0f, 2500f, 25);
        boolean asked = true;
        for (int i = 0; i < 120; i++, now += FRAME_MS) asked = drawAt(light, now, x);
        assertFalse("no frame asked for at rest", asked);
        assertEquals(0f, light.lagXPx(), 0f);
        assertEquals(0f, light.lagYPx(), 0f);
        // Still at rest: more draws neither move it nor ask the gate again.
        int askedBefore = gate.asked;
        for (int i = 0; i < 10; i++, now += FRAME_MS) assertFalse(drawAt(light, now, x));
        assertEquals(askedBefore, gate.asked);
    }

    // ----------------------------------------------------------------------------------- lag

    @Test
    public void theBendLagsTheWayThePaneMovesAndMoreForAFlick() {
        GlassMotionLight slow = new GlassMotionLight(DENSITY, new CountingGate(1f));
        List<Float> slowLags = new ArrayList<>();
        slide(slow, 1000L, 0f, 200f, 30, slowLags);
        GlassMotionLight fast = new GlassMotionLight(DENSITY, new CountingGate(1f));
        List<Float> fastLags = new ArrayList<>();
        slide(fast, 1000L, 0f, 2500f, 30, fastLags);
        float slowMax = max(slowLags), fastMax = max(fastLags);
        assertTrue("a slow drag barely deforms: " + slowMax,
            slowMax > 0f && slowMax < 0.12f * GlassMotionLight.LAG_MAX_DP);
        assertTrue("a flick deforms fully: " + fastMax,
            fastMax > 0.85f * GlassMotionLight.LAG_MAX_DP);
        assertTrue("held under the clamp", fastMax <= GlassMotionLight.LAG_MAX_DP + 1e-3f);
        assertEquals("a horizontal slide lags only across", 0f, fast.lagYPx(), 0f);
        // Leftward: the lag points left.
        GlassMotionLight left = new GlassMotionLight(DENSITY, new CountingGate(1f));
        slide(left, 1000L, 2000f, -1500f, 10, null);
        assertTrue(left.lagXPx() < 0f);
    }

    @Test
    public void onLandingItRocksBackOnceOrTwiceAndSettlesInAboutFourHundredMs() {
        for (float speed : new float[] {800f, 2500f, 3500f}) {
            GlassMotionLight light = new GlassMotionLight(DENSITY, new CountingGate(1f));
            long now = slide(light, 1000L, 0f, speed, 27, null);
            float x = endOf(0f, speed, 27);
            long landed = now - FRAME_MS;
            List<Float> after = new ArrayList<>();
            long restAt = -1L, lastVisible = landed;
            for (int i = 0; i < 120; i++, now += FRAME_MS) {
                boolean more = drawAt(light, now, x);
                float lag = lagDp(light);
                after.add(lag);
                if (Math.abs(lag) > VISIBLE_DP) lastVisible = now;
                if (!more && restAt < 0L) restAt = now;
            }
            // Visible swings past the rest: the overshoot back, and at most one more.
            int swings = 0;
            int sign = 1;
            for (float lag : after) {
                if (Math.abs(lag) <= VISIBLE_DP) continue;
                int s = lag > 0f ? 1 : -1;
                if (s != sign) {
                    swings++;
                    sign = s;
                }
            }
            assertTrue(speed + ": rocks back at least once, " + swings, swings >= 1);
            assertTrue(speed + ": rocks back at most twice, " + swings, swings <= 2);
            assertTrue(speed + ": the overshoot is smaller than the lag",
                -min(after) < 0.35f * GlassMotionLight.LAG_MAX_DP);
            assertTrue(speed + ": visibly still by 400 ms, " + (lastVisible - landed),
                lastVisible - landed <= 400L);
            assertTrue(speed + ": stops asking for frames by 550 ms, " + (restAt - landed),
                restAt > 0L && restAt - landed <= 550L);
            assertEquals(0f, light.lagXPx(), 0f);
        }
    }

    @Test
    public void theResultDoesNotDependOnTheFrameRate() {
        // 60 Hz against 120 Hz over the same motion: the spring is solved exactly per interval.
        float[] lags = new float[2];
        long[] frames = {16L, 8L};
        for (int r = 0; r < 2; r++) {
            GlassMotionLight light = new GlassMotionLight(DENSITY, new CountingGate(1f));
            long now = 1000L;
            float pxPerMs = 1500f * DENSITY / 1000f;
            for (long t = 0; t <= 192L; t += frames[r]) drawAt(light, now + t, t * pxPerMs);
            for (long t = 192L + frames[r]; t <= 288L; t += frames[r]) drawAt(light, now + t, 192L * pxPerMs);
            lags[r] = lagDp(light);
        }
        assertEquals(lags[0], lags[1], 1e-2f);
    }

    // ---------------------------------------------------------------- placement, not motion

    @Test
    public void aPlacementFromRestNeverKicks() {
        CountingGate gate = new CountingGate(1f);
        GlassMotionLight light = new GlassMotionLight(DENSITY, gate);
        drawAt(light, 1000L, 0f);
        // A jump past JUMP_DP in one frame: the keyboard, a rotation.
        assertFalse(drawAt(light, 1000L + FRAME_MS, (GlassMotionLight.JUMP_DP + 10f) * DENSITY));
        assertEquals(0f, light.lagXPx(), 0f);
        // A move after a pause: the first frame of a motion, or a redraw somewhere else.
        assertFalse(drawAt(light, 1000L + FRAME_MS + GlassMotionLight.MAX_GAP_MS + 1L, 0f));
        assertEquals(0f, light.lagXPx(), 0f);
        assertEquals("placement asks nothing", 0, gate.asked);
        // A pane that only grows past the jump in one frame was placed as well.
        GlassMotionLight grow = new GlassMotionLight(DENSITY, new CountingGate(1f));
        grow.onDraw(1000L, 100f, 100f, 500f, 700f);
        assertFalse(grow.onDraw(1000L + FRAME_MS, -200f, 100f, 800f, 700f));
        assertEquals(0f, grow.lagXPx(), 0f);
    }

    @Test
    public void aPlacementMidSettleCarriesTheBendWithoutAKick() {
        GlassMotionLight moved = new GlassMotionLight(DENSITY, new CountingGate(1f));
        GlassMotionLight kept = new GlassMotionLight(DENSITY, new CountingGate(1f));
        long now = slide(moved, 1000L, 0f, 2000f, 20, null);
        slide(kept, 1000L, 0f, 2000f, 20, null);
        float x = endOf(0f, 2000f, 20);
        drawAt(moved, now, x);
        drawAt(kept, now, x);
        now += FRAME_MS;
        // One pane is put 300 dp further on; the other stays. Both settle the same.
        drawAt(moved, now, x + 300f * DENSITY);
        drawAt(kept, now, x);
        assertEquals(lagDp(kept), lagDp(moved), 1e-3f);
        for (int i = 0; i < 10; i++) {
            now += FRAME_MS;
            drawAt(moved, now, x + 300f * DENSITY);
            drawAt(kept, now, x);
            assertEquals(lagDp(kept), lagDp(moved), 1e-3f);
        }
    }

    // ---------------------------------------------------------------------------------- dent

    @Test
    public void aPressDentsTheGlassHoldsStillAndSpringsBackToExactlyZero() {
        CountingGate gate = new CountingGate(1f);
        GlassMotionLight light = new GlassMotionLight(DENSITY, gate);
        long now = 1000L;
        drawAt(light, now, 0f);
        assertTrue(light.press(now + 3L, 120f, 240f));
        assertEquals(1, gate.asked);
        assertEquals(120f, light.dentXPx(), 0f);
        assertEquals(240f, light.dentYPx(), 0f);
        float full = GlassMotionLight.DENT_DEPTH_DP * DENSITY;
        float deepest = 0f;
        boolean more = true;
        long held = -1L;
        for (int i = 1; i < 60; i++) {
            now += FRAME_MS;
            more = drawAt(light, now, 0f);
            deepest = Math.max(deepest, light.dentDepthPx());
            if (!more && held < 0L) held = now;
        }
        assertTrue("reaches its depth", deepest >= full);
        assertTrue("overshoots a little, " + deepest / full, deepest <= 1.25f * full);
        assertFalse("held still, nothing asked for", more);
        assertTrue("settled under the finger within 400 ms", held > 0L && held - 1003L <= 400L);
        assertEquals(full, light.dentDepthPx(), 0f);

        assertTrue(light.release(now));
        assertFalse("a second release is nothing", light.release(now));
        float shallowest = 0f;
        long restAt = -1L;
        long released = now;
        for (int i = 0; i < 60; i++) {
            now += FRAME_MS;
            more = drawAt(light, now, 0f);
            shallowest = Math.min(shallowest, light.dentDepthPx());
            if (!more && restAt < 0L) restAt = now;
        }
        assertTrue("rebounds past flat", shallowest < 0f);
        assertTrue("but only a little, " + shallowest / full, shallowest > -0.25f * full);
        assertTrue("back at rest within 450 ms, " + (restAt - released),
            restAt > 0L && restAt - released <= 450L);
        assertEquals(0f, light.dentDepthPx(), 0f);
        assertFalse(light.isMoving());
    }

    @Test
    public void aQuickTapStillDentsAndReleases() {
        GlassMotionLight light = new GlassMotionLight(DENSITY, new CountingGate(1f));
        drawAt(light, 1000L, 0f);
        light.press(1000L, 50f, 50f);
        drawAt(light, 1011L, 0f);
        drawAt(light, 1022L, 0f);
        assertTrue(light.dentDepthPx() > 0f);
        assertTrue(light.release(1030L));
        long now = 1033L;
        for (int i = 0; i < 80; i++, now += FRAME_MS) drawAt(light, now, 0f);
        assertEquals(0f, light.dentDepthPx(), 0f);
        assertFalse(drawAt(light, now, 0f));
    }

    @Test
    public void aDentPressedAfterALongRestStartsFromTheFingerNotFromTheLastDraw() {
        GlassMotionLight light = new GlassMotionLight(DENSITY, new CountingGate(1f));
        drawAt(light, 1000L, 0f);
        // Seconds untouched, then a press: the first draw after it is one frame into the dent.
        light.press(9000L, 10f, 10f);
        drawAt(light, 9000L + FRAME_MS, 0f);
        assertTrue(light.dentDepthPx() < GlassMotionLight.DENT_DEPTH_DP * DENSITY * 0.5f);
        assertTrue(light.dentDepthPx() > 0f);
    }

    // ---------------------------------------------------------------------------------- gate

    @Test
    public void aClosedGateKeepsTheGlassStillAndIsAskedOncePerMotion() {
        CountingGate gate = new CountingGate(0f);
        GlassMotionLight light = new GlassMotionLight(DENSITY, gate);
        long now = slide(light, 1000L, 0f, 3000f, 20, null);
        assertEquals(0f, light.lagXPx(), 0f);
        assertEquals("once for the whole motion", 1, gate.asked);
        float x = endOf(0f, 3000f, 20);
        assertFalse(drawAt(light, now, x));
        assertFalse(light.press(now, 10f, 10f));
        assertEquals(0f, light.dentDepthPx(), 0f);
        assertFalse(light.release(now));
        // The next motion asks afresh.
        slide(light, now + FRAME_MS, x, 3000f, 3, null);
        assertEquals(3, gate.asked);
    }

    @Test
    public void animationSpeedStretchesTheSpringsTime() {
        long[] settle = new long[2];
        float[] multipliers = {1f, 2f};
        for (int r = 0; r < 2; r++) {
            GlassMotionLight light = new GlassMotionLight(DENSITY, new CountingGate(multipliers[r]));
            // The same travel, taking as much longer as the multiplier says.
            float speed = 2000f / multipliers[r];
            int frames = Math.round(20 * multipliers[r]);
            long now = slide(light, 1000L, 0f, speed, frames, null);
            float x = endOf(0f, speed, frames);
            long landed = now - FRAME_MS;
            for (int i = 0; i < 200; i++, now += FRAME_MS) {
                if (!drawAt(light, now, x)) {
                    settle[r] = now - landed;
                    break;
                }
            }
        }
        assertTrue("Slower settles about twice as long: " + settle[0] + " / " + settle[1],
            settle[1] > 1.6f * settle[0] && settle[1] < 2.4f * settle[0]);
    }

    @Test
    public void theGateIsZeroForEveryReasonToKeepStill() {
        assertEquals(1f, GlassMotionGate.multiplier(true, false, true, false, false, 1f), 0f);
        assertEquals(1.5f, GlassMotionGate.multiplier(true, false, true, false, false, 1.5f), 0f);
        assertEquals(0f, GlassMotionGate.multiplier(false, false, true, false, false, 1f), 0f);
        assertEquals(0f, GlassMotionGate.multiplier(true, true, true, false, false, 1f), 0f);
        assertEquals(0f, GlassMotionGate.multiplier(true, false, false, false, false, 1f), 0f);
        assertEquals(0f, GlassMotionGate.multiplier(true, false, true, true, false, 1f), 0f);
        assertEquals(0f, GlassMotionGate.multiplier(true, false, true, false, true, 1f), 0f);
        assertEquals("Animation speed Off", 0f,
            GlassMotionGate.multiplier(true, false, true, false, false, 0f), 0f);
    }

    @Test
    public void resetGoesToRestAtOnce() {
        GlassMotionLight light = new GlassMotionLight(DENSITY, new CountingGate(1f));
        long now = slide(light, 1000L, 0f, 2500f, 10, null);
        light.press(now, 5f, 5f);
        light.reset();
        assertEquals(0f, light.lagXPx(), 0f);
        assertEquals(0f, light.dentDepthPx(), 0f);
        assertFalse(light.isMoving());
    }

    // -------------------------------------------------------------- the program's mirrors

    @Test
    public void theLiveBendThickensTheLeadingEdgeAndThinsTheTrailingOne() {
        float band = 18f * DENSITY, strength = 27f * DENSITY, big = 1000f;
        float rimW = GlassRefraction.Look.rimWidthPx(DENSITY);
        for (float inside : new float[] {rimW, band * 0.25f, band * 0.6f}) {
            float rest = GlassRefraction.pullPx(inside, band, strength, big, rimW);
            assertEquals("lean 0 is the resting pull, bit for bit", rest,
                GlassRefraction.livePullPx(inside, band, strength, big, rimW, 0f), 0f);
            assertTrue(GlassRefraction.livePullPx(inside, band, strength, big, rimW, 1f) > rest);
            assertTrue(GlassRefraction.livePullPx(inside, band, strength, big, rimW, -1f) < rest);
        }
        // Still held at the half short side.
        float small = 10f * DENSITY;
        assertTrue(GlassRefraction.livePullPx(rimW, band, strength, small, rimW, 1f)
            <= small + 1e-4f);
        // The lean: a whole lag facing the edge is 1, behind it -1, across it 0.
        float whole = GlassMotionLight.LAG_MAX_DP * DENSITY;
        assertEquals(1f, GlassRefraction.lagLean(1f, 0f, whole * 2f, 0f, DENSITY), 0f);
        assertEquals(-1f, GlassRefraction.lagLean(-1f, 0f, whole * 2f, 0f, DENSITY), 0f);
        assertEquals(0f, GlassRefraction.lagLean(0f, 1f, whole, 0f, DENSITY), 0f);
        assertEquals(0.5f, GlassRefraction.lagLean(1f, 0f, whole * 0.5f / GlassRefraction.LAG_LEAN_GAIN, 0f, DENSITY), 1e-6f);
    }

    @Test
    public void theDentIsFlatAtItsCentreAndRimAndMovesThePictureByItsDepthAtItsSteepest() {
        float r = GlassRefraction.DENT_RADIUS_DP * DENSITY, depth = 6f;
        assertEquals(0f, GlassRefraction.dentShiftPx(0f, r, depth), 0f);
        assertEquals(0f, GlassRefraction.dentShiftPx(r, r, depth), 0f);
        assertEquals(0f, GlassRefraction.dentShiftPx(2f * r, r, depth), 0f);
        assertEquals(16.0 / (25.0 * Math.sqrt(5.0)), GlassRefraction.DENT_PEAK, 1e-7);
        float steepest = (float) (r / Math.sqrt(5.0));
        assertEquals(depth, GlassRefraction.dentShiftPx(steepest, r, depth), 1e-4f);
        for (int i = 0; i <= 50; i++) {
            float shift = GlassRefraction.dentShiftPx(r * i / 50f, r, depth);
            assertTrue("never past its depth at " + i, shift <= depth + 1e-4f && shift >= 0f);
        }
        assertEquals("a rebound gathers inward", -depth,
            GlassRefraction.dentShiftPx(steepest, r, -depth), 1e-4f);
    }

    @Test
    public void theProgramTakesTheLiveTermsOnlyWhenLiveAndKeepsTheRestingExpressions() {
        String agsl = GlassRefraction.AGSL;
        assertTrue(agsl.contains("uniform float2 uLag;"));
        assertTrue(agsl.contains("uniform float3 uDent;"));
        for (String gone : new String[] {"uMotion", "uMotionDir", "uSweep", "sweepG", "kSweepGain",
            "kMotionBend", "kMotionDisp", "kMotionRimSplitDp", "lead"}) {
            assertFalse(gone + " is gone", Pattern.compile("\\b" + gone + "\\b").matcher(agsl).find());
        }
        assertTrue(agsl.contains("bool live = uLag.x != 0.0 || uLag.y != 0.0 || uDent.z != 0.0 || tilted;"));
        // The resting expressions are untouched, and every live term sits behind its gate.
        assertTrue(agsl.contains("float pull = edgeProfile(t) * min(uStrength, min(b.x, b.y)) * edge;"));
        assertTrue(agsl.contains("float2 sampleCoord = fragCoord - n * pull;"));
        assertTrue(agsl.contains("float split = uDisp > 0.0 ? pull * uDisp * cornerWeight(p, b) : 0.0;"));
        assertTrue(agsl.contains("float2 sn = n;"));
        int liveGate = agsl.indexOf("if (live) {");
        assertTrue(liveGate > 0);
        assertTrue(agsl.indexOf("pull = bent;") > liveGate);
        assertTrue(agsl.indexOf("sampleCoord = sampleCoord + dentOff;") > agsl.indexOf("if (uDent.z != 0.0) {"));
        assertTrue(agsl.indexOf("sn = split > 0.0 ? sv / split : n;") > agsl.lastIndexOf("if (live) {"));
        // The constants are the Java ones.
        assertConst(agsl, "kLagMaxDp", GlassMotionLight.LAG_MAX_DP);
        assertConst(agsl, "kLagLean", GlassRefraction.LAG_LEAN_GAIN);
        assertConst(agsl, "kLagBend", GlassRefraction.LAG_BEND);
        assertConst(agsl, "kLagBand", GlassRefraction.LAG_BAND);
        assertConst(agsl, "kDentRadiusDp", GlassRefraction.DENT_RADIUS_DP);
        assertConst(agsl, "kDentPeak", GlassRefraction.DENT_PEAK);
        assertConst(agsl, "kLiveDisp", GlassRefraction.LIVE_DISPERSION);
    }

    private static void assertConst(String agsl, String name, float java) {
        Matcher m = Pattern.compile("const float " + name + " = ([0-9.]+);").matcher(agsl);
        assertTrue(name + " is a plain decimal constant", m.find());
        assertEquals(name, java, Float.parseFloat(m.group(1)), 0f);
    }

    @Test
    public void theProgramTextIsBalanced() {
        String agsl = GlassRefraction.AGSL;
        int braces = 0, parens = 0;
        for (int i = 0; i < agsl.length(); i++) {
            char c = agsl.charAt(i);
            if (c == '{') braces++;
            else if (c == '}') braces--;
            else if (c == '(') parens++;
            else if (c == ')') parens--;
            assertTrue("no brace closes before it opens, at " + i, braces >= 0);
            assertTrue("no paren closes before it opens, at " + i, parens >= 0);
        }
        assertEquals(0, braces);
        assertEquals(0, parens);
        assertFalse("no exponent a float printed as Java writes it", agsl.matches("(?s).*[0-9]E-?[0-9].*"));
    }

    @Test
    public void everyUniformTheProgramDeclaresHasAWriter() throws Exception {
        File source = new File("src/main/java/com/termux/app/chrome/GlassRefraction.java");
        if (!source.isFile()) source = new File("app/" + source.getPath());
        assumeTrue("the source is reachable from the test's directory", source.isFile());
        String java = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("uniform (\\w+) (\\w+);").matcher(GlassRefraction.AGSL);
        int seen = 0;
        while (m.find()) {
            seen++;
            String type = m.group(1), name = m.group(2);
            String writer = "shader".equals(type) ? "setInputShader(\"" + name + "\""
                : "setFloatUniform(\"" + name + "\"";
            assertTrue(name + " has a writer", java.contains(writer));
            assertTrue(type + " is an AGSL type", type.matches("shader|float[234]?|half[234]?"));
        }
        assertTrue(seen > 20);
    }

    // -------------------------------------------------------------------------------- helpers

    private static float max(List<Float> values) {
        float m = -Float.MAX_VALUE;
        for (float v : values) m = Math.max(m, v);
        return m;
    }

    private static float min(List<Float> values) {
        float m = Float.MAX_VALUE;
        for (float v : values) m = Math.min(m, v);
        return m;
    }
}
