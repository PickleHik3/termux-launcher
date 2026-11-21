package com.termux.app.chrome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** A surface pressed into the page wears a well: depth from shadow, light only outside it. */
public class InsetGlassTest {

    private static final float D = 2.625f;

    private static int alpha(int color) {
        return color >>> 24;
    }

    private static int luma(int color) {
        return ((color >> 16) & 0xFF) + ((color >> 8) & 0xFF) + (color & 0xFF);
    }

    @Test
    public void theWellIsShadedLightlyTowardShadowNeverWhiter() {
        int[] grounds = {0xFF000000, 0xFF202020, 0xFF6A4FA0, 0xFF808080, 0xFFE0E0E0, 0xFFFFFFFF};
        for (int ground : grounds) {
            for (boolean dark : new boolean[] {true, false}) {
                int in = InsetGlass.groundIn(ground, dark);
                assertEquals(0xFF, alpha(in));
                assertTrue(Integer.toHexString(ground), luma(in) <= luma(ground));
            }
        }
        // Low enough that the inner shadow, not the fill, makes the depth.
        for (boolean dark : new boolean[] {true, false}) {
            assertTrue(alpha(InsetGlass.wellShade(dark)) > 0);
            assertTrue(alpha(InsetGlass.wellShade(dark)) * 4 < alpha(InsetGlass.innerShadow(dark)));
        }
    }

    @Test
    public void theInnerShadowIsStrongestUnderTheTopAndStillOnTheSides() {
        for (boolean dark : new boolean[] {true, false}) {
            int top = alpha(InsetGlass.innerShadow(dark));
            int side = alpha(InsetGlass.innerShadowSide(dark));
            assertTrue(side > 0);
            assertTrue(side < top);
            assertEquals(InsetGlass.SHADOW_RGB, InsetGlass.innerShadow(dark) & 0x00FFFFFF);
        }
        // About 55 % black at the edge on a dark ground.
        assertEquals(0.55f, alpha(InsetGlass.innerShadow(true)) / 255f, 0.01f);
    }

    @Test
    public void aLightGroundTakesWeakerShadowAndStrongerLight() {
        assertTrue(alpha(InsetGlass.wellShade(false)) < alpha(InsetGlass.wellShade(true)));
        assertTrue(alpha(InsetGlass.innerShadow(false)) < alpha(InsetGlass.innerShadow(true)));
        assertTrue(alpha(InsetGlass.rimShadow(false)) < alpha(InsetGlass.rimShadow(true)));
        assertTrue(alpha(InsetGlass.rimLight(false)) > alpha(InsetGlass.rimLight(true)));
        assertTrue(alpha(InsetGlass.innerShadow(false)) > 0);
        assertTrue(alpha(InsetGlass.rimLight(true)) > 0);
    }

    @Test
    public void theRimIsDarkAcrossTheTopAndLightAcrossTheBottomNeverBoth() {
        assertEquals(InsetGlass.SHADOW_RGB, InsetGlass.rimShadow(true) & 0x00FFFFFF);
        assertEquals(InsetGlass.LIGHT_RGB, InsetGlass.rimLight(true) & 0x00FFFFFF);
        assertTrue(InsetGlass.RIM_SHADOW_TO_SHARE < InsetGlass.RIM_LIGHT_FROM_SHARE);
    }

    @Test
    public void theShadowReachesEightDpAndSmallWellsClampIt() {
        float pill = 30f * D;
        assertEquals(8f * D, InsetGlass.shadowReachPx(D, pill), 1e-3f);
        assertEquals(InsetGlass.RIM_WIDTH_DP * D, InsetGlass.rimWidthPx(D, pill), 1e-3f);
        float sliver = 6f * D;
        assertEquals(sliver * InsetGlass.INNER_SHADOW_MAX_SHARE,
            InsetGlass.shadowReachPx(D, sliver), 1e-3f);
        assertTrue(InsetGlass.shadowReachPx(D, sliver) < sliver / 2f);
        assertEquals(sliver * InsetGlass.RIM_MAX_SHARE, InsetGlass.rimWidthPx(D, sliver), 1e-3f);
        assertEquals(0, InsetGlass.ringCount(D, 0f));
        assertEquals(0f, InsetGlass.rimWidthPx(D, -4f), 0f);
    }

    @Test
    public void theRingsCoverTheReachAndFadeInward() {
        float pill = 30f * D;
        int count = InsetGlass.ringCount(D, pill);
        assertEquals(16, count);
        assertTrue(count * InsetGlass.ringStepPx(D) >= InsetGlass.shadowReachPx(D, pill));
        assertEquals(255, InsetGlass.ringAlpha(0, count));
        for (int i = 1; i < count; i++) {
            assertTrue(InsetGlass.ringAlpha(i, count) < InsetGlass.ringAlpha(i - 1, count));
        }
        assertTrue(InsetGlass.ringAlpha(count - 1, count) <= 2);
        assertEquals(0, InsetGlass.ringAlpha(count, count));
        assertEquals(0, InsetGlass.ringAlpha(0, 0));
    }
}
