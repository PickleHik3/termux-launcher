package com.termux.app.chrome.wallpaper.living;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** {@link RecipeRules#fromPlan} on real Gallery plans and small synthetic ones. */
public class RecipeRulesFromPlanTest {

    private static RegionMasks.Stats stats() {
        RegionMasks.Stats s = new RegionMasks.Stats();
        s.sceneLum = 0.6f;
        s.skyLum = 0.7f;
        s.depthSpread = 0.3f;
        return s;
    }

    private static ScenePlan plan(String elements, String scene) throws Exception {
        return ScenePlan.fromJson(new JSONObject("{\"elements\":[" + elements + "],\"scene\":{" + scene + "}}"));
    }

    private static String el(String name, String kind, String motion) {
        return "{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\",\"box\":[100,100,500,500],"
            + "\"depth\":\"middle\",\"motion\":\"" + motion + "\"}";
    }

    private static void checkSamurai(String file) throws Exception {
        File f = new File("/tmp/gemma-cmp/gallery-answers/" + file);
        assumeTrue(f.isFile());
        ScenePlan p = ScenePlan.fromJson(new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
        assertNotNull(p);
        LivingRecipe r = RecipeRules.fromPlan(p, stats());
        assertEquals(LivingRecipe.CLOUDS_WARP, r.cloudMode);
        assertEquals(0.6f, r.cloudWarpAmount, 1e-6f);
        assertEquals(90f, r.cloudWarpPeriod, 1e-6f);
        assertTrue(r.windAmp > 0f);
        assertEquals(180f, r.windDirDeg, 0f);
        assertEquals(LivingRecipe.PARTICLES_PETALS, r.particles);
        assertEquals(LivingRecipe.WATER_NONE, r.waterMode);
        assertTrue(r.stillProtected);
        assertEquals(0.35f, r.drift, 1e-6f);
        assertEquals(1f, r.intensity, 0f);
        assertNotNull(r.plan);
    }

    @Test
    public void galleryRoundOneSamuraiPlan() throws Exception {
        checkSamurai("e4b-round1.json");
    }

    @Test
    public void galleryRoundTwoSamuraiPlan() throws Exception {
        checkSamurai("e4b-round2-speculative.json");
    }

    @Test
    public void windDirectionFollowsTheLightText() throws Exception {
        String grass = el("grass", "grass", "sway") + "," + el("sky", "sky", "still");
        assertEquals(0f, RecipeRules.fromPlan(plan(grass, "\"light_direction\":\"sun from the left\""), stats()).windDirDeg, 0f);
        assertEquals(180f, RecipeRules.fromPlan(plan(grass, "\"light_direction\":\"sun from the right\""), stats()).windDirDeg, 0f);
        assertEquals(20f, RecipeRules.fromPlan(plan(grass, "\"light_direction\":\"overhead\""), stats()).windDirDeg, 0f);
    }

    @Test
    public void glintsNeedWaterAndDriftFollowsTheFigure() throws Exception {
        String sky = el("sky", "sky", "still") + "," + el("hill", "mountain", "none");
        LivingRecipe dry = RecipeRules.fromPlan(plan(sky, "\"particles\":\"glints\""), stats());
        assertEquals(LivingRecipe.PARTICLES_NONE, dry.particles);
        assertEquals(0.6f, dry.drift, 1e-6f);
        assertEquals(LivingRecipe.CLOUDS_NONE, dry.cloudMode);
        LivingRecipe wet = RecipeRules.fromPlan(
            plan(sky + "," + el("lake", "water", "ripple"), "\"particles\":\"glints\""), stats());
        assertEquals(LivingRecipe.PARTICLES_GLINTS, wet.particles);
        assertEquals(LivingRecipe.WATER_LAKE, wet.waterMode);
    }

    @Test
    public void waterModeFollowsMotionAndNightBringsStars() throws Exception {
        String sky = el("sky", "sky", "still");
        assertEquals(LivingRecipe.WATER_NOISE, RecipeRules.fromPlan(
            plan(sky + "," + el("w", "water", "flow"), "\"time\":\"night\""), stats()).waterMode);
        LivingRecipe still = RecipeRules.fromPlan(
            plan(sky + "," + el("w", "water", "still"), "\"time\":\"night\""), stats());
        assertEquals(LivingRecipe.WATER_REFLECTION, still.waterMode);
        assertTrue(still.skyStars);
        assertTrue(!RecipeRules.fromPlan(plan(sky + "," + el("w", "water", "still"), "\"time\":\"day\""), stats()).skyStars);
    }

    @Test
    public void mistNeedsMistyWeatherOrDepthSpread() throws Exception {
        String sky = el("sky", "sky", "still") + "," + el("hill", "mountain", "none");
        assertEquals(0f, RecipeRules.fromPlan(plan(sky, "\"weather\":\"clear\""), stats()).mistAmount, 0f);
        assertTrue(RecipeRules.fromPlan(plan(sky, "\"weather\":\"misty\""), stats()).mistAmount > 0f);
        RegionMasks.Stats deep = stats();
        deep.depthSpread = 0.7f;
        assertEquals(0.25f, RecipeRules.fromPlan(plan(sky, "\"weather\":\"clear\""), deep).mistAmount, 1e-6f);
        String cloudy = sky + "," + el("c", "clouds", "drift");
        assertEquals(0.12f, RecipeRules.fromPlan(plan(cloudy, "\"weather\":\"clear\""), deep).mistAmount, 1e-6f);
    }

    @Test
    public void lightsMapToGlowByMotion() throws Exception {
        String sky = el("sky", "sky", "still");
        LivingRecipe t = RecipeRules.fromPlan(plan(sky + "," + el("l", "lights", "twinkle"), "\"time\":\"day\""), stats());
        assertEquals(LivingRecipe.GLOW_FLICKER, t.glowMode);
        assertEquals(0.5f, t.glowGain, 0f);
        assertEquals(LivingRecipe.GLOW_BREATHE,
            RecipeRules.fromPlan(plan(sky + "," + el("l", "lights", "glow"), "\"time\":\"day\""), stats()).glowMode);
    }
}
