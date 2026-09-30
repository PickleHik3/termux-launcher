package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Rect;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The pure half of the glass: how the three knobs become uniforms, and where a surface's rim runs.
 * The program itself compiles only on a phone; nothing here needs one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassRefractionTest {

    private static final float DENSITY = 2.625f; // pong, 420 dpi

    @Test
    public void theDefaultLookIsTheDocksOwnRefraction() {
        // The band and the pull were literals in the activity's shader setup: 20 dp band, 9 dp
        // pull. The rim light is the "medium" depth every Look declares (18% of MAX_RIM, spec
        // appearance-layout-editor §4), since the 1dp drawable rim carries the edge and the
        // shader only adds a whisper on top.
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        assertEquals(20f * DENSITY, look.bandPx(DENSITY), 1e-4f);
        assertEquals(9f * DENSITY, look.strengthPx(DENSITY), 1e-4f);
        assertEquals(0.18f * GlassRefraction.Look.MAX_RIM, look.rim(), 1e-4f);
    }

    @Test
    public void theKnobsMapToUniformsAtTheScreensDensity() {
        GlassRefraction.Look look = new GlassRefraction.Look(12, 30, 50);
        assertEquals(12f * DENSITY, look.strengthPx(DENSITY), 1e-4f);
        assertEquals(30f * DENSITY, look.bandPx(DENSITY), 1e-4f);
        assertEquals(GlassRefraction.Look.MAX_RIM * 0.5f, look.rim(), 1e-4f);
        assertEquals("all the way up is the brightest rim", GlassRefraction.Look.MAX_RIM,
            new GlassRefraction.Look(0, 1, 100).rim(), 1e-4f);
        assertEquals(0f, new GlassRefraction.Look(0, 1, 0).rim(), 1e-4f);
        assertEquals(0f, new GlassRefraction.Look(0, 1, 0).strengthPx(DENSITY), 1e-4f);
    }

    @Test
    public void aBandNeverCollapsesToNothing() {
        // The shader divides by the band; a zero would push every pixel to the rim.
        assertTrue(new GlassRefraction.Look(9, 0, 32).bandPx(DENSITY) >= 1f);
        assertTrue(new GlassRefraction.Look(9, 0, 32).bandPx(0f) >= 1f);
    }

    @Test
    public void aLookIsAValue() {
        GlassRefraction.Look one = new GlassRefraction.Look(9, 20, 18);
        assertEquals(one, GlassRefraction.Look.DEFAULT);
        assertEquals(one.hashCode(), GlassRefraction.Look.DEFAULT.hashCode());
        assertNotEquals(one, new GlassRefraction.Look(10, 20, 18));
        assertNotEquals(one, new GlassRefraction.Look(9, 21, 18));
        assertNotEquals(one, new GlassRefraction.Look(9, 20, 19));
    }

    @Test
    public void theRimRunsAlongTheBoundsUnlessAnEdgeIsASeam() {
        Rect bounds = new Rect(0, 0, 1080, 300);
        float[] rim = new float[4];
        GlassRefraction.rimRect(rim, bounds, 100f, 0);
        assertEquals(0f, rim[0], 0f);
        assertEquals(0f, rim[1], 0f);
        assertEquals(1080f, rim[2], 0f);
        assertEquals(300f, rim[3], 0f);

        // The keyboard host over the under-pill strip: its bottom edge is interior glass.
        GlassRefraction.rimRect(rim, bounds, 100f, GlassRefraction.SEAM_BOTTOM);
        assertEquals(0f, rim[1], 0f);
        assertEquals(400f, rim[3], 0f);

        // The window bar under the status band: its top edge is.
        GlassRefraction.rimRect(rim, bounds, 100f, GlassRefraction.SEAM_TOP);
        assertEquals(-100f, rim[1], 0f);
        assertEquals(300f, rim[3], 0f);

        GlassRefraction.rimRect(rim, bounds, 40f,
            GlassRefraction.SEAM_LEFT | GlassRefraction.SEAM_RIGHT);
        assertEquals(-40f, rim[0], 0f);
        assertEquals(1120f, rim[2], 0f);
    }

    @Test
    public void aFractionalRimIsTheSameRuleAsTheBoundsOne() {
        // A corner tab mid-slide stands at fractional bounds; its rim follows the one rule.
        float[] fromRect = new float[4];
        float[] fromFloats = new float[4];
        int seams = GlassRefraction.SEAM_TOP | GlassRefraction.SEAM_RIGHT;
        GlassRefraction.rimRect(fromRect, new Rect(10, 20, 110, 52), 30f, seams);
        GlassRefraction.rimRect(fromFloats, 10f, 20f, 110f, 52f, 30f, seams);
        for (int i = 0; i < 4; i++) assertEquals(fromRect[i], fromFloats[i], 0f);

        GlassRefraction.rimRect(fromFloats, 10.5f, -12.25f, 110.5f, 19.75f, 30f, seams);
        assertEquals(10.5f, fromFloats[0], 0f);
        assertEquals(-42.25f, fromFloats[1], 0f);
        assertEquals(140.5f, fromFloats[2], 0f);
        assertEquals(19.75f, fromFloats[3], 0f);
    }

    @Test
    public void aSeamIsPushedPastEverythingTheRimCouldTouch() {
        // Past the band the pull reaches into, the pull itself, and the corner arc — so neither
        // the light nor the bend nor a rounded corner shows along the shared edge.
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        float reach = GlassRefraction.seamReachPx(look, DENSITY, 60f);
        assertTrue(reach > look.bandPx(DENSITY) + look.strengthPx(DENSITY) + 60f);
        assertTrue("a square seam still clears the band and the pull",
            GlassRefraction.seamReachPx(look, DENSITY, 0f)
                > look.bandPx(DENSITY) + look.strengthPx(DENSITY));
    }
}
