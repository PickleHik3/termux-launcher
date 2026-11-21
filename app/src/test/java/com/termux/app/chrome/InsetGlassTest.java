package com.termux.app.chrome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** A surface pressed into the page wears a well lit from above: dark under the top, lit below. */
public class InsetGlassTest {

    private static final float D = 2.625f;

    private static int alpha(int color) {
        return color >>> 24;
    }

    private static int luma(int color) {
        return ((color >> 16) & 0xFF) + ((color >> 8) & 0xFF) + (color & 0xFF);
    }

    @Test
    public void theFloorIsDarkestUnderTheTopLipAndLitInTheLowerPart() {
        for (boolean dark : new boolean[] {true, false}) {
            int[] colors = InsetGlass.floorColors(dark, new int[InsetGlass.FLOOR_POSITIONS.length]);
            assertEquals(InsetGlass.FLOOR_POSITIONS.length, colors.length);
            // The shade falls off from the top and is gone by the clear share.
            float last = Float.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                assertEquals(InsetGlass.SHADOW_RGB, colors[i] & 0x00FFFFFF);
                assertTrue(alpha(colors[i]) < last);
                last = alpha(colors[i]);
            }
            assertEquals(0, alpha(InsetGlass.floorAt(InsetGlass.FLOOR_CLEAR_SHARE, dark)));
            // A faint light at the bottom, never stronger than the top's shade.
            int bottom = InsetGlass.floorAt(1f, dark);
            assertEquals(InsetGlass.LIGHT_RGB, bottom & 0x00FFFFFF);
            assertTrue(alpha(bottom) > 0);
            assertTrue(alpha(bottom) < alpha(InsetGlass.floorAt(0f, dark)) / 2);
        }
        // About 40 % under the lip on a dark ground.
        assertEquals(0.40f, alpha(InsetGlass.floorAt(0f, true)) / 255f, 0.01f);
    }

    @Test
    public void theGroundIsDarkerAtTheTopThanAtTheBottom() {
        int[] grounds = {0xFF202020, 0xFF6A4FA0, 0xFF808080, 0xFFE0E0E0};
        for (int ground : grounds) {
            for (boolean dark : new boolean[] {true, false}) {
                int top = InsetGlass.groundAt(ground, dark, 0f);
                int bottom = InsetGlass.groundAt(ground, dark, 1f);
                assertEquals(0xFF, alpha(top));
                assertTrue(luma(top) < luma(ground));
                assertTrue(luma(bottom) >= luma(ground));
                assertTrue(luma(top) < luma(bottom));
            }
        }
    }

    @Test
    public void theContactShadowIsStrongestUnderTheTopAndWeakerOnTheSides() {
        for (boolean dark : new boolean[] {true, false}) {
            int top = alpha(InsetGlass.innerShadow(dark));
            int side = alpha(InsetGlass.innerShadowSide(dark));
            assertTrue(side > 0);
            assertTrue(side < top / 2);
        }
    }

    @Test
    public void aLightGroundTakesWeakerShadowAndStrongerLight() {
        assertTrue(alpha(InsetGlass.floorAt(0f, false)) < alpha(InsetGlass.floorAt(0f, true)));
        assertTrue(alpha(InsetGlass.innerShadow(false)) < alpha(InsetGlass.innerShadow(true)));
        assertTrue(alpha(InsetGlass.rimShadow(false)) < alpha(InsetGlass.rimShadow(true)));
        assertTrue(alpha(InsetGlass.rimLight(false)) > alpha(InsetGlass.rimLight(true)));
    }

    @Test
    public void theRimIsSubtlyDarkOnTopAndLightOnTheBottomArc() {
        assertEquals(InsetGlass.SHADOW_RGB, InsetGlass.rimShadow(true) & 0x00FFFFFF);
        assertEquals(InsetGlass.LIGHT_RGB, InsetGlass.rimLight(true) & 0x00FFFFFF);
        assertTrue(alpha(InsetGlass.rimShadow(true)) < alpha(InsetGlass.rimLight(true)));
        assertTrue(InsetGlass.RIM_SHADOW_TO_SHARE < 1f - InsetGlass.RIM_LIGHT_REACH_SHARE);
        assertTrue(InsetGlass.RIM_LIGHT_MID > 0f && InsetGlass.RIM_LIGHT_MID < 1f);
    }

    @Test
    public void smallWellsClampTheShadowAndTheRim() {
        float pill = 30f * D;
        assertEquals(InsetGlass.INNER_SHADOW_REACH_DP * D, InsetGlass.shadowReachPx(D, pill), 1e-3f);
        assertEquals(InsetGlass.RIM_WIDTH_DP * D, InsetGlass.rimWidthPx(D, pill), 1e-3f);
        float sliver = 6f * D;
        assertEquals(sliver * InsetGlass.INNER_SHADOW_MAX_SHARE,
            InsetGlass.shadowReachPx(D, sliver), 1e-3f);
        assertEquals(sliver * InsetGlass.RIM_MAX_SHARE, InsetGlass.rimWidthPx(D, sliver), 1e-3f);
        assertEquals(0, InsetGlass.ringCount(D, 0f));
    }

    @Test
    public void theRingsStandAboutAPixelApartAndFadeInward() {
        float pill = 30f * D;
        int count = InsetGlass.ringCount(D, pill);
        assertTrue(InsetGlass.ringStepPx(D) <= 1f);
        assertTrue(count * InsetGlass.ringStepPx(D) >= InsetGlass.shadowReachPx(D, pill));
        assertEquals(255, InsetGlass.ringAlpha(0, count));
        int worstStep = 0;
        for (int i = 1; i < count; i++) {
            assertTrue(InsetGlass.ringAlpha(i, count) < InsetGlass.ringAlpha(i - 1, count));
            worstStep = Math.max(worstStep,
                InsetGlass.ringAlpha(i - 1, count) - InsetGlass.ringAlpha(i, count));
        }
        // No band steps by more than about 5 % of the shadow's own alpha (no visible banding).
        assertTrue("step " + worstStep, worstStep * alpha(InsetGlass.innerShadow(true)) / 255
            <= 0.05f * 255);
        assertEquals(0, InsetGlass.ringAlpha(count, count));
    }
}
