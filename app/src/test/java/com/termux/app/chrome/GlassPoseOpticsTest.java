package com.termux.app.chrome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * How glass answers its page's pose (#318; the maths of the experiment 8bafff2d0's
 * GlassRealOptics, folded in on the physical slab): every term against the turn and the scale,
 * nothing at rest, and the debug property's numbers.
 */
public class GlassPoseOpticsTest {

    private static final float DENSITY = 2.625f;
    private static final GlassPoseOptics OPTICS = GlassPoseOptics.DEFAULT;
    private static final float IOR = GlassSlab.DEFAULT.ior;

    private static float sine(double degrees) {
        return (float) Math.sin(Math.toRadians(degrees));
    }

    @Test
    public void theShiftThroughTheSlabGrowsWithTheTurnAsTheTangentsDiffer() {
        assertEquals(0f, OPTICS.shiftTan(0f, IOR), 0f);
        float last = 0f;
        for (int deg = 5; deg <= 80; deg += 5) {
            float shift = OPTICS.shiftTan(sine(deg), IOR);
            assertTrue("grows at " + deg, shift > last);
            last = shift;
        }
        // 30 degrees at 1.5: tan 30 - tan 19.47 = 0.2239.
        assertEquals(0.2239f, OPTICS.shiftTan(sine(30), IOR), 1e-3f);
        // Carousel's 60: tan 60 - tan 35.26 = 1.025, so about the slab's thickness.
        assertEquals(1.025f, OPTICS.shiftTan(sine(60), IOR), 2e-3f);
        assertTrue(Float.isFinite(OPTICS.shiftTan(1f, IOR)));
        // Symmetric in the turn's direction: the direction is the uniform's vector.
        assertEquals(OPTICS.shiftTan(sine(20), IOR), OPTICS.shiftTan(-sine(20), IOR), 0f);
    }

    @Test
    public void theFacesReflectanceRisesFromFourToAboutNinePercentAtSixty() {
        assertEquals(0.04f, GlassPoseOptics.fresnelExact(1f, IOR), 1e-4f);
        assertEquals(0.0895f, GlassPoseOptics.fresnelExact((float) Math.cos(Math.toRadians(60)), IOR), 2e-3f);
        assertEquals(1f, GlassPoseOptics.fresnelExact(0f, IOR), 1e-6f);
        assertEquals(0f, OPTICS.faceReflectanceRise(0f, IOR), 0f);
        assertEquals(0.0495f, OPTICS.faceReflectanceRise(sine(60), IOR), 2e-3f);
        float last = 0f;
        for (int deg = 5; deg <= 60; deg += 5) {
            float rise = OPTICS.faceReflectanceRise(sine(deg), IOR);
            assertTrue("rises at " + deg, rise >= last);
            last = rise;
        }
    }

    @Test
    public void theBevelsSchlickMeetsTheFacesReflectanceWhereItFlattens() {
        float f0 = GlassSlab.DEFAULT.f0;
        for (int deg = 0; deg <= 60; deg += 10) {
            float s = sine(deg);
            float cos = (float) Math.cos(Math.toRadians(deg));
            float bevelAtFace = GlassPoseOptics.schlick(cos, f0) + GlassPoseOptics.schlickCorrection(s, IOR, f0);
            assertEquals("at " + deg, f0 + OPTICS.faceReflectanceRise(s, IOR), bevelAtFace, 1e-5f);
        }
        assertEquals(0f, GlassPoseOptics.schlickCorrection(0f, IOR, f0), 0f);
    }

    @Test
    public void inTheTurnedSlabsFrameTheEyeLeansTowardTheNearEdge() {
        float[] v = new float[3];
        float s = sine(30);
        GlassPoseOptics.toSlab(0f, 0f, 1f, s, 0f, v);
        assertEquals(-s, v[0], 1e-6f);
        assertEquals(0f, v[1], 1e-6f);
        assertEquals((float) Math.cos(Math.toRadians(30)), v[2], 1e-6f);
        // So the bevel going away (its normal leaning toward +x) meets the eye more steeply than
        // the near one, and takes more Fresnel; flat, both take the same.
        float slope = 1f;
        float cv = (float) (1.0 / Math.sqrt(2.0));
        float far = cv * (slope * v[0] + v[2]);
        float near = cv * (-slope * v[0] + v[2]);
        assertTrue(far < near);
        float f0 = GlassSlab.DEFAULT.f0;
        assertTrue(GlassPoseOptics.schlick(far, f0) > GlassPoseOptics.schlick(cv, f0));
        assertTrue(GlassPoseOptics.schlick(near, f0) < GlassPoseOptics.schlick(cv, f0));
        // Unturned it is the identity.
        GlassPoseOptics.toSlab(0.3f, -0.4f, 0.8f, 0f, 0f, v);
        assertEquals(0.3f, v[0], 0f);
        assertEquals(-0.4f, v[1], 0f);
        assertEquals(0.8f, v[2], 0f);
    }

    @Test
    public void theHalfVectorIsTheSlabsFlatAndTurnsWithTheSlab() {
        float[] rest = new float[3];
        float[] turned = new float[3];
        GlassSlab.half(rest);
        GlassPoseOptics.half(0f, 0f, turned);
        for (int i = 0; i < 3; i++) assertEquals(rest[i], turned[i], 1e-6f);
        GlassPoseOptics.half(sine(30), 0f, turned);
        assertEquals(1f, (float) Math.sqrt(turned[0] * turned[0] + turned[1] * turned[1]
            + turned[2] * turned[2]), 1e-5f);
        assertTrue("the half vector leans toward the near edge", turned[0] < rest[0]);
    }

    @Test
    public void theGlintShipsOff() {
        // No fixed light, so no glint (the developer, 2026-11-23): a turn sends none across.
        assertEquals(0f, GlassPoseOptics.GLINT, 0f);
        float[] g = new float[4];
        OPTICS.glint(sine(15), 0f, DENSITY, g);
        assertEquals(0f, g[3], 0f);
    }

    @Test
    public void theGlintIsOffFlatAndTravelsAcrossTheFaceAsTheNormalSweeps() {
        // The tuning key's glint, at the strength it shipped with before 2026-11-23.
        GlassPoseOptics glinted = GlassPoseOptics.parse("glint=0.1");
        float[] g = new float[4];
        glinted.glint(0f, 0f, DENSITY, g);
        assertEquals(0f, g[3], 0f);
        float[] a = new float[4];
        float[] b = new float[4];
        glinted.glint(sine(5), 0f, DENSITY, a);
        glinted.glint(sine(15), 0f, DENSITY, b);
        assertEquals(0.1f, a[3], 1e-6f);
        // Turning so the right edge goes away swings the normal right: the glint moves left.
        assertTrue(b[0] < a[0]);
        assertTrue("mostly along the turn", Math.abs(a[1] - b[1]) < Math.abs(a[0] - b[0]) / 4f);
        // About 2 x VIEW_DP x 10 degrees of travel.
        float travelDp = (a[0] - b[0]) / DENSITY;
        assertTrue("travel " + travelDp, travelDp > 100f && travelDp < 400f);
        // A turn too small to see fades it in rather than popping it on.
        glinted.glint(sine(1), 0f, DENSITY, g);
        assertTrue(g[3] > 0f && g[3] < 0.1f * 0.3f);
        assertEquals(GlassPoseOptics.GLINT_WIDTH_DP * DENSITY, g[2], 1e-4f);
    }

    @Test
    public void scaleMagnifiesALittle() {
        assertEquals(1f, OPTICS.magnify(1f), 0f);
        assertTrue(OPTICS.magnify(0.75f) < 1f);
        assertEquals(1f - 0.06f * 0.25f, OPTICS.magnify(0.75f), 1e-6f);
        assertTrue(OPTICS.magnify(1.2f) > 1f);
        assertTrue(OPTICS.magnify(5f) <= 1.1f);
        assertEquals(1f, OPTICS.magnify(Float.NaN), 0f);
    }

    @Test
    public void atRestTheUniformsAreTheRestLookWhateverTheNumbers() {
        float[] rest = new float[3];
        GlassSlab.half(rest);
        GlassPose pose = new GlassPose();
        float[] u = new float[GlassPoseOptics.UNIFORMS];
        float[] scratch = new float[4];
        for (GlassPoseOptics optics : new GlassPoseOptics[] {GlassPoseOptics.DEFAULT,
                GlassPoseOptics.parse("shift=3,fresnel=2,glint=0.5,zoom=0.5")}) {
            optics.uniforms(pose, GlassSlab.DEFAULT, DENSITY, u, scratch);
            assertEquals(0f, u[0], 0f);
            assertEquals(0f, u[1], 0f);
            assertEquals(1f, u[2], 0f);
            assertEquals(0f, u[3], 0f);
            assertEquals("no magnification", 1f, u[4], 0f);
            assertEquals(0f, u[5], 0f);
            assertEquals(0f, u[6], 0f);
            assertEquals(rest[0], u[8], 0f);
            assertEquals(rest[1], u[9], 0f);
            assertEquals(rest[2], u[10], 0f);
            assertEquals(0f, u[11], 0f);
            assertEquals("no glint", 0f, u[15], 0f);
        }
        // Turned at Carousel's 30 and Depth's three quarters.
        pose.tiltX = sine(30);
        pose.scale = 0.75f;
        OPTICS.uniforms(pose, GlassSlab.DEFAULT, DENSITY, u, scratch);
        assertEquals(sine(30), u[0], 0f);
        assertEquals(OPTICS.shiftTan(sine(30), IOR), u[3], 0f);
        assertEquals(OPTICS.magnify(0.75f), u[4], 0f);
        assertTrue(u[5] > 0f);
        assertEquals(1f, u[6], 0f);
        assertEquals(GlassPoseOptics.MAX_SHIFT_DP * DENSITY, u[7], 1e-4f);
        assertEquals("no glint: no fixed light", 0f, u[15], 0f);
        GlassPoseOptics.parse("glint=0.1").uniforms(pose, GlassSlab.DEFAULT, DENSITY, u, scratch);
        assertTrue("the tuning key's glint still reaches the program", u[15] > 0f);
    }

    @Test
    public void thePropertyOverridesNamedNumbersAndFallsBackWhole() {
        assertSame(GlassPoseOptics.DEFAULT, GlassPoseOptics.parse(null));
        assertSame(GlassPoseOptics.DEFAULT, GlassPoseOptics.parse("  "));
        assertSame(GlassPoseOptics.DEFAULT, GlassPoseOptics.parse("shift=1"));
        GlassPoseOptics o = GlassPoseOptics.parse("shift=1.5, glint=0.2,zoom=0");
        assertEquals(1.5f, o.shift, 0f);
        assertEquals(0.2f, o.glint, 0f);
        assertEquals(0f, o.zoom, 0f);
        assertEquals(GlassPoseOptics.VIEW_DP, o.viewDp, 0f);
        assertNotEquals(GlassPoseOptics.DEFAULT, o);
        assertEquals(1f, o.magnify(0.75f), 0f);
        assertSame(GlassPoseOptics.DEFAULT, GlassPoseOptics.parse("shift=9"));
        assertSame(GlassPoseOptics.DEFAULT, GlassPoseOptics.parse("shift=1,bogus=2"));
        assertSame(GlassPoseOptics.DEFAULT, GlassPoseOptics.parse("shift=abc"));
        assertSame(GlassPoseOptics.DEFAULT, GlassPoseOptics.parse("shift"));
        assertEquals(o, GlassPoseOptics.parse(o.toString()));
        assertEquals("debug.tl.glass_pose", GlassPoseOptics.PROP);
    }
}
