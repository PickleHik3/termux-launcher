package com.termux.app.chrome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** A surface pressed into the page wears a well: shaded toward shadow, an inner shadow, a lip. */
public class InsetGlassTest {

    private static final float D = 2.75f;

    private static int alpha(int color) {
        return color >>> 24;
    }

    private static int luma(int color) {
        return ((color >> 16) & 0xFF) + ((color >> 8) & 0xFF) + (color & 0xFF);
    }

    @Test
    public void theWellIsShadedTowardShadowNeverWhiter() {
        int[] grounds = {0xFF000000, 0xFF202020, 0xFF6A4FA0, 0xFF808080, 0xFFE0E0E0, 0xFFFFFFFF};
        for (int ground : grounds) {
            for (boolean dark : new boolean[] {true, false}) {
                int in = InsetGlass.groundIn(ground, dark);
                assertEquals(0xFF, alpha(in));
                assertTrue(Integer.toHexString(ground), luma(in) <= luma(ground));
            }
        }
        assertEquals(InsetGlass.SHADOW_RGB, InsetGlass.wellShade(true) & 0x00FFFFFF);
        assertTrue(alpha(InsetGlass.wellShade(true)) > 0);
        assertTrue(alpha(InsetGlass.wellShade(false)) > 0);
    }

    @Test
    public void aLightGroundTakesWeakerShadowAndStrongerLight() {
        assertTrue(alpha(InsetGlass.wellShade(false)) < alpha(InsetGlass.wellShade(true)));
        assertTrue(alpha(InsetGlass.innerShadow(false)) < alpha(InsetGlass.innerShadow(true)));
        assertTrue(alpha(InsetGlass.lip(false)) > alpha(InsetGlass.lip(true)));
        // Every cue still shows on both.
        assertTrue(alpha(InsetGlass.innerShadow(false)) > 0);
        assertTrue(alpha(InsetGlass.lip(true)) > 0);
    }

    @Test
    public void theInnerShadowIsDarkerThanTheWellAndTheLipIsLight() {
        for (boolean dark : new boolean[] {true, false}) {
            assertTrue(alpha(InsetGlass.innerShadow(dark)) > alpha(InsetGlass.wellShade(dark)));
            assertEquals(InsetGlass.SHADOW_RGB, InsetGlass.innerShadow(dark) & 0x00FFFFFF);
            assertEquals(InsetGlass.LIP_RGB, InsetGlass.lip(dark) & 0x00FFFFFF);
        }
    }

    @Test
    public void smallWellsClampTheShadowAndTheLip() {
        // The Home pill: 24.5 dp tall keeps the full depth and width.
        float pill = 24.5f * D;
        assertEquals(InsetGlass.INNER_SHADOW_DEPTH_DP * D, InsetGlass.shadowDepthPx(D, pill), 1e-3f);
        assertEquals(InsetGlass.LIP_WIDTH_DP * D, InsetGlass.lipWidthPx(D, pill), 1e-3f);
        // A sliver clamps both to a share of itself.
        float sliver = 6f * D;
        assertEquals(sliver * InsetGlass.INNER_SHADOW_MAX_SHARE, InsetGlass.shadowDepthPx(D, sliver), 1e-3f);
        assertEquals(sliver * InsetGlass.LIP_MAX_SHARE, InsetGlass.lipWidthPx(D, sliver), 1e-3f);
        assertTrue(InsetGlass.shadowDepthPx(D, sliver) < sliver / 2f);
        assertEquals(0f, InsetGlass.shadowDepthPx(D, 0f), 0f);
        assertEquals(0f, InsetGlass.lipWidthPx(D, -4f), 0f);
    }

    @Test
    public void theShadowAndTheLipNeverMeet() {
        // The shadow fades out above the point the lip starts fading in.
        for (float dp = 4f; dp <= 64f; dp += 0.5f) {
            float h = dp * D;
            assertTrue("h=" + dp, InsetGlass.shadowDepthPx(D, h) <= h * InsetGlass.LIP_FROM_SHARE);
        }
    }
}
