package com.termux.app.chrome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The rest look is the thin rim's (#314, {@link GlassRim}) under the fixed key light, and the
 * glint is the slab's motion only. On pong the narrow glint at rest read as a bright smear in the
 * top-left corner of every glass surface ("it does not look good when the glass slabs are static,
 * should it not appear only when in motion?", the user, 2026-10-20). The program cannot run on
 * the JVM, so its rest path is held to its text and its Java twins to the rim's arithmetic.
 *
 * <p>Until #314 this pinned the gathered light of commit 673ff85d4 bit for bit; that light (the
 * backdrop's colour multiplied at the outline) is gone, so the pin is now the rim's rest lobe:
 * the thin-edge experiment's {@code c²} toward the key light over an even baseline.</p>
 */
public class GlassRestLookTest {

    /** Pong's density. */
    private static final float DENSITY = 2.625f;
    private static final float D = (float) Math.sqrt(0.5);

    /** The rest lobe, as the thin-edge experiment (86d023b6d) wrote it: c² toward the key light. */
    private static float restLobe(float nx, float ny) {
        float c = nx * -0.552f + ny * -0.834f;
        return c > 0f ? c * c : 0f;
    }

    // ------------------------------------------------------------------------- the Java twins

    @Test
    public void theRestConstantsAreTheFixedLight() {
        assertEquals(2f, GlassRefraction.Look.KEY_EXPONENT, 0f);
        assertEquals(-0.552f, GlassRefraction.KEY_LIGHT_X, 0f);
        assertEquals(-0.834f, GlassRefraction.KEY_LIGHT_Y, 0f);
    }

    @Test
    public void theRestLobeIsTheThinEdgesAllRound() {
        assertEquals(0.97f, GlassRefraction.rimLobe(-D, -D), 0.01f);
        assertEquals(0.70f, GlassRefraction.rimLobe(0f, -1f), 0.01f);
        assertEquals(0.30f, GlassRefraction.rimLobe(-1f, 0f), 0.01f);
        assertEquals(0f, GlassRefraction.rimLobe(D, D), 0f);
        for (int deg = 0; deg < 360; deg++) {
            float nx = (float) Math.cos(Math.toRadians(deg)), ny = (float) Math.sin(Math.toRadians(deg));
            assertEquals("at " + deg, restLobe(nx, ny), GlassRefraction.rimLobe(nx, ny), 1e-6f);
            // Every side keeps the baseline whatever the lobe.
            assertTrue(GlassRim.DEFAULT.white(GlassRefraction.rimLobe(nx, ny)) >= GlassRim.DEFAULT.base);
        }
    }

    @Test
    public void atGlintWeightZeroTheLobeIsTheRestLookWhateverTheLight() {
        // A slab whose springs have settled draws the rest look exactly, wherever its last pose
        // left uKeyLight: the program then never reads it.
        float[][] lights = {{GlassRefraction.KEY_LIGHT_X, GlassRefraction.KEY_LIGHT_Y},
            {1f, 0f}, {0f, 1f}, {-0.8f, 0.6f}};
        for (float[] l : lights) {
            for (int deg = 0; deg < 360; deg += 3) {
                float nx = (float) Math.cos(Math.toRadians(deg)), ny = (float) Math.sin(Math.toRadians(deg));
                assertEquals(GlassRefraction.rimLobe(nx, ny),
                    GlassRefraction.rimLobe(nx, ny, l[0], l[1], 0f), 0f);
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
    public void theLobeMovesFromTheRestLookToTheLiveGlintWithTheWeight() {
        float kx = GlassRefraction.KEY_LIGHT_X, ky = GlassRefraction.KEY_LIGHT_Y;
        for (float[] n : new float[][] {{-D, -D}, {0f, -1f}, {-1f, 0f}, {D, D}}) {
            float rest = GlassRefraction.rimLobe(n[0], n[1]);
            float live = GlassRefraction.liveRimLobe(n[0], n[1], kx, ky);
            assertEquals(live, GlassRefraction.rimLobe(n[0], n[1], kx, ky, 1f), 1e-6f);
            float last = rest;
            for (int i = 1; i <= 100; i++) {
                float lobe = GlassRefraction.rimLobe(n[0], n[1], kx, ky, i / 100f);
                assertTrue("between the two", lobe >= Math.min(rest, live) - 1e-6f
                    && lobe <= Math.max(rest, live) + 1e-6f);
                assertTrue("no step", Math.abs(lobe - last) <= Math.abs(live - rest) / 100f + 1e-5f);
                last = lobe;
            }
        }
        // The top-left corner at rest against a whole glint: the smear the user saw is the live
        // light only.
        assertEquals(0.97f, GlassRefraction.rimLobe(-D, -D, kx, ky, 0f), 0.01f);
        assertTrue(GlassRefraction.rimLobe(-D, -D, kx, ky, 1f) > 1.5f);
    }

    // ---------------------------------------------------------------------------- the program

    @Test
    public void theProgramsRestPathIsTheRimUnderTheFixedLight() {
        String agsl = GlassRefraction.AGSL;
        String[] rest = {
            "const float2 kKeyLight = float2(-0.552, -0.834);\n",
            "const float kKeyExp = 2.0;\n",
            "float rimLobe(float c) {\n"
                + "    return c > 0.0 ? pow(c, kKeyExp) : 0.0;\n"
                + "}\n",
            "        float rc = dot(n, kKeyLight);\n"
                + "        float lobe = rimLobe(rc);\n"
                + "        float away = rc < 0.0 ? rc * rc : 0.0;\n",
        };
        for (String line : rest) assertTrue(line, agsl.contains(line));
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
        // Every live light term is behind glintW > 0.
        String liveLobe = "        if (glintW > 0.0) {\n"
            + "            float lc = dot(n, uKeyLight);\n"
            + "            lobe = mix(lobe, kKeyPeak * (liveKeyLobe(lc) + kCounter * liveKeyLobe(-lc)), glintW);\n"
            + "            away = mix(away, lc < 0.0 ? lc * lc : 0.0, glintW);\n"
            + "        }\n";
        assertTrue(agsl.contains(liveLobe));
        // uKeyLight is read in main once, inside that gate.
        int main = agsl.indexOf("half4 main(");
        int read = agsl.indexOf("uKeyLight", main);
        assertTrue(read > agsl.indexOf(liveLobe));
        assertEquals(read, agsl.lastIndexOf("uKeyLight"));
    }

    @Test
    public void theRestLookHoldsWhereverTheProgramIsBuiltOnceAndNeverMoved() {
        // The dock and the strip run the program as a RenderEffect built once: never a lag, never
        // a tilt. Their weight is 0 for good, so they wear the rest look.
        float w = GlassRefraction.glintWeight(GlassRefraction.liveness(0f, 0f, 0f, 0f, DENSITY));
        assertEquals(0f, w, 0f);
        for (int deg = 0; deg < 360; deg += 5) {
            float nx = (float) Math.cos(Math.toRadians(deg)), ny = (float) Math.sin(Math.toRadians(deg));
            assertEquals(restLobe(nx, ny), GlassRefraction.rimLobe(nx, ny,
                GlassRefraction.KEY_LIGHT_X, GlassRefraction.KEY_LIGHT_Y, w), 1e-6f);
        }
    }
}
