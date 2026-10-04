package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.wallpaper.living.LivingRecipe;
import com.termux.app.chrome.wallpaper.living.Manifest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Map;

/**
 * The living still's AGSL contract for both programs (the composite and the effects program), the
 * rest-pose rule, the cover mapping and how a recipe becomes uniforms. The shaders themselves need a
 * GPU; these check what the JVM can: the sources' shape.
 */
public class LivingStillTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final String[] UNIFORMS = {
        "uniform float2 uResolution", "uniform float uTime", "uniform float uPhase", "uniform float uEnergy", "uniform float uDim",
        "layout(color) uniform half4 uPalette0", "layout(color) uniform half4 uPalette1",
        "layout(color) uniform half4 uPalette2", "layout(color) uniform half4 uPalette3",
        "uniform float4 uMomentRect0", "uniform float2 uMomentState0",
        "uniform float4 uMomentRect1", "uniform float2 uMomentState1",
        "half4 main(float2 "
    };

    private LivingStill still(LivingRecipe recipe) {
        File dir = new File(tmp.getRoot(), "0123456789abcdef");
        assertTrue(dir.mkdirs() || dir.isDirectory());
        return new LivingStill(new Manifest(dir, recipe));
    }

    private static LivingRecipe fullRecipe() {
        LivingRecipe r = new LivingRecipe();
        r.swaySpeed = 1f;
        r.swayAmp = 0.005f;
        r.waterMode = LivingRecipe.WATER_LAKE;
        r.waterParams.put("freqX", 5f);
        r.waterParams.put("freqY", 26f);
        r.waterParams.put("ampX", 0.0045f);
        r.waterParams.put("ampY", 0.0035f);
        r.skyFlow = 0.035f;
        r.pour = 1f;
        r.glowMode = LivingRecipe.GLOW_TRAILS;
        r.glowGain = 1.2f;
        r.glowTrailAngleDeg = 90f;
        r.mistColour = new float[] {0.2f, 0.4f, 0.6f};
        r.mistAmount = 0.5f;
        r.particles = LivingRecipe.PARTICLES_FIREFLIES;
        r.intensity = 1.25f;
        return r;
    }

    @Test public void idIsTheManifestsAndTheStillIsNotResolvedUntilTheDiskIsRead() {
        LivingStill s = still(new LivingRecipe());
        assertEquals("living:0123456789abcdef", s.id());
        assertTrue(AnimatedWallpapers.isLivingId(s.id()));
        assertNull("not resolvable without a context until one was resolved", AnimatedWallpapers.byId(s.id()));
        assertEquals(4, s.ownPalette().length);
        assertTrue(s.periodSeconds() >= 60f);
    }

    @Test public void livingIdsAreWellFormedOrUnknown() {
        assertTrue(AnimatedWallpapers.isKnownId("living:0123456789abcdef"));
        assertFalse(AnimatedWallpapers.isKnownId("mesh"));
        assertFalse(AnimatedWallpapers.isKnownId("living:xyz"));
        assertFalse(AnimatedWallpapers.isKnownId("living:0123456789ABCDEF"));
        assertFalse(AnimatedWallpapers.isKnownId("living:"));
        assertFalse(AnimatedWallpapers.isKnownId(null));
    }

    @Test public void declaresTheUniformContractAndItsChildren() {
        String src = still(new LivingRecipe()).agsl();
        for (String u : UNIFORMS) assertTrue("lacks " + u, src.contains(u));
        assertTrue(src.contains("float3 scene(float2 p)"));
        for (String child : new String[] {"uImage", "uDepth", "uMaskA", "uMaskB", "uMaskC", "uEffects"}) {
            assertTrue(child, src.contains("uniform shader " + child + ";"));
        }
        assertTrue(src.contains("uniform float2 uEffectsSize;"));
    }

    @Test public void theEffectsProgramDeclaresTheContractAndNoChildren() {
        String src = still(new LivingRecipe()).effectsAgsl();
        for (String u : UNIFORMS) assertTrue("lacks " + u, src.contains(u));
        assertFalse("it samples no picture", src.contains("uniform shader"));
        for (String u : new String[] {"uCover", "uIntensity", "uWaterMode", "uWater", "uMist"}) {
            assertTrue(u, src.contains(" " + u + ";"));
        }
        assertFalse("scene stays the composite's", src.contains("float3 scene(float2 p)"));
    }

    @Test public void theNoiseLoopsLiveInTheEffectsProgramOnly() {
        String composite = still(new LivingRecipe()).agsl();
        String effects = still(new LivingRecipe()).effectsAgsl();
        for (String heavy : new String[] {"fbm4", "caustic", "for ("}) {
            assertFalse("the composite has no " + heavy, composite.contains(heavy));
        }
        assertTrue(effects.contains("float fbm4(float2 p)"));
        assertTrue("the pool, the noise water and the mist all use it", effects.contains("fbm4(q0"));
    }

    @Test public void theEffectsMapEncodingMatchesBetweenTheTwoPrograms() {
        assertEquals(4, LivingStill.EFFECTS_DIVISOR);
        assertEquals(25f, LivingStill.EFFECTS_GAIN, 0f);
        String composite = still(new LivingRecipe()).agsl();
        String effects = still(new LivingRecipe()).effectsAgsl();
        assertTrue("displacement is written as 0.5 + d * 25.0", effects.contains("0.5 + d * 25.0"));
        assertTrue("and read back as (rg - 0.5) / 25.0", composite.contains("(fx.rg - 0.5) / 25.0"));
        assertTrue("alpha is always 1; the data is in rgb", effects.contains("half3(clamp(float3(0.5 + d * 25.0, b)"));
        assertTrue(effects.contains("float waveH = ") && composite.contains("float waveH = fx.b * 2.0 - 1.0;"));
        assertTrue("the focus blur is the 3 tap disc", composite.contains("float3 sampleLite(") && !composite.contains("sampleImg"));
    }

    @Test public void sourceAvoidsUnsupportedAgslConstructs() {
        LivingStill s = still(new LivingRecipe());
        for (String src : new String[] {s.agsl(), s.effectsAgsl()}) {
            for (String b : new String[] {"uint", "<<", ">>", "#define", "^", "textureLod", "texture("}) {
                assertFalse("contains " + b, src.contains(b));
            }
        }
    }

    @Test public void everyLoopHasAConstantBound() {
        LivingStill still = still(new LivingRecipe());
        int loops = 0;
        for (String src : new String[] {still.agsl(), still.effectsAgsl()}) {
            int at = 0;
            while ((at = src.indexOf("for (", at)) >= 0) {
                int end = src.indexOf(')', at);
                String head = src.substring(at, end);
                assertTrue(head, head.matches("for \\(int \\w+ = 0; \\w+ < \\d+; \\w+\\+\\+"));
                loops++;
                at = end;
            }
        }
        assertEquals("the one noise loop, in the effects program", 1, loops);
    }

    @Test public void restPoseIsThePhotoBeforeAnyUseOfTime() {
        String src = still(new LivingRecipe()).agsl();
        String scene = src.substring(src.indexOf("float3 scene(float2 p)"));
        int early = scene.indexOf("if (e <= 0.0005) return imgAt(uv0);");
        assertTrue("an early return of the photo at zero energy", early > 0);
        assertTrue("energy is clamped from the uniform", scene.indexOf("float e = clamp(uEnergy") < early);
        int firstTime = scene.indexOf("uTime");
        assertTrue("uTime is read after the return", firstTime > early);
        assertEquals("uTime is read exactly once", firstTime, scene.lastIndexOf("uTime"));
        assertFalse("one-way motion would need uPhase; nothing here does", scene.contains("uPhase"));
        // Everything that moves is scaled by energy: the intensity-scaled I, or e itself.
        assertTrue(scene.contains("float I = e * uIntensity;"));
        assertTrue(scene.contains("(1.0 - 0.03 * e)"));
        assertTrue(scene.contains("float focus = clamp(uFocus, 0.0, 1.0) * e;"));
        assertTrue("the effects map is not read at rest", scene.indexOf("effectsAt(p)") > early);
        assertEquals("depth is read once", scene.indexOf("depthAt(", scene.indexOf("depthAt(") + 1), -1);
    }

    @Test public void effectsProgramIsNeutralAtRestBeforeAnyUseOfTime() {
        String effects = still(new LivingRecipe()).effectsAgsl();
        String main = effects.substring(effects.indexOf("half4 main(float2 p)"));
        int early = main.indexOf("if (e <= 0.0005) return half4(0.5, 0.5, 0.0, 1.0);");
        assertTrue("the neutral map at zero energy", early > 0);
        int firstTime = main.indexOf("uTime");
        assertTrue(firstTime > early);
        assertEquals(firstTime, main.lastIndexOf("uTime"));
        assertFalse(main.contains("uPhase"));
        assertTrue(main.contains("float I = e * uIntensity;"));
    }

    @Test public void theTailKeepsThePhotosFullRange() {
        assertFalse("no 0.6 cap as the built-ins have", LivingStill.TAIL.contains("0.6, 0.6, 0.6"));
        assertFalse("no dither at rest", LivingStill.TAIL.contains("hash21"));
        assertTrue(LivingStill.TAIL.contains("momWarp(p0)"));
    }

    @Test public void coverFillsTheFrameAndCentres() {
        // A square photo in a tall frame: scaled to the height, cropped on the sides.
        float[] c = LivingStill.coverSize(1000f, 2000f, 500f, 500f);
        assertArrayEquals(new float[] {2000f, 2000f}, c, 1e-3f);
        // A wide photo in a tall frame.
        c = LivingStill.coverSize(1000f, 2000f, 3000f, 1500f);
        assertArrayEquals(new float[] {4000f, 2000f}, c, 1e-3f);
        // A tall photo in a wider frame: scaled to the width.
        c = LivingStill.coverSize(1500f, 2400f, 1000f, 2000f);
        assertTrue(c[0] >= 1500f && c[1] >= 2400f);
        assertEquals("keeps the aspect", 0.5f, c[0] / c[1], 1e-5f);
    }

    @Test public void theRecipeBecomesUniforms() {
        Map<String, float[]> u = still(fullRecipe()).recipeUniforms();
        assertArrayEquals(new float[] {1.25f}, u.get("uIntensity"), 0f);
        assertArrayEquals(new float[] {0.7f}, u.get("uDrift"), 0f);
        assertArrayEquals(new float[] {1f, 0.005f}, u.get("uSway"), 0f);
        assertArrayEquals(new float[] {1f}, u.get("uWaterMode"), 0f);
        assertArrayEquals(new float[] {5f, 26f, 0.0045f, 0.0035f}, u.get("uWater"), 0f);
        assertArrayEquals(new float[] {0.035f}, u.get("uSkyFlow"), 0f);
        assertArrayEquals(new float[] {1f}, u.get("uPour"), 0f);
        assertArrayEquals(new float[] {3f}, u.get("uGlowMode"), 0f);
        assertArrayEquals(new float[] {1.2f}, u.get("uGlowGain"), 0f);
        assertArrayEquals(new float[] {0f, 1f}, u.get("uGlowDir"), 1e-6f);
        assertArrayEquals(new float[] {0.2f, 0.4f, 0.6f, 0.5f}, u.get("uMist"), 0f);
        assertArrayEquals(new float[] {2f}, u.get("uParticles"), 0f);
        assertArrayEquals(new float[] {1f, 1f, 1f}, u.get("uNeed"), 0f);
    }

    @Test public void anEmptyRecipeReadsNoMask() {
        Map<String, float[]> u = still(new LivingRecipe()).recipeUniforms();
        assertArrayEquals("drift needs depth alone", new float[] {0f, 0f, 0f}, u.get("uNeed"), 0f);
        assertArrayEquals(new float[] {-1f}, u.get("uWaterMode"), 0f);
        assertArrayEquals(new float[] {0f}, u.get("uGlowMode"), 0f);
        assertArrayEquals(new float[] {0f}, u.get("uParticles"), 0f);
    }

    @Test public void theDefaultTrailPointsAlongTheApprovedDirection() {
        LivingRecipe r = new LivingRecipe();
        r.glowMode = LivingRecipe.GLOW_TRAILS;
        r.glowGain = 1f;
        float[] d = still(r).recipeUniforms().get("uGlowDir");
        assertEquals(1f, Math.hypot(d[0], d[1]), 1e-5);
        assertTrue("right", d[0] > 0f);
        assertTrue("and up", d[1] < 0f);
    }

    @Test public void waterReflectionAndNoiseShareTheNoiseMode() {
        assertEquals(LivingStill.WATER_NOISE, LivingStill.waterMode(LivingRecipe.WATER_REFLECTION), 0f);
        assertEquals(LivingStill.WATER_NOISE, LivingStill.waterMode(LivingRecipe.WATER_NOISE), 0f);
        assertEquals(LivingStill.WATER_POOL, LivingStill.waterMode(LivingRecipe.WATER_POOL), 0f);
        assertEquals(LivingStill.WATER_OFF, LivingStill.waterMode(LivingRecipe.WATER_NONE), 0f);
    }

    @Test public void aStillWithNoRecipeFileHasNoStamp() {
        assertEquals(0L, still(new LivingRecipe()).stamp());
    }
}
