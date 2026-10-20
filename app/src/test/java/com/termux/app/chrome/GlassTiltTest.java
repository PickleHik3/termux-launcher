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
        // The magnitudes the constants are chosen for, dp: visible at a press, and at the plank's
        // 18 no more than the outline's own motion under the tilt, about 7 dp (glass-perception
        // research, recommendation 3; it was 10.1).
        assertEquals(1.45f, GlassRefraction.tiltShiftPx(sin(1.1f), DENSITY) / DENSITY, 0.02f);
        assertEquals(3.09f, GlassRefraction.tiltShiftPx(sin(3f), DENSITY) / DENSITY, 0.02f);
        assertEquals(4.60f, GlassRefraction.tiltShiftPx(sin(6f), DENSITY) / DENSITY, 0.02f);
        assertEquals(6.80f, GlassRefraction.tiltShiftPx(sin(18f), DENSITY) / DENSITY, 0.02f);
        assertTrue(GlassRefraction.tiltShiftPx(sin(18f), DENSITY) <= 7f * DENSITY);
        assertTrue("a pixel or more at the press rig",
            GlassRefraction.tiltShiftPx(sin(1.1f), DENSITY) >= 1f * DENSITY);
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
        // A real gradient now (it was under ±5% here), and still well short of a fold.
        assertTrue("clearly: " + near + " / " + far, near < 0.95f && far > 1.05f);
        assertTrue("within a tenth", near > 0.9f && far < 1.1f);
    }

    @Test
    public void aLargePanesEndsMoveAboutEightDpAtThePlanksTip() {
        // Research recommendation 3's starting size: the gradient moves the face's ends about
        // 8 dp at 18 degrees, against the face's mean (the quadratic's two thirds of the cap).
        float rimW = GlassRefraction.Look.rimWidthPx(DENSITY);
        float tilt = sin(18f), deep = 10000f;
        float[] end = new float[2], mid = new float[2];
        float half = 450f * DENSITY; // a whole-screen pane's half height
        float shift = GlassRefraction.tiltShiftPx(tilt, DENSITY);
        GlassRefraction.tiltOffsetPx(0f, half, 900f, half, deep, rimW, 0f, tilt, DENSITY, end);
        GlassRefraction.tiltOffsetPx(0f, 0f, 900f, half, deep, rimW, 0f, tilt, DENSITY, mid);
        assertEquals(8f, (end[1] + shift) / DENSITY, 0.05f);
        assertEquals("the ends against the middle, the whole cap", 12f, (end[1] - mid[1]) / DENSITY, 0.05f);
        // The gradient now carries more than the uniform shift does, from a press up.
        for (float deg : new float[] {1.1f, 3f, 6f, 18f}) {
            float s = sin(deg);
            float m = Math.min(GlassRefraction.TILT_MAG * GlassRefraction.tiltShare(s),
                GlassRefraction.TILT_MAG_MAX_DP * DENSITY / half);
            assertTrue(deg + "°", m * half > GlassRefraction.tiltShiftPx(s, DENSITY));
        }
    }

    @Test
    public void theFaceIsMonotoneOnEveryPaneAtEveryTilt() {
        // Schlüter & Faul 2019: once the rays cross the picture folds and stops reading as shape.
        // The bound tiltOffsetPx states: the step is over 1/4 - 2m, so m stays under an eighth.
        float mMax = GlassRefraction.TILT_MAG * GlassRefraction.tiltShare(1f);
        assertTrue("the gradient's most, " + mMax, mMax < 0.125f);
        float rimW = GlassRefraction.Look.rimWidthPx(DENSITY);
        float[] out = new float[2];
        // Panes from a 40 dp chip to a whole screen, tipped along x, along y and diagonally, from
        // a press to past the plank's tip.
        for (float halfDp : new float[] {20f, 60f, 150f, 250f, 450f}) {
            float half = halfDp * DENSITY;
            for (float deg : new float[] {1.1f, 3f, 6f, 12f, 18f, 30f, 60f, 90f}) {
                float s = sin(deg);
                for (float[] dir : new float[][] {{1f, 0f}, {0f, 1f}, {-1f, 0f}, {0.6f, 0.8f}}) {
                    float tx = s * dir[0], ty = s * dir[1];
                    // Walk the whole face along the tilt, outline to outline, through its middle.
                    float last = Float.NaN;
                    int n = 800;
                    for (int i = 0; i <= n; i++) {
                        float u = -half + 2f * half * i / n;
                        float x = u * dir[0], y = u * dir[1];
                        float inside = half - Math.abs(u);
                        GlassRefraction.tiltOffsetPx(x, y, half, half, inside, rimW, tx, ty,
                            DENSITY, out);
                        float sample = (x + out[0]) * dir[0] + (y + out[1]) * dir[1];
                        if (!Float.isNaN(last)) {
                            assertTrue(halfDp + " dp at " + deg + "° along " + dir[0] + "/" + dir[1]
                                + " folds at " + u, sample > last);
                        }
                        last = sample;
                    }
                }
            }
        }
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

    // ------------------------------------------------------------------------------ the light

    private static final float KX = GlassRefraction.KEY_LIGHT_X, KY = GlassRefraction.KEY_LIGHT_Y;

    @Test
    public void aFlatStillSlabKeepsTheRestingLightExactly() {
        float[] key = new float[2];
        GlassRefraction.keyLight(0f, 0f, 0f, 0f, DENSITY, key);
        assertEquals(KX, key[0], 0f);
        assertEquals(KY, key[1], 0f);
    }

    /**
     * The key light turned by a tilt, by hand: the light in three dimensions, (KEY_LIGHT,
     * KEY_LIGHT_RISE), turned by exactly {@code angle} about the axis across the tilt, toward the
     * edge rising toward the viewer (Rodrigues), then seen round the outline.
     */
    private static float[] turnedByHand(float tiltX, float tiltY, double angle) {
        double s = Math.hypot(tiltX, tiltY);
        double dx = tiltX / s, dy = tiltY / s;
        double[] v = {KX, KY, GlassRefraction.KEY_LIGHT_RISE};
        // The axis across the tilt, (dy, -dx, 0); a positive turn about it takes +z toward -d.
        double[] k = {dy, -dx, 0.0};
        double c = Math.cos(angle), sn = Math.sin(angle);
        double kv = k[0] * v[0] + k[1] * v[1] + k[2] * v[2];
        double[] kxv = {k[1] * v[2] - k[2] * v[1], k[2] * v[0] - k[0] * v[2], k[0] * v[1] - k[1] * v[0]};
        double x = v[0] * c + kxv[0] * sn + k[0] * kv * (1 - c);
        double y = v[1] * c + kxv[1] * sn + k[1] * kv * (1 - c);
        double l = Math.hypot(x, y);
        return new float[] {(float) (x / l), (float) (y / l)};
    }

    @Test
    public void theLightTurnsByTwiceTheTiltAboutTheTiltAxis() {
        // The law of reflection: a plate tipped by θ under a fixed light turns its reflection by
        // 2θ. 6 degrees at 3 of tilt, 36 at 18 (glass-perception research, recommendation 1).
        float[] key = new float[2];
        for (float deg : new float[] {1.1f, 3f, 6f, 18f}) {
            for (float[] dir : new float[][] {{1f, 0f}, {0f, 1f}, {-0.6f, 0.8f}, {0.8f, -0.6f}}) {
                float tx = sin(deg) * dir[0], ty = sin(deg) * dir[1];
                GlassRefraction.keyLight(tx, ty, 0f, 0f, DENSITY, key);
                float[] hand = turnedByHand(tx, ty, Math.toRadians(2.0 * deg));
                assertEquals(deg + "° x", hand[0], key[0], 1e-4f);
                assertEquals(deg + "° y", hand[1], key[1], 1e-4f);
                assertEquals("a unit vector", 1f, (float) Math.hypot(key[0], key[1]), 1e-5f);
            }
        }
        // Across the light, round the outline it turns atan(sin 2θ) at a 45-degree light: 6
        // degrees at 3 of tilt (a third of the glint's half width), 30 at 18.
        float[] across = {-KY, KX}; // a tilt at right angles to the light
        for (float[] at : new float[][] {{3f, 5.97f}, {18f, 30.4f}}) {
            GlassRefraction.keyLight(sin(at[0]) * across[0], sin(at[0]) * across[1], 0f, 0f, DENSITY, key);
            double turn = Math.toDegrees(Math.acos(Math.min(1.0, key[0] * KX + key[1] * KY)));
            assertEquals(at[0] + "°", at[1], turn, 0.1);
        }
    }

    @Test
    public void theGlintTravelsTowardTheEdgeRisingTowardTheViewer() {
        float[] key = new float[2];
        // The right edge going away: the left one rises, and the light swings toward it.
        GlassRefraction.keyLight(sin(3f), 0f, 0f, 0f, DENSITY, key);
        assertTrue(key[0] < KX);
        // The bottom edge going away: toward the top.
        GlassRefraction.keyLight(0f, sin(3f), 0f, 0f, DENSITY, key);
        assertTrue(key[1] < KY);
        // The rim's lean and the glint agree: the edge the light swings toward is the one whose
        // bend thickens (tiltLean > 0) and whose light grows (tiltLight).
        assertTrue(GlassRefraction.tiltLean(-1f, 0f, sin(3f), 0f) > 0f);
        // Further with more tilt, and back exactly when flat.
        float last = KX;
        for (int deg = 1; deg <= 18; deg++) {
            GlassRefraction.keyLight(sin(deg), 0f, 0f, 0f, DENSITY, key);
            assertTrue(deg + "°", key[0] < last);
            last = key[0];
        }
    }

    @Test
    public void aLagsLeanTurnsTheLightAsTheTiltThatLeansTheSameWay() {
        // The lag's whole lean leans an edge as a tilt of sine 1 / TILT_LEAN_GAIN does; the light
        // follows the bend, toward the edge the lag faces.
        float whole = GlassMotionLight.LAG_MAX_DP * DENSITY / GlassRefraction.LAG_LEAN_GAIN;
        float[] fromLag = new float[2], fromTilt = new float[2];
        for (float share : new float[] {0.25f, 0.5f, 1f}) {
            GlassRefraction.keyLight(0f, 0f, -whole * share, 0f, DENSITY, fromLag);
            GlassRefraction.keyLight(share / GlassRefraction.TILT_LEAN_GAIN, 0f, 0f, 0f, DENSITY, fromTilt);
            assertEquals(fromTilt[0], fromLag[0], 1e-5f);
            assertEquals(fromTilt[1], fromLag[1], 1e-5f);
            // The same lean, edge for edge.
            assertEquals(GlassRefraction.liveLean(-1f, 0f, -whole * share, 0f, 0f, 0f, DENSITY),
                GlassRefraction.liveLean(-1f, 0f, 0f, 0f, share / GlassRefraction.TILT_LEAN_GAIN, 0f, DENSITY),
                1e-5f);
        }
        // Held to a whole lean, as the bend is.
        float[] far = new float[2];
        GlassRefraction.keyLight(0f, 0f, -whole * 4f, 0f, DENSITY, far);
        GlassRefraction.keyLight(0f, 0f, -whole, 0f, DENSITY, fromLag);
        assertEquals(fromLag[0], far[0], 1e-6f);
        assertEquals(fromLag[1], far[1], 1e-6f);
        // A lag and a tilt that lean the edges opposite ways cancel.
        GlassRefraction.keyLight(0.5f / GlassRefraction.TILT_LEAN_GAIN, 0f, whole * 0.5f, 0f, DENSITY, far);
        assertEquals(KX, far[0], 1e-6f);
        assertEquals(KY, far[1], 1e-6f);
    }

    @Test
    public void theLightNeverJumpsAndStopsTurningPastItsHold() {
        float[] key = new float[2], previous = new float[2];
        for (int a = 0; a < 360; a += 10) {
            double dir = Math.toRadians(a);
            float last = Float.NaN;
            for (float deg = 0.1f; deg <= 90f; deg += 0.1f) {
                float s = sin(deg);
                GlassRefraction.keyLight(s * (float) Math.cos(dir), s * (float) Math.sin(dir), 0f, 0f,
                    DENSITY, key);
                assertEquals(1f, (float) Math.hypot(key[0], key[1]), 1e-4f);
                if (!Float.isNaN(last)) {
                    double step = Math.toDegrees(Math.acos(Math.min(1.0,
                        key[0] * previous[0] + key[1] * previous[1])));
                    assertTrue("a jump of " + step + "° at " + deg + "° toward " + a, step < 2.0);
                }
                last = deg;
                previous[0] = key[0];
                previous[1] = key[1];
            }
        }
        // Past the hold (20 degrees) the light is where the hold left it.
        float[] held = new float[2];
        GlassRefraction.keyLight(sin(20f), 0f, 0f, 0f, DENSITY, held);
        GlassRefraction.keyLight(sin(40f), 0f, 0f, 0f, DENSITY, key);
        assertEquals(held[0], key[0], 1e-3f);
        assertEquals(held[1], key[1], 1e-3f);
    }

    @Test
    public void theGlintTheCounterLobeAndTheDarkEdgeTurnTogether() {
        // Kim et al. 2011: a highlight out of step with its shape costs gloss. Every term the light
        // draws reads the one turned light: turn the outline with it and nothing changes.
        float[] key = new float[2];
        GlassRefraction.keyLight(sin(6f), sin(2f), 0f, 0f, DENSITY, key);
        double turn = Math.atan2(key[1], key[0]) - Math.atan2(KY, KX);
        for (int a = 0; a < 360; a += 15) {
            double n = Math.toRadians(a);
            float nx = (float) Math.cos(n), ny = (float) Math.sin(n);
            float tx = (float) Math.cos(n + turn), ty = (float) Math.sin(n + turn);
            // To half a percent: the resting light is a unit vector to 1e-4, the turned one
            // exactly, and the lobe's 16th power makes that a few parts in a thousand.
            float still = GlassRefraction.liveGatherShare(nx, ny, KX, KY);
            assertEquals(still, GlassRefraction.liveGatherShare(tx, ty, key[0], key[1]), 5e-3f * still);
            assertEquals(GlassRefraction.liveShadeShare(nx, ny, KX, KY),
                GlassRefraction.liveShadeShare(tx, ty, key[0], key[1]), 5e-3f);
        }
        // In the program, every live term reads uKeyLight, and nothing reads a time.
        String agsl = GlassRefraction.AGSL;
        assertTrue(agsl.contains("float c = dot(n, uKeyLight);"));
        assertTrue(agsl.contains("return 1.0 - liveKeyLobe(dot(n, uKeyLight));"));
        assertTrue(agsl.contains("liveKeyLobe(c) + kCounter * liveKeyLobe(-c)"));
        for (String time : new String[] {"uTime", "uSweep", "uPhase", "uClock"}) {
            assertTrue(time, !agsl.contains(time));
        }
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
        // The face is moved before the finger's light, and takes one sample: no new eval.
        assertTrue(agsl.indexOf("tiltOffset(p, b, inside, rimW)") < agsl.indexOf("if (uTouchActive > 0.001) {"));
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
        assertConst(agsl, "kKeyExp", GlassRefraction.Look.KEY_EXPONENT);
        assertConst(agsl, "kLiveKeyExp", GlassRefraction.Look.LIVE_KEY_EXPONENT);
        assertConst(agsl, "kKeyPeak", GlassRefraction.Look.KEY_PEAK);
        assertConst(agsl, "kCounter", GlassRefraction.Look.COUNTER);
    }

    private static void assertConst(String agsl, String name, float java) {
        Matcher m = Pattern.compile("const float " + name + " = ([0-9.]+);").matcher(agsl);
        assertTrue(name + " is a plain decimal constant", m.find());
        assertEquals(name, java, Float.parseFloat(m.group(1)), 0f);
    }
}
