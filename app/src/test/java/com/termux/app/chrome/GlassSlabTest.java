package com.termux.app.chrome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The slab's numbers, their property, and the Java mirrors of the program's lens. */
public class GlassSlabTest {

    /** Pong, 420 dpi. */
    private static final float PONG = 2.625f;
    private static final GlassSlab SLAB = GlassSlab.DEFAULT;
    /** The default Depth, dp. */
    private static final float DEPTH_DP = 9f;

    @Test
    public void theDefaultsAreAsTunedOnPong20261123() {
        // Tuned on pong by the developer, 2026-11-23: a deep zone (32 dp) eased in (p = 4) at index
        // 1.7, no fixed light (no specular), the sky tone cut to a floor, the wallpaper reflected.
        assertEquals(32f, SLAB.lensDp, 0f);
        assertEquals(0.25f, SLAB.share, 0f);
        assertEquals(1.7f, SLAB.ior, 0f);
        assertEquals(0.04f, SLAB.f0, 0f);
        // F0 stays the 4% it was tuned at; it is no longer glass's own at 1.7 (6.7%).
        assertEquals(0.1f, SLAB.env, 0f);
        assertEquals(0f, SLAB.spec, 0f);
        assertEquals(24f, SLAB.power, 0f);
        assertEquals(4f, SLAB.profile, 0f);
        assertEquals(1f, SLAB.disp, 0f);
        assertEquals(2f, SLAB.reflect, 0f);
    }

    @Test
    public void thePropertyOverridesNamedKeysAndFallsBackWhole() {
        assertSame(GlassSlab.DEFAULT, GlassSlab.parse(null));
        assertSame(GlassSlab.DEFAULT, GlassSlab.parse(" "));
        assertSame(GlassSlab.DEFAULT, GlassSlab.parse(GlassSlab.DEFAULT.toString()));
        GlassSlab slab = GlassSlab.parse(" lens=20, IOR=1.6 ,spec=0.3,profile=3,");
        assertEquals(20f, slab.lensDp, 0f);
        assertEquals(1.6f, slab.ior, 0f);
        assertEquals(0.3f, slab.spec, 0f);
        assertEquals(3f, slab.profile, 0f);
        assertEquals(GlassSlab.SHARE, slab.share, 0f);
        assertEquals(GlassSlab.ENV, slab.env, 0f);
        assertEquals(slab, GlassSlab.parse(slab.toString()));
        String[] bad = {"lens", "=2", "lens=", "lens=abc", "lens=NaN", "lens=-1", "lens=49",
            "share=0.6", "ior=0.9", "ior=2.6", "f0=1.1", "env=-0.1", "spec=3", "power=0",
            "profile=1", "disp=11", "width=3", "lens=16,bogus=1", "reflect=-1", "reflect=9"};
        assertEquals(3f, GlassSlab.parse("reflect=3").reflect, 0f);
        assertEquals(0f, GlassSlab.parse("reflect=0").reflect, 0f);
        assertEquals(GlassSlab.REFLECT, slab.reflect, 0f);
        for (String value : bad) assertSame(value, GlassSlab.DEFAULT, GlassSlab.parse(value));
    }

    @Test
    public void theProgramCarriesTheNumbersAsConstants() {
        String agsl = GlassRefraction.agsl(GlassRim.DEFAULT, GlassSlab.parse("lens=20,ior=1.6"));
        assertTrue(agsl.contains("const float kLensDp = 20.0;\n"));
        assertTrue(agsl.contains("const float kIor = 1.6;\n"));
        assertTrue(GlassRefraction.AGSL.contains("const float kLensDp = 32.0;\n"));
        assertTrue(GlassRefraction.AGSL.contains("const float kLensShare = 0.25;\n"));
        assertTrue(GlassRefraction.AGSL.contains("const float kSlabP = 4.0;\n"));
        assertTrue(GlassRefraction.AGSL.contains("const float kEnv = 0.1;\n"));
        assertTrue("no specular", GlassRefraction.AGSL.contains("const float kSpecK = 0.0;\n"));
        assertTrue(GlassRefraction.AGSL.contains("const float3 kHalf = float3("));
        assertTrue(GlassRefraction.agsl(GlassRim.DEFAULT, GlassSlab.parse("reflect=3"))
            .contains("const float kEnvK = 3.0;\n"));
    }

    // ------------------------------------------------------------------- W and T by size

    @Test
    public void aLargePaneAtPongTakesTheWholeLensAndTheDepth() {
        float shortSide = 360f * PONG;
        assertEquals(32f * PONG, SLAB.lensWidthPx(shortSide, PONG), 1e-3f);
        assertEquals("84 px on pong", 84f, SLAB.lensWidthPx(shortSide, PONG), 1e-3f);
        assertEquals(DEPTH_DP * PONG, SLAB.thicknessPx(DEPTH_DP * PONG, shortSide, PONG), 1e-3f);
    }

    @Test
    public void aChipIsTheSameSlabScaledDown() {
        float shortSide = 32f * PONG;
        float w = SLAB.lensWidthPx(shortSide, PONG);
        assertEquals("a quarter of its short side", 8f * PONG, w, 1e-3f);
        float t = SLAB.thicknessPx(DEPTH_DP * PONG, shortSide, PONG);
        assertEquals("a quarter of the Depth, as its lens is a quarter of the large one",
            DEPTH_DP / 4f * PONG, t, 1e-3f);
        assertEquals("the same slope as a large pane's", DEPTH_DP / SLAB.lensDp, t / w, 1e-4f);
        // Its deepest bend stays well inside its half height: it stays readable.
        float deepest = SLAB.shiftPx(0f, w, t);
        assertTrue(deepest < 0.5f * shortSide / 2f);
    }

    @Test
    public void thePageBarTakesItsShareToo() {
        float shortSide = 30f * PONG;
        assertEquals(7.5f * PONG, SLAB.lensWidthPx(shortSide, PONG), 1e-3f);
        assertEquals(DEPTH_DP * 7.5f / SLAB.lensDp * PONG,
            SLAB.thicknessPx(DEPTH_DP * PONG, shortSide, PONG), 1e-3f);
    }

    @Test
    public void theThicknessIsHeldSoTheBendNeverCrossesTheCentreLine() {
        float shortSide = 64f * PONG;
        float deep = SLAB.thicknessPx(50f * PONG, shortSide, PONG);
        float bound = deep * (float) Math.sqrt(SLAB.ior * SLAB.ior - 1f);
        assertTrue(bound <= shortSide / 2f + 1e-3f);
        assertEquals(shortSide / 2f, bound, 1e-2f);
    }

    @Test
    public void depthZeroAndLensZeroBendNothing() {
        assertEquals(0f, SLAB.thicknessPx(0f, 400f * PONG, PONG), 0f);
        for (float inside = 0f; inside < 50f; inside += 1f)
            assertEquals(0f, SLAB.shiftPx(inside, 42f, 0f), 0f);
        GlassSlab none = GlassSlab.parse("lens=0");
        assertEquals(0f, none.lensWidthPx(400f * PONG, PONG), 0f);
        assertEquals(0f, none.thicknessPx(DEPTH_DP * PONG, 400f * PONG, PONG), 0f);
    }

    // ------------------------------------------------------------------- profile and refraction

    @Test
    public void theProfileIsConvexAndMeetsTheFaceWithNoStepInItsSlope() {
        float p = SLAB.profile;
        assertEquals(0f, GlassSlab.profile(0f, p), 0f);
        assertEquals(1f, GlassSlab.profile(1f, p), 0f);
        assertEquals("flat where it meets the face", 0f, GlassSlab.profileSlope(1f, p), 0f);
        assertTrue("and still nearly flat just short of it", GlassSlab.profileSlope(0.99f, p) < 0.15f);
        float last = Float.MAX_VALUE;
        for (int i = 1; i <= 100; i++) {
            float slope = GlassSlab.profileSlope(i / 100f, p);
            assertTrue("convex: the slope only falls toward the face", slope <= last);
            last = slope;
        }
        assertTrue("steep at the outline", GlassSlab.profileSlope(0f, p) > 10f);
        // The slope is the profile's own derivative.
        for (int i = 5; i < 95; i += 10) {
            float e = i / 100f;
            float numeric = (GlassSlab.profile(e + 1e-3f, p) - GlassSlab.profile(e - 1e-3f, p)) / 2e-3f;
            assertEquals(numeric, GlassSlab.profileSlope(e, p), 1e-2f * Math.max(1f, numeric));
        }
    }

    @Test
    public void snellIsExactAndBoundsItself() {
        float n = SLAB.ior;
        assertEquals(0f, GlassSlab.snellTan(0f, n), 0f);
        for (float slope : new float[] {0.1f, 0.5f, 1f, 3f, 10f}) {
            double in = Math.atan(slope);
            double out = Math.asin(Math.sin(in) / n);
            assertEquals((float) Math.tan(in - out), GlassSlab.snellTan(slope, n), 1e-5f);
        }
        float bound = (float) Math.sqrt(n * n - 1f);
        float last = 0f;
        for (float slope = 0f; slope < 1000f; slope = slope * 1.5f + 0.01f) {
            float t = GlassSlab.snellTan(slope, n);
            assertTrue(t >= last - 1e-6f);
            assertTrue(t <= bound + 1e-4f);
            last = t;
        }
        assertEquals(bound, GlassSlab.snellTan(1.0e5f, n), 1e-3f);
        assertEquals("index 1 bends nothing", 0f, GlassSlab.snellTan(3f, 1f), 1e-6f);
    }

    @Test
    public void theShiftIsZeroOnTheFaceAndBoundedInTheZone() {
        float w = SLAB.lensWidthPx(360f * PONG, PONG);
        float t = SLAB.thicknessPx(DEPTH_DP * PONG, 360f * PONG, PONG);
        float bound = t * (float) Math.sqrt(SLAB.ior * SLAB.ior - 1f);
        for (float inside = w; inside < 4f * w; inside += 0.5f)
            assertEquals("face at " + inside, 0f, SLAB.shiftPx(inside, w, t), 0f);
        for (float inside = 0f; inside < w; inside += 0.25f) {
            float shift = SLAB.shiftPx(inside, w, t);
            assertTrue("bent at " + inside, shift > 0f);
            assertTrue("bounded at " + inside, shift <= bound + 1e-3f);
        }
        // Strong at the border, as a thick slab's: at the outline about 10 dp on pong.
        assertEquals(bound, SLAB.shiftPx(0f, w, t), 0.1f * bound);
        assertTrue("still a px or more a quarter in", SLAB.shiftPx(w / 4f, w, t) > 1f);
        assertTrue("easing out toward the face", SLAB.shiftPx(0.9f * w, w, t) < 1.5f);
    }

    @Test
    public void dispersionPartsTheIndicesOnlyWhenAsked() {
        assertEquals(SLAB.ior, SLAB.iorRed(0f), 0f);
        assertEquals(SLAB.ior, SLAB.iorBlue(0f), 0f);
        assertEquals(1.69f, SLAB.iorRed(1f), 1e-6f);
        assertEquals(1.72f, SLAB.iorBlue(1f), 1e-6f);
        float w = 42f, t = 23.6f;
        for (float inside = 0f; inside < w; inside += 1f) {
            float r = SLAB.shiftPx(inside, w, t, SLAB.iorRed(1f));
            float b = SLAB.shiftPx(inside, w, t, SLAB.iorBlue(1f));
            assertTrue("blue bends further than red", b >= r);
        }
        assertEquals("nothing on the face", 0f, SLAB.shiftPx(w, w, t, SLAB.iorBlue(1f))
            - SLAB.shiftPx(w, w, t, SLAB.iorRed(1f)), 0f);
    }

    // ------------------------------------------------------------------- the light

    @Test
    public void fresnelRunsFromGlassesOwnReflectanceToAMirrorAtGrazing() {
        assertEquals(SLAB.f0, SLAB.fresnel(0f), 1e-6f);
        float last = SLAB.f0;
        for (float slope = 0.1f; slope < 500f; slope *= 1.5f) {
            float f = SLAB.fresnel(slope);
            assertTrue(f >= last);
            last = f;
        }
        assertEquals(1f, SLAB.fresnel(1.0e5f), 1e-3f);
    }

    @Test
    public void theRimLineLightsTheOutlineOnceNotTwice() {
        float lin = 0.3f;
        assertEquals("under the rim's whole line Fresnel stands back", lin,
            SLAB.lit(lin, 40f, 1f, 0f), 1e-6f);
        assertTrue("inside it, the steep bevel reflects the sky", SLAB.lit(lin, 40f, 0f, 0f) != lin);
        assertEquals("the face is untouched", lin, SLAB.lit(lin, 0f, 0f, 0f), 1e-6f);
        assertTrue("held at 1", SLAB.lit(1f, 2f, 0f, 2f) <= 1f);
    }

    @Test
    public void theSpecularIsTheLensZonesFacingTheLight() {
        // The tuning key's specular, at the strength it shipped with before 2026-11-23.
        GlassSlab lit02 = GlassSlab.parse("spec=0.2");
        assertEquals("none on the flat face", 0f, lit02.specular(-0.55f, -0.83f, 0f), 1e-6f);
        float lit = lit02.specular(GlassRefraction.KEY_LIGHT_X, GlassRefraction.KEY_LIGHT_Y, 0.41f);
        float away = lit02.specular(-GlassRefraction.KEY_LIGHT_X, -GlassRefraction.KEY_LIGHT_Y, 0.41f);
        assertTrue(lit > 0.15f);
        assertTrue(lit <= lit02.spec + 1e-3f);
        assertTrue(away < 0.01f * lit);
        // Along the top and the left edges, not a spot in the corner alone.
        assertTrue(lit02.specular(0f, -1f, 0.41f) > 0.25f * lit);
        assertTrue(lit02.specular(-1f, 0f, 0.41f) > 0.05f * lit);
    }

    @Test
    public void theShippedSlabHasNoFixedLight() {
        for (int deg = 0; deg < 360; deg += 15) {
            float nx = (float) Math.cos(Math.toRadians(deg)), ny = (float) Math.sin(Math.toRadians(deg));
            for (float slope : new float[] {0.1f, 0.41f, 2f, 40f}) {
                assertEquals(0f, SLAB.specular(nx, ny, slope), 0f);
            }
        }
    }

    @Test
    public void theWallpapersShareIsNothingWhereTheZoneMeetsTheFaceAndGrowsToTheOutline() {
        float w = 24f * PONG, t = DEPTH_DP * PONG;
        assertEquals("flat: nothing", 0f, SLAB.envWeight(0f, 0f), 0f);
        // Where the zone meets the face the slope is all but 0: no step against the face.
        assertEquals(0f, SLAB.envWeight(SLAB.slopeAt(0.999f * w, w, t), 0f), 1e-4f);
        float last = 0f;
        for (float inside = w; inside >= 0f; inside -= 0.25f) {
            float weight = SLAB.envWeight(SLAB.slopeAt(inside, w, t), 0f);
            assertTrue("rises toward the outline at " + inside, weight >= last - 1e-6f);
            last = weight;
        }
        assertTrue("strongest at the steep outer edge", last > 0.5f);
        assertEquals("standing back under the rim's whole line", 0f, SLAB.envWeight(40f, 1f), 0f);
        // Wholly steep, it is Fresnel itself.
        assertEquals(SLAB.fresnel(40f), SLAB.envWeight(40f, 0f), 1e-6f);
    }

    /**
     * The lens zone meets the flat face with no step, on a dark backdrop too: whole head-on Fresnel
     * at the zone's inner edge drew a lighter ring the zone's width in from the outline, a hard
     * darker rectangle across the control bar (luma 33 against 24 on the emulator, 2026-11-28).
     */
    @Test
    public void theZoneMeetsTheFaceWithNoStepOverADarkBackdrop() {
        float w = 24f * PONG, t = DEPTH_DP * PONG;
        for (float lin : new float[] {0.005f, 0.009f, 0.05f, 0.3f}) {
            // The flat face is what the program leaves untouched: the picture itself.
            float face = lin;
            float edge = SLAB.lit(lin, SLAB.slopeAt(0.999f * w, w, t), 0f, 0f, 0.02f, 1f);
            assertEquals("at " + lin, face, edge, 2e-5f);
            // Across the zone's flat inner part the light rises with no jump.
            float last = edge;
            for (float inside = 0.999f * w; inside >= 0.5f * w; inside -= 0.25f) {
                float here = SLAB.lit(lin, SLAB.slopeAt(inside, w, t), 0f, 0f, 0.02f, 1f);
                assertTrue("no jump at " + inside, Math.abs(here - last) < 2e-4f);
                last = here;
            }
        }
    }

    @Test
    public void theProgramUsesTheSameShareForTheSkyAsTheJava() {
        String program = GlassRefraction.AGSL;
        assertTrue(program.contains("float envW = max(fres - kF0 * (1.0 - steepK), 0.0) * (1.0 - lineW);"));
        assertTrue(program.contains("fres = envW;"));
    }

    @Test
    public void theZoneReflectsColourAndNeverMultipliesThePictureBeneath() {
        float lin = 0.2f, slope = 3f;
        float none = SLAB.lit(lin, slope, 0f, 0f);
        assertEquals("a black surround brings nothing", none, SLAB.lit(lin, slope, 0f, 0f, 0f, 1f), 0f);
        float bright = SLAB.lit(lin, slope, 0f, 0f, 0.8f, 1f);
        assertEquals("a bright one adds its own light, by the weight and reflect",
            none + SLAB.envWeight(slope, 0f) * SLAB.reflect * 0.8f, bright, 1e-6f);
        // Additive: the same surround adds the same light over a dark or a bright picture.
        assertEquals(SLAB.lit(0.6f, slope, 0f, 0f, 0.8f, 1f) - SLAB.lit(0.6f, slope, 0f, 0f),
            bright - none, 1e-6f);
        assertEquals("Shine scales it", none + 2f * (bright - none),
            SLAB.lit(lin, slope, 0f, 0f, 0.8f, 2f), 1e-6f);
        assertEquals("Shine 0 keeps none of it", none, SLAB.lit(lin, slope, 0f, 0f, 0.8f, 0f), 0f);
        assertEquals("reflect 0 keeps none of it", GlassSlab.parse("reflect=0").lit(lin, slope, 0f, 0f),
            GlassSlab.parse("reflect=0").lit(lin, slope, 0f, 0f, 0.8f, 1f), 0f);
        assertTrue("held at 1", SLAB.lit(1f, 40f, 0f, 0f, 1f, 2.5f) <= 1f);
    }

    @Test
    public void theReflectedPatchIsTheMirrorAcrossTheOutlineAndAStepFurther() {
        assertEquals(GlassSlab.ENV_REACH_DP * PONG, SLAB.envTapPx(0f, PONG), 1e-4f);
        assertEquals(2f * 10f + GlassSlab.ENV_REACH_DP * PONG, SLAB.envTapPx(10f, PONG), 1e-4f);
        assertEquals("never inward", GlassSlab.ENV_REACH_DP * PONG, SLAB.envTapPx(-3f, PONG), 1e-4f);
    }

    @Test
    public void steepnessFollowsTheBendFromNothingAtTheFaceToWholeOutward() {
        assertEquals(0f, SLAB.steepness(0f), 0f);
        assertEquals(0f, SLAB.bendShare(0f), 0f);
        assertEquals(1f, SLAB.bendShare(1.0e5f), 1e-3f);
        assertEquals(1f, SLAB.steepness(1.0e5f), 0f);
        float last = 0f;
        for (float slope = 0.001f; slope < 100f; slope *= 1.3f) {
            float s = SLAB.steepness(slope);
            assertTrue(s >= last);
            last = s;
        }
    }

    // ------------------------------------------------------------------- the one field

    @Test
    public void theFieldsNormalIsContinuousThroughTheCornerArc() {
        float bx = 100f, by = 60f, cr = 24f;
        float inside = 6f;
        float[] n = new float[2];
        float[] prev = null;
        // Walk round the top-right corner at a fixed depth, from the top edge to the right edge.
        int steps = 400;
        float arcR = cr - inside;
        for (int i = 0; i <= steps; i++) {
            double a = Math.PI / 2 * i / steps;
            float px = bx - cr + arcR * (float) Math.sin(a);
            float py = -(by - cr) - arcR * (float) Math.cos(a);
            assertEquals(inside, GlassRefraction.insidePx(px, py, bx, by, cr), 1e-3f);
            GlassRefraction.rectNormal(n, px, py, bx, by, cr);
            assertEquals(1f, (float) Math.hypot(n[0], n[1]), 1e-4f);
            if (prev != null) {
                float turn = (float) Math.acos(Math.min(1f, n[0] * prev[0] + n[1] * prev[1]));
                assertTrue("no step at " + i, turn < 2f * (float) (Math.PI / 2 / steps) + 1e-3f);
            }
            prev = new float[] {n[0], n[1]};
        }
        // And it meets the straights' normals at each end of the arc.
        GlassRefraction.rectNormal(n, bx - cr, -(by - inside), bx, by, cr);
        assertEquals(0f, n[0], 1e-4f);
        assertEquals(-1f, n[1], 1e-4f);
        GlassRefraction.rectNormal(n, bx - inside, -(by - cr), bx, by, cr);
        assertEquals(1f, n[0], 1e-4f);
        assertEquals(0f, n[1], 1e-4f);
    }

    @Test
    public void theRestingBendFollowsTheSlabOnEverySideAlike() {
        float bx = 180f * PONG, by = 300f * PONG, cr = 24f * PONG;
        float depth = DEPTH_DP * PONG;
        float[] out = new float[2];
        float w = SLAB.lensWidthPx(2f * bx, PONG);
        float t = SLAB.thicknessPx(depth, 2f * bx, PONG);
        float inside = 0.25f * w;
        float expected = SLAB.shiftPx(inside, w, t);
        GlassRefraction.restBendOffset(out, 0f, -by + inside, bx, by, cr, depth, 0f, 0f, PONG);
        assertEquals("top: inward, down", expected, out[1], 1e-3f);
        assertEquals(0f, out[0], 1e-6f);
        GlassRefraction.restBendOffset(out, -bx + inside, 0f, bx, by, cr, depth, 0f, 0f, PONG);
        assertEquals("left: inward, right", expected, out[0], 1e-3f);
        GlassRefraction.restBendOffset(out, 0f, 0f, bx, by, cr, depth, 0f, 0f, PONG);
        assertEquals("face", 0f, out[0], 0f);
        assertEquals("face", 0f, out[1], 0f);
    }
}
