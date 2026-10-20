package com.termux.app.chrome;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The tilted slab: how a pane's rotations become {@code uTilt}, and the Java mirrors of what the
 * program does with it — the face's displacement and magnification, the rim's lean and the
 * light's share — at the press rig's degree, the held border's 6 and the plank's 18.
 */
public class GlassTiltTest {

    private static final float DENSITY = 2.625f; // pong, 420 dpi

    private static float sin(float degrees) {
        return (float) Math.sin(Math.toRadians(degrees));
    }

    // ------------------------------------------------------------------------- the tilt vector

    @Test
    public void theTiltPointsAtTheEdgeGoingAway() {
        float[] t = new float[2];
        // rotationY positive takes the right edge away.
        GlassRefraction.tiltVector(0f, 6f, t);
        assertEquals(sin(6f), t[0], 1e-6f);
        assertEquals(0f, t[1], 0f);
        // rotationX positive takes the top edge away: up is -y.
        GlassRefraction.tiltVector(6f, 0f, t);
        assertEquals(0f, t[0], 0f);
        assertEquals(-sin(6f), t[1], 1e-6f);
        // The press rig pressed at the bottom right: ry > 0, rx < 0, both edges there go away.
        GlassRefraction.tiltVector(-1.1f, 1.1f, t);
        assertTrue(t[0] > 0f && t[1] > 0f);
    }

    @Test
    public void aTiltUnderTheRestIsExactlyFlat() {
        float[] t = {1f, 1f};
        GlassRefraction.tiltVector(0f, 0f, t);
        assertEquals(0f, t[0], 0f);
        assertEquals(0f, t[1], 0f);
        GlassRefraction.tiltVector(0.02f, -0.03f, t);
        assertEquals("a settling spring's residue is flat", 0f, t[0], 0f);
        assertEquals(0f, t[1], 0f);
        GlassRefraction.tiltVector(Float.NaN, 3f, t);
        assertEquals(0f, t[0], 0f);
        assertEquals(0f, t[1], 0f);
        GlassRefraction.tiltVector(0f, GlassRefraction.TILT_REST_DEG * 2f, t);
        assertTrue("just past the rest it tips", t[0] > 0f);
    }

    // ------------------------------------------------------------------------------- the face

    @Test
    public void theFaceShiftReadsAtAPressAndStaysUnderTheCeilingAtThePlanksTip() {
        // The magnitudes the constants are chosen for, dp.
        assertEquals(1.93f, GlassRefraction.tiltShiftPx(sin(1.1f), DENSITY) / DENSITY, 0.02f);
        assertEquals(4.25f, GlassRefraction.tiltShiftPx(sin(3f), DENSITY) / DENSITY, 0.02f);
        assertEquals(6.52f, GlassRefraction.tiltShiftPx(sin(6f), DENSITY) / DENSITY, 0.02f);
        assertEquals(10.08f, GlassRefraction.tiltShiftPx(sin(18f), DENSITY) / DENSITY, 0.02f);
        // Never past the asymptote, and growing with the tilt.
        float last = 0f;
        for (int deg = 1; deg <= 90; deg++) {
            float shift = GlassRefraction.tiltShiftPx(sin(deg), DENSITY);
            assertTrue(shift > last);
            assertTrue(shift < GlassRefraction.TILT_SHIFT_DP * DENSITY);
            last = shift;
        }
        assertEquals(0f, GlassRefraction.tiltShiftPx(0f, DENSITY), 0f);
    }

    @Test
    public void theFaceSamplesTowardTheNearEdgeSoThePictureShowsCarriedTowardTheFarOne() {
        float bx = 500f, by = 900f, rimW = GlassRefraction.Look.rimWidthPx(DENSITY);
        float[] out = new float[2];
        // Right edge going away: in the middle of the face the sample moves left.
        GlassRefraction.tiltOffsetPx(0f, 0f, bx, by, 400f, rimW, sin(6f), 0f, DENSITY, out);
        assertTrue(out[0] < 0f);
        assertEquals(0f, out[1], 0f);
        // Close to the whole shift there (the quadratic's -h/3 adds a little).
        float shift = GlassRefraction.tiltShiftPx(sin(6f), DENSITY);
        assertTrue(-out[0] >= shift && -out[0] < shift + GlassRefraction.TILT_MAG_MAX_DP * DENSITY);
        // Flat: nothing.
        GlassRefraction.tiltOffsetPx(100f, -200f, bx, by, 400f, rimW, 0f, 0f, DENSITY, out);
        assertEquals(0f, out[0], 0f);
        assertEquals(0f, out[1], 0f);
    }

    @Test
    public void theNearHalfMagnifiesAndTheFarHalfCompresses() {
        float bx = 500f, by = 900f, rimW = GlassRefraction.Look.rimWidthPx(DENSITY);
        float tilt = sin(18f), step = 10f;
        float[] a = new float[2], c = new float[2];
        // The sample's step per px of face, along the tilt, deep inside on each half.
        GlassRefraction.tiltOffsetPx(-400f, 0f, bx, by, 100f, rimW, tilt, 0f, DENSITY, a);
        GlassRefraction.tiltOffsetPx(-400f + step, 0f, bx, by, 100f, rimW, tilt, 0f, DENSITY, c);
        float near = (step + c[0] - a[0]) / step;
        GlassRefraction.tiltOffsetPx(400f, 0f, bx, by, 100f, rimW, tilt, 0f, DENSITY, a);
        GlassRefraction.tiltOffsetPx(400f + step, 0f, bx, by, 100f, rimW, tilt, 0f, DENSITY, c);
        float far = (step + c[0] - a[0]) / step;
        assertTrue("the near half shows its picture larger", near < 1f);
        assertTrue("the far half smaller", far > 1f);
        assertTrue("gently", near > 0.95f && far < 1.05f);
    }

    @Test
    public void theMagnificationAveragesToNothingAndIsHeldOnAWholeScreenPane() {
        float rimW = GlassRefraction.Look.rimWidthPx(DENSITY);
        float tilt = sin(18f), deep = 1000f;
        float[] out = new float[2];
        for (float half : new float[] {150f, 540f, 1200f}) {
            float shift = GlassRefraction.tiltShiftPx(tilt, DENSITY);
            double sum = 0.0;
            float most = 0f;
            int n = 2000;
            for (int i = 0; i <= n; i++) {
                float u = -half + 2f * half * i / n;
                GlassRefraction.tiltOffsetPx(u, 0f, half, 900f, deep, rimW, tilt, 0f, DENSITY, out);
                float mag = out[0] + shift; // what the quadratic adds, the shift taken off
                sum += mag;
                most = Math.max(most, Math.abs(mag));
            }
            assertEquals("averages to nothing at half " + half, 0.0, sum / (n + 1), 0.05);
            assertTrue("held at half " + half,
                most <= GlassRefraction.TILT_MAG_MAX_DP * DENSITY * 2f / 3f + 1e-3f);
        }
    }

    @Test
    public void theFaceEasesInFromTheOutlineAndNeverFolds() {
        float bx = 500f, by = 900f, rimW = GlassRefraction.Look.rimWidthPx(DENSITY);
        float[] out = new float[2];
        for (float deg : new float[] {1.1f, 3f, 6f, 18f, 60f}) {
            float tilt = sin(deg);
            // The outline's own pixel samples where it is.
            GlassRefraction.tiltOffsetPx(-bx, 0f, bx, by, 0f, rimW, tilt, 0f, DENSITY, out);
            assertEquals(0f, out[0], 0f);
            // Walking in from either outline along the tilt, the sample never steps backward:
            // the picture is never mirrored, even with no bend at the rim at all.
            for (int side = -1; side <= 1; side += 2) {
                float lastSample = Float.NaN;
                for (int i = 0; i <= 400; i++) {
                    float inside = i * 0.25f;
                    float x = side * (bx - inside);
                    GlassRefraction.tiltOffsetPx(x, 0f, bx, by, inside, rimW, tilt, 0f, DENSITY, out);
                    float sample = -side * (x + out[0]); // distance in from this outline
                    if (!Float.isNaN(lastSample)) {
                        assertTrue(deg + "° side " + side + " folds at " + inside, sample >= lastSample);
                    }
                    lastSample = sample;
                }
            }
        }
    }

    // -------------------------------------------------------------------------------- the rim

    @Test
    public void theEdgeRisingTowardTheViewerLeansThickerAndTheOneGoingAwayThinner() {
        float tilt = sin(3f);
        // Right edge going away: the left edge (normal -x) rises toward the viewer.
        assertTrue(GlassRefraction.tiltLean(-1f, 0f, tilt, 0f) > 0f);
        assertTrue(GlassRefraction.tiltLean(1f, 0f, tilt, 0f) < 0f);
        assertEquals("an edge across the tilt does not lean", 0f,
            GlassRefraction.tiltLean(0f, 1f, tilt, 0f), 0f);
        // The magnitudes: a quarter at the press rig, about two thirds at 3°, whole by 6°.
        assertEquals(0.23f, GlassRefraction.tiltLean(-1f, 0f, sin(1.1f), 0f), 0.005f);
        assertEquals(0.63f, GlassRefraction.tiltLean(-1f, 0f, sin(3f), 0f), 0.005f);
        assertEquals(1f, GlassRefraction.tiltLean(-1f, 0f, sin(6f), 0f), 0f);
        assertEquals(1f, GlassRefraction.tiltLean(-1f, 0f, sin(18f), 0f), 0f);
    }

    @Test
    public void theTiltsLeanAddsToTheLagsAndIsHeldToAWholeOne() {
        float whole = com.termux.app.chrome.GlassMotionLight.LAG_MAX_DP * DENSITY
            / GlassRefraction.LAG_LEAN_GAIN;
        // No tilt: the lag's lean, bit for bit, wherever it is.
        for (float lag : new float[] {0f, whole * 0.3f, -whole * 0.7f, whole * 3f}) {
            assertEquals(GlassRefraction.lagLean(1f, 0f, lag, 0f, DENSITY),
                GlassRefraction.liveLean(1f, 0f, lag, 0f, 0f, 0f, DENSITY), 0f);
        }
        // Summed, then held.
        float tl = GlassRefraction.tiltLean(-1f, 0f, sin(1.1f), 0f);
        assertEquals(0.3f + tl,
            GlassRefraction.liveLean(-1f, 0f, -whole * 0.3f, 0f, sin(1.1f), 0f, DENSITY), 1e-5f);
        assertEquals(1f, GlassRefraction.liveLean(-1f, 0f, -whole, 0f, sin(6f), 0f, DENSITY), 0f);
        // A lag one way and a tilt the other cancel rather than stack.
        assertEquals(0f, GlassRefraction.liveLean(-1f, 0f, whole * tl, 0f, sin(1.1f), 0f, DENSITY), 1e-5f);
        // The leaned pull thickens the near edge's bend as the lag's lean does.
        float band = 20f * DENSITY, strength = 9f * DENSITY, rimW = GlassRefraction.Look.rimWidthPx(DENSITY);
        float rest = GlassRefraction.pullPx(rimW, band, strength, 1000f, rimW);
        assertTrue(GlassRefraction.livePullPx(rimW, band, strength, 1000f, rimW, tl) > rest);
    }

    @Test
    public void theNearEdgeCatchesMoreOfTheLightAndTheFarEdgeLess() {
        assertEquals(1f, GlassRefraction.tiltLight(0f), 0f);
        assertEquals(1f + GlassRefraction.TILT_LIGHT, GlassRefraction.tiltLight(1f), 0f);
        assertEquals(1f - GlassRefraction.TILT_LIGHT, GlassRefraction.tiltLight(-1f), 0f);
        assertTrue("the far edge keeps some light", GlassRefraction.tiltLight(-1f) > 0f);
    }

    // ---------------------------------------------------------------------------- the program

    @Test
    public void theProgramTakesTheTiltOnlyTiltedAndKeepsTheRestingExpressions() {
        String agsl = GlassRefraction.AGSL;
        assertTrue(agsl.contains("uniform float2 uTilt;"));
        assertTrue(agsl.contains("bool tilted = uTilt.x != 0.0 || uTilt.y != 0.0;"));
        // Every tilt term sits behind its gate, and the resting expressions are untouched.
        int gate = agsl.indexOf("bool tilted =");
        assertTrue(agsl.indexOf("tiltLean = clamp(-kTiltLean * dot(n, uTilt), -1.0, 1.0);") > gate);
        assertTrue(agsl.contains("        if (tilted) {\n"
            + "            tiltLean = clamp(-kTiltLean * dot(n, uTilt), -1.0, 1.0);\n"
            + "            lean = lean + tiltLean;\n"
            + "        }\n"
            + "        lean = clamp(lean, -1.0, 1.0);\n"));
        assertTrue(agsl.contains("    if (tilted) {\n"
            + "        sampleCoord = sampleCoord + tiltOffset(p, b, inside, rimW);\n"
            + "    }\n"));
        assertTrue(agsl.contains("        if (tilted) {\n"
            + "            k = k * (1.0 + kTiltLight * tiltLean);\n"
            + "        }\n"));
        assertTrue(agsl.contains("float pull = edgeProfile(t) * min(uStrength, min(b.x, b.y)) * edge;"));
        assertTrue(agsl.contains("float2 sampleCoord = fragCoord - n * pull;"));
        assertTrue(agsl.contains(
            "float k = glow * (gatherShare(n) + 1.5 * touchG) * (1.0 - kPaleCut * pale) * uEdgeGain;"));
        // The face is moved before the dent, and takes one sample: no new eval.
        assertTrue(agsl.indexOf("tiltOffset(p, b, inside, rimW)") < agsl.indexOf("if (uDent.z != 0.0) {"));
        // The mirror's arithmetic.
        assertTrue(agsl.contains("float share = s / (s + kTiltSoft);"));
        assertTrue(agsl.contains("float shift = kTiltShiftDp * uDensity * share;"));
        assertTrue(agsl.contains("float h = max(abs(dir.x) * b.x + abs(dir.y) * b.y, 1.0);"));
        assertTrue(agsl.contains("float m = min(kTiltMag * share, kTiltMagMaxDp * uDensity / h);"));
        assertTrue(agsl.contains("float ease = smoothstep(0.0, max(rimW, 2.0 * (shift + m * h)), inside);"));
        assertTrue(agsl.contains("return dir * ((m * (u * u / h - h / 3.0) - shift) * ease);"));
        // The constants are the Java ones.
        assertConst(agsl, "kTiltShiftDp", GlassRefraction.TILT_SHIFT_DP);
        assertConst(agsl, "kTiltSoft", GlassRefraction.TILT_SOFT);
        assertConst(agsl, "kTiltMag", GlassRefraction.TILT_MAG);
        assertConst(agsl, "kTiltMagMaxDp", GlassRefraction.TILT_MAG_MAX_DP);
        assertConst(agsl, "kTiltLean", GlassRefraction.TILT_LEAN_GAIN);
        assertConst(agsl, "kTiltLight", GlassRefraction.TILT_LIGHT);
    }

    private static void assertConst(String agsl, String name, float java) {
        Matcher m = Pattern.compile("const float " + name + " = ([0-9.]+);").matcher(agsl);
        assertTrue(name + " is a plain decimal constant", m.find());
        assertEquals(name, java, Float.parseFloat(m.group(1)), 0f);
    }
}
