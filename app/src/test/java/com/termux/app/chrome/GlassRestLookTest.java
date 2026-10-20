package com.termux.app.chrome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The rest look is the glass from before the travelling glint (commit 673ff85d4's
 * GlassRefraction), bit for bit, and the glint is the slab's motion only. On pong the narrow
 * glint at rest read as a bright smear of wallpaper colour in the top-left corner of every glass
 * surface ("it does not look good when the glass slabs are static, should it not appear only when
 * in motion?", the user, 2026-10-20). The program cannot run on the JVM, so its rest path is held
 * to the old program's text, line for line, and its Java twins to the old arithmetic.
 */
public class GlassRestLookTest {

    /** Pong's density. */
    private static final float DENSITY = 2.625f;
    private static final float D = (float) Math.sqrt(0.5);

    /** The old gatherShare, its numbers as 673ff85d4 wrote them. */
    private static float oldGatherShare(float nx, float ny) {
        float c = Math.max(nx * -0.552f + ny * -0.834f, 0f);
        float key = c > 0f ? (float) Math.pow(c, 2f) : 0f;
        return 0.25f + (1f - 0.25f) * key;
    }

    /** The old gatheredLit, as 673ff85d4 wrote it, on the old constants. */
    private static void oldGatheredLit(float r, float g, float b, float a, float insidePx,
                                       float density, float nx, float ny, float edgeGain,
                                       float[] out) {
        float luma = r * 0.2126f + g * 0.7152f + b * 0.0722f;
        float glow = insidePx >= 10f * density ? 0f
            : (float) Math.exp(-Math.max(insidePx, 0f) / (1.4f * density));
        float k = glow * oldGatherShare(nx, ny) * (1f - 0.75f * luma) * edgeGain;
        float shade = 1f - glow * 0.05f;
        float sr = r * shade, sg = g * shade, sb = b * shade;
        if (k > 0f) {
            float kMax = Math.max(a - Math.max(sr, Math.max(sg, sb)), 0f)
                / (1.6f * Math.max(sr, Math.max(sg, sb)) + 0.07f);
            float ratio = k / Math.max(kMax, 1e-6f);
            k = ratio <= 0.5f ? k : kMax * (1f - 0.5f * 0.5f / ratio);
        }
        float gain = 1f + 1.6f * k;
        float lift = 0.07f * k;
        out[0] = Math.min(a, sr * gain + lift);
        out[1] = Math.min(a, sg * gain + lift);
        out[2] = Math.min(a, sb * gain + lift);
    }

    // ------------------------------------------------------------------------- the Java twins

    @Test
    public void theRestConstantsAreTheOldOnes() {
        assertEquals(0.25f, GlassRefraction.Look.AMBIENT, 0f);
        assertEquals(2f, GlassRefraction.Look.KEY_EXPONENT, 0f);
        assertEquals(0.05f, GlassRefraction.Look.EDGE_SHADE, 0f);
        assertEquals(-0.552f, GlassRefraction.KEY_LIGHT_X, 0f);
        assertEquals(-0.834f, GlassRefraction.KEY_LIGHT_Y, 0f);
    }

    @Test
    public void theRestEdgeSharesAreTheOldOnes() {
        // The shares the old plan quotes, top-left, top, left and bottom-right.
        assertEquals(0.97f, GlassRefraction.gatherShare(-D, -D), 0.005f);
        assertEquals(0.77f, GlassRefraction.gatherShare(0f, -1f), 0.005f);
        assertEquals(0.48f, GlassRefraction.gatherShare(-1f, 0f), 0.005f);
        assertEquals(0.25f, GlassRefraction.gatherShare(D, D), 0f);
        // And bit for bit the old arithmetic all the way round.
        for (int deg = 0; deg < 360; deg++) {
            float nx = (float) Math.cos(Math.toRadians(deg)), ny = (float) Math.sin(Math.toRadians(deg));
            assertEquals("at " + deg, oldGatherShare(nx, ny), GlassRefraction.gatherShare(nx, ny), 0f);
        }
    }

    @Test
    public void atGlintWeightZeroTheShareAndTheShadeAreTheRestLookWhateverTheLight() {
        // A slab whose springs have settled draws the rest look exactly, wherever its last pose
        // left uKeyLight: the program then never reads it.
        float[][] lights = {{GlassRefraction.KEY_LIGHT_X, GlassRefraction.KEY_LIGHT_Y},
            {1f, 0f}, {0f, 1f}, {-0.8f, 0.6f}};
        for (float[] l : lights) {
            for (int deg = 0; deg < 360; deg += 3) {
                float nx = (float) Math.cos(Math.toRadians(deg)), ny = (float) Math.sin(Math.toRadians(deg));
                assertEquals(oldGatherShare(nx, ny),
                    GlassRefraction.gatherShare(nx, ny, l[0], l[1], 0f), 0f);
                assertEquals(0.05f, GlassRefraction.edgeShade(nx, ny, l[0], l[1], 0f), 0f);
            }
        }
    }

    @Test
    public void theGatheredLightAtRestIsTheOldOneBitForBit() {
        float[] got = new float[3], old = new float[3];
        float[][] colours = {{0f, 0f, 0f}, {0.2f, 0.3f, 0.8f}, {0.9f, 0.85f, 0.7f}, {1f, 1f, 1f},
            {0.5f, 0.5f, 0.5f}, {0.05f, 0.4f, 0.1f}};
        float[] gains = {1f, 1.6f, 2.25f};
        for (float[] c : colours) {
            for (float gain : gains) {
                for (int deg = 0; deg < 360; deg += 15) {
                    float nx = (float) Math.cos(Math.toRadians(deg));
                    float ny = (float) Math.sin(Math.toRadians(deg));
                    for (int i = 0; i <= 44; i++) {
                        float inside = i * 0.25f * DENSITY;
                        GlassRefraction.gatheredLit(c[0], c[1], c[2], 1f, inside, DENSITY, nx, ny,
                            gain, got);
                        oldGatheredLit(c[0], c[1], c[2], 1f, inside, DENSITY, nx, ny, gain, old);
                        for (int ch = 0; ch < 3; ch++) {
                            assertEquals(deg + "° " + i + " ch " + ch, old[ch], got[ch], 0f);
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------- the weight

    @Test
    public void theGlintWeightIsZeroAtRestWholeWhenWhollyLiveAndNeverSteps() {
        assertEquals(0f, GlassRefraction.glintWeight(0f), 0f);
        assertEquals(1f, GlassRefraction.glintWeight(1f), 0f);
        // A slab with no lag and no tilt is not live at all.
        assertEquals(0f, GlassRefraction.glintWeight(GlassRefraction.liveness(0f, 0f, 0f, 0f, DENSITY)), 0f);
        // A tilt under the rest threshold is flat, so its weight is 0 exactly.
        float[] tilt = new float[2];
        GlassRefraction.tiltVector(GlassRefraction.TILT_REST_DEG * 0.9f, 0f, tilt);
        assertEquals(0f, GlassRefraction.glintWeight(
            GlassRefraction.liveness(0f, 0f, tilt[0], tilt[1], DENSITY)), 0f);
        // The springs' last snap to rest moves it by a few thousandths at most: the lag's last
        // dp on both axes, and the tilt's last twentieth of a degree.
        float lastLag = GlassMotionLight.LAG_REST_DP * DENSITY;
        assertTrue(GlassRefraction.glintWeight(
            GlassRefraction.liveness(lastLag, lastLag, 0f, 0f, DENSITY)) < 0.005f);
        GlassRefraction.tiltVector(GlassRefraction.TILT_REST_DEG, 0f, tilt);
        assertTrue(GlassRefraction.glintWeight(
            GlassRefraction.liveness(0f, 0f, tilt[0], tilt[1], DENSITY)) < 0.001f);
        // It rises with the pose, without a step anywhere.
        float last = 0f;
        for (int i = 0; i <= 200; i++) {
            float w = GlassRefraction.glintWeight(i / 200f);
            assertTrue(w >= last);
            assertTrue("eases at " + i, w - last < 0.01f);
            last = w;
        }
        // Its scale: about an eighth at the press rig's 1.1 degrees, whole by 5 degrees.
        float rig = GlassRefraction.liveness(0f, 0f, (float) Math.sin(Math.toRadians(1.1)), 0f, DENSITY);
        assertEquals(0.13f, GlassRefraction.glintWeight(rig), 0.01f);
        float held = GlassRefraction.liveness(0f, 0f, (float) Math.sin(Math.toRadians(5.0)), 0f, DENSITY);
        assertEquals(1f, GlassRefraction.glintWeight(held), 0f);
    }

    @Test
    public void theShareMovesFromTheRestLookToTheLiveGlintWithTheWeight() {
        float kx = GlassRefraction.KEY_LIGHT_X, ky = GlassRefraction.KEY_LIGHT_Y;
        for (float[] n : new float[][] {{-D, -D}, {0f, -1f}, {-1f, 0f}, {D, D}}) {
            float rest = GlassRefraction.gatherShare(n[0], n[1]);
            float live = GlassRefraction.liveGatherShare(n[0], n[1], kx, ky);
            assertEquals(live, GlassRefraction.gatherShare(n[0], n[1], kx, ky, 1f), 1e-6f);
            float last = rest;
            for (int i = 1; i <= 100; i++) {
                float share = GlassRefraction.gatherShare(n[0], n[1], kx, ky, i / 100f);
                assertTrue("between the two", share >= Math.min(rest, live) - 1e-6f
                    && share <= Math.max(rest, live) + 1e-6f);
                assertTrue("no step", Math.abs(share - last) <= Math.abs(live - rest) / 100f + 1e-5f);
                last = share;
            }
            assertEquals(GlassRefraction.Look.LIVE_EDGE_SHADE
                    * GlassRefraction.liveShadeShare(n[0], n[1], kx, ky),
                GlassRefraction.edgeShade(n[0], n[1], kx, ky, 1f), 1e-6f);
        }
        // The top-left corner at rest against a whole glint: the smear the user saw is the live
        // light only.
        assertEquals(0.97f, GlassRefraction.gatherShare(-D, -D, kx, ky, 0f), 0.005f);
        assertEquals(1.63f, GlassRefraction.gatherShare(-D, -D, kx, ky, 1f), 0.01f);
    }

    // ---------------------------------------------------------------------------- the program

    @Test
    public void theProgramsRestPathIsTheOldProgramLineForLine() {
        String agsl = GlassRefraction.AGSL;
        // These lines, each exactly as 673ff85d4's program had it.
        String[] old = {
            "const float2 kKeyLight = float2(-0.552, -0.834);\n",
            "const float kAmbient = 0.25;\n",
            "const float kKeyExp = 2.0;\n",
            "const float kEdgeShade = 0.05;\n",
            "float gatherShare(float2 n) {\n"
                + "    float c = max(dot(n, kKeyLight), 0.0);\n"
                + "    return kAmbient + (1.0 - kAmbient) * (c > 0.0 ? pow(c, kKeyExp) : 0.0);\n"
                + "}\n",
            "        float k = glow * (gatherShare(n) + 1.5 * touchG) * (1.0 - kPaleCut * pale) * uEdgeGain;\n",
            "            col.rgb = col.rgb * half(1.0 - glow * kEdgeShade);\n",
        };
        for (String line : old) assertTrue(line, agsl.contains(line));
        // The glint's weight is 0 unless the slab is live, and is set only inside the live gate.
        assertTrue(agsl.contains("    float glintW = 0.0;\n"));
        int liveGate = agsl.indexOf("    if (live) {\n");
        String setLine = "        glintW = smoothstep(0.0, 1.0, liveK);\n";
        int set = agsl.indexOf(setLine);
        assertTrue(liveGate > 0 && set > liveGate);
        assertEquals("set once", set, agsl.lastIndexOf(setLine));
        assertEquals("declared at 0 and assigned nowhere else", 2,
            agsl.split("glintW = ", -1).length - 1);
        assertTrue(agsl.contains("    bool live = uLag.x != 0.0 || uLag.y != 0.0 || tilted;\n"));
        // Every live light term is behind glintW > 0; the rest path's shade is its else.
        String liveK = "        if (glintW > 0.0) {\n"
            + "            k = glow * (mix(gatherShare(n), liveGatherShare(n), glintW) + 1.5 * touchG)\n"
            + "                * (1.0 - kPaleCut * pale) * uEdgeGain;\n"
            + "        }\n";
        String liveShade = "        if (glintW > 0.0) {\n"
            + "            col.rgb = col.rgb * half(1.0 - glow * mix(kEdgeShade, kLiveEdgeShade * liveShadeShare(n), glintW));\n"
            + "        } else {\n"
            + "            col.rgb = col.rgb * half(1.0 - glow * kEdgeShade);\n"
            + "        }\n";
        assertTrue(agsl.contains(liveK));
        assertTrue(agsl.contains(liveShade));
        int main = agsl.indexOf("half4 main(");
        assertEquals("the live share is called once, inside its gate",
            agsl.indexOf("liveGatherShare(n)", main), agsl.lastIndexOf("liveGatherShare(n)"));
        assertEquals("the live shade is called once, inside its gate",
            agsl.indexOf("liveShadeShare(n)", main), agsl.lastIndexOf("liveShadeShare(n)"));
        assertTrue(agsl.indexOf("liveGatherShare(n)", main) > agsl.indexOf(liveK));
        assertTrue(agsl.indexOf("liveShadeShare(n)", main) > agsl.indexOf(liveShade));
        // uKeyLight is read by the live functions alone, never in main.
        assertEquals(-1, agsl.indexOf("uKeyLight", main));
    }

    @Test
    public void theRestLookHoldsWhereverTheProgramIsBuiltOnceAndNeverMoved() {
        // The dock and the strip run the program as a RenderEffect built once: never a lag, never
        // a tilt. Their weight is 0 for good, so they wear the rest look, the old one.
        float w = GlassRefraction.glintWeight(GlassRefraction.liveness(0f, 0f, 0f, 0f, DENSITY));
        assertEquals(0f, w, 0f);
        for (int deg = 0; deg < 360; deg += 5) {
            float nx = (float) Math.cos(Math.toRadians(deg)), ny = (float) Math.sin(Math.toRadians(deg));
            assertEquals(oldGatherShare(nx, ny), GlassRefraction.gatherShare(nx, ny,
                GlassRefraction.KEY_LIGHT_X, GlassRefraction.KEY_LIGHT_Y, w), 0f);
        }
    }
}
