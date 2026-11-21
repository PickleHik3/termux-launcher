package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The pre-blur keeps the frame sizes RenderScript baked at, and the GPU bake's radius gives
 * RenderScript's softness. The GPU bake itself needs a real GPU and cannot run here.
 */
public class WallpaperBlurSizingTest {

    /** The sizing WallpaperBlurRenderer had inline before the GPU bake, kept as the reference. */
    private static float[] legacyFactorAndRadius(float blurRadiusPx) {
        float downsampleFactor = Math.max(1f, Math.min(ChromePolicy.ACCESSORY_BLUR_DOWNSAMPLE_FACTOR,
            (float) Math.ceil(blurRadiusPx / 25f)));
        float scriptRadius = blurRadiusPx / downsampleFactor;
        if (scriptRadius > 25f) {
            downsampleFactor = (float) Math.ceil(blurRadiusPx / 25f);
            scriptRadius = blurRadiusPx / downsampleFactor;
        }
        scriptRadius = Math.max(0.1f, Math.min(25f, scriptRadius));
        return new float[] {downsampleFactor, scriptRadius};
    }

    @Test
    public void factorAndRadiusMatchTheOldSizingAtEveryRadius() {
        for (float px = 0.25f; px <= 400f; px += 0.25f) {
            float[] legacy = legacyFactorAndRadius(px);
            float factor = WallpaperBlurSizing.downsampleFactor(px);
            assertEquals("factor at " + px, legacy[0], factor, 0f);
            assertEquals("radius at " + px, legacy[1],
                WallpaperBlurSizing.scriptRadius(px, factor), 0f);
        }
    }

    @Test
    public void underTwentyFivePxKeepsAFullSizeFrame() {
        assertEquals(1f, WallpaperBlurSizing.downsampleFactor(3f), 0f);
        assertEquals(1f, WallpaperBlurSizing.downsampleFactor(25f), 0f);
        assertEquals(2736, WallpaperBlurSizing.scaledSide(2736, 1f));
    }

    @Test
    public void largeRadiiDownsampleAndStayInsideTheCap() {
        // 32 dp at 2.75 density: 88 px, a quarter-size frame at a 22 px script radius.
        assertEquals(4f, WallpaperBlurSizing.downsampleFactor(88f), 0f);
        assertEquals(22f, WallpaperBlurSizing.scriptRadius(88f, 4f), 1e-5f);
        assertEquals(684, WallpaperBlurSizing.scaledSide(2736, 4f));
        assertEquals(456, WallpaperBlurSizing.scaledSide(1824, 4f));
        // Past 100 px the factor grows past 4 so the radius stays at 25 px or under.
        assertEquals(5f, WallpaperBlurSizing.downsampleFactor(110f), 0f);
        assertTrue(WallpaperBlurSizing.scriptRadius(110f, 5f) <= 25f);
    }

    @Test
    public void scaledSideIsAtLeastOne() {
        assertEquals(1, WallpaperBlurSizing.scaledSide(1, 4f));
        assertEquals(1, WallpaperBlurSizing.scaledSide(0, 4f));
    }

    @Test
    public void gpuRadiusGivesRenderScriptsSigma() {
        for (float r = 0.1f; r <= 25f; r += 0.1f) {
            float rsSigma = 0.4f * r + 0.6f;
            float skiaSigma = 0.57735f * WallpaperBlurSizing.gpuBlurRadius(r) + 0.5f;
            assertEquals("sigma at " + r, rsSigma, skiaSigma, 1e-4f);
        }
    }

    @Test
    public void gpuRadiusAtKnownPoints() {
        // r = 25: sigma 10.6, R = 10.1 / 0.57735.
        assertEquals(17.4937f, WallpaperBlurSizing.gpuBlurRadius(25f), 1e-3f);
        // The smallest script radius still blurs a little rather than not at all.
        assertTrue(WallpaperBlurSizing.gpuBlurRadius(0.1f) > 0f);
    }
}
