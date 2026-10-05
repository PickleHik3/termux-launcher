package com.termux.app.chrome.wallpaper.living;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Recipe rules on synthetic scene statistics. */
public class RecipeRulesTest {

    private static RegionMasks.Stats stats() {
        RegionMasks.Stats s = new RegionMasks.Stats();
        s.sceneLum = 0.6f;
        s.skyLum = 0.7f;
        s.depthSpread = 0.3f;
        return s;
    }

    @Test
    public void emptySceneOnlyDrifts() {
        LivingRecipe r = RecipeRules.make(stats());
        assertEquals(LivingRecipe.WATER_NONE, r.waterMode);
        assertEquals(0f, r.swayAmp, 0f);
        assertEquals(0f, r.pour, 0f);
        assertEquals(LivingRecipe.GLOW_NONE, r.glowMode);
        assertEquals(0f, r.mistAmount, 0f);
        assertTrue(r.drift > 0f);
        assertFalse(r.gemma);
    }

    @Test
    public void waterModesFollowTheRules() {
        RegionMasks.Stats s = stats();
        s.water = 0.2f;
        assertEquals(LivingRecipe.WATER_LAKE, RecipeRules.make(s).waterMode);
        s.water = 0.6f;
        s.waterDepthStd = 0.05f;
        assertEquals(LivingRecipe.WATER_POOL, RecipeRules.make(s).waterMode);
        s.water = 0.2f;
        s.sceneLum = 0.2f;
        s.horizon = 0.3f;
        assertEquals(LivingRecipe.WATER_REFLECTION, RecipeRules.make(s).waterMode);
        s.water = 0.02f;
        assertEquals(LivingRecipe.WATER_NONE, RecipeRules.make(s).waterMode);
    }

    @Test
    public void skyIsCloudsByDayAndStarsAtNight() {
        RegionMasks.Stats s = stats();
        s.sky = 0.3f;
        LivingRecipe day = RecipeRules.make(s);
        assertFalse(day.skyStars);
        assertTrue(day.skyFlow > 0f);
        s.skyLum = 0.1f;
        assertTrue(RecipeRules.make(s).skyStars);
        s.sky = 0.04f;
        assertEquals(0f, RecipeRules.make(s).skyFlow, 0f);
    }

    @Test
    public void glowFlickersInSignsAndBreathesOtherwise() {
        RegionMasks.Stats s = stats();
        s.glow = 0.02f;
        assertEquals(LivingRecipe.GLOW_BREATHE, RecipeRules.make(s).glowMode);
        s.lightsInSigns = 0.8f;
        assertEquals(LivingRecipe.GLOW_FLICKER, RecipeRules.make(s).glowMode);
    }

    @Test
    public void foliageSwaysFallingWaterPoursDepthSpreadMists() {
        RegionMasks.Stats s = stats();
        s.foliage = 0.2f;
        s.fall = 0.01f;
        s.depthSpread = 0.7f;
        LivingRecipe r = RecipeRules.make(s);
        assertTrue(r.swayAmp > 0f);
        assertEquals(1f, r.pour, 0f);
        assertTrue(r.mistAmount > 0.1f);
        assertTrue("a haze, not a fog that washes the picture out", r.mistAmount <= 0.25f);
    }
}
