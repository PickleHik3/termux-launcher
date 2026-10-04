package com.termux.app.chrome.wallpaper.living;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The rules that turn scene statistics into an effect recipe (living-stills.md, Part C.4). Gemma's
 * choices win where it made them; the rules fill the rest, and are the whole answer when Gemma did
 * not run. Parameter sets are the approved prototype ones: lake is the BMO shore, pool the
 * Shin-chan pool, reflection the willow lake, noise the fountain basin.
 */
public final class RecipeRules {
    private RecipeRules() {}

    static final float DARK_SCENE = 0.3f;
    static final float DARK_SKY = 0.25f;
    static final float POOL_COVERAGE = 0.45f;
    static final float FLAT_DEPTH_STD = 0.12f;
    static final float STRONG_HORIZON = 0.15f;
    static final float MIST_SPREAD = 0.4f;
    static final float MIN_WATER = 0.03f;
    static final float MIN_SKY = 0.05f;
    static final float MIN_FOLIAGE = 0.03f;

    @NonNull
    public static LivingRecipe make(@NonNull RegionMasks.Stats s, @Nullable GemmaSceneReader.Plan plan) {
        LivingRecipe r = new LivingRecipe();
        boolean darkScene = s.sceneLum < DARK_SCENE;
        r.gemma = plan != null;

        r.drift = 0.5f + 0.5f * Math.min(1f, s.depthSpread / 0.6f);

        if (s.foliage > MIN_FOLIAGE) {
            r.swaySpeed = 1.0f;
            r.swayAmp = 0.005f;
        }

        // water
        String water = LivingRecipe.WATER_NONE;
        if (s.water > MIN_WATER) {
            String asked = plan == null ? null : plan.waterStyle;
            if (asked != null) {
                switch (asked) {
                    case "lake": water = LivingRecipe.WATER_LAKE; break;
                    case "pool": water = LivingRecipe.WATER_POOL; break;
                    case "reflection": water = LivingRecipe.WATER_REFLECTION; break;
                    case "stream": water = LivingRecipe.WATER_NOISE; break;
                    default: water = LivingRecipe.WATER_NONE; break;
                }
            } else if (s.water > POOL_COVERAGE && s.waterDepthStd < FLAT_DEPTH_STD) {
                water = LivingRecipe.WATER_POOL;
            } else if (darkScene && s.horizon > STRONG_HORIZON) {
                water = LivingRecipe.WATER_REFLECTION;
            } else {
                water = LivingRecipe.WATER_LAKE;
            }
        }
        r.waterMode = water;
        r.waterParams = waterParams(water);

        // sky
        if (s.sky > MIN_SKY) {
            String asked = plan == null ? null : plan.skyMotion;
            // A night sky twinkles whatever Gemma said, and its clouds still drift: on pong a night
            // sky with clouds round a full moon was better for both than for either.
            boolean stars = "stars".equals(asked) || s.skyLum < DARK_SKY;
            boolean none = "none".equals(asked) && !stars;
            r.skyStars = stars;
            r.skyFlow = none ? 0f : 0.03f;
        }

        if (s.fall > 0f) r.pour = 1f;

        // glow
        if (s.glow > 0f) {
            String asked = plan == null ? null : plan.lightStyle;
            String mode;
            float gain;
            if (asked != null) {
                switch (asked) {
                    case "neon": mode = LivingRecipe.GLOW_FLICKER; gain = 1.4f; break;
                    case "lamps": mode = LivingRecipe.GLOW_BREATHE; gain = 1.0f; break;
                    case "trails": mode = LivingRecipe.GLOW_TRAILS; gain = 1.2f; break;
                    case "sun": mode = LivingRecipe.GLOW_FLICKER; gain = 0.25f; break;
                    default:
                        // A bright blob in a dark picture (a moon, a lamp) breathes gently even
                        // when Gemma named no light style.
                        mode = darkScene ? LivingRecipe.GLOW_BREATHE : LivingRecipe.GLOW_NONE;
                        gain = darkScene ? 0.6f : 0f;
                        break;
                }
            } else if (s.lightsInSigns > 0.5f) {
                mode = LivingRecipe.GLOW_FLICKER;
                gain = 1.4f;
            } else {
                mode = LivingRecipe.GLOW_BREATHE;
                gain = 1.0f;
            }
            r.glowMode = mode;
            r.glowGain = gain;
            if (mode.equals(LivingRecipe.GLOW_TRAILS) && plan != null && plan.trailAngleDeg != null) {
                r.glowTrailAngleDeg = plan.trailAngleDeg;
            }
        }

        // mist
        r.mistColour = s.farColour.clone();
        if (s.depthSpread > MIST_SPREAD) {
            r.mistAmount = 0.12f + 0.13f * Math.min(1f, (s.depthSpread - MIST_SPREAD) / 0.3f);
        }

        // particles
        String particles = plan == null ? null : plan.particles;
        if (particles == null) {
            if (darkScene && s.lightsInSigns > 0.5f) particles = LivingRecipe.PARTICLES_RAIN;
            else if (darkScene && s.foliage > MIN_FOLIAGE) particles = LivingRecipe.PARTICLES_FIREFLIES;
            else if (s.water > MIN_WATER) particles = LivingRecipe.PARTICLES_GLINTS;
            else if (r.mistAmount > 0f) particles = LivingRecipe.PARTICLES_DUST;
            else particles = LivingRecipe.PARTICLES_NONE;
        }
        r.particles = particles;

        r.intensity = plan != null && plan.intensity != null ? plan.intensity : 1f;
        return r;
    }

    /**
     * The recipe a director's scene plan asks for (living-stills Part C v2). Elements the plan
     * marks {@code still} do not animate. {@code stats} supplies the measured numbers the plan
     * cannot (sky luminance, depth spread, far colour).
     */
    @NonNull
    public static LivingRecipe fromPlan(@NonNull ScenePlan plan, @NonNull RegionMasks.Stats stats) {
        LivingRecipe r = new LivingRecipe();
        r.gemma = true;
        r.plan = plan.toJson();
        r.stillProtected = true;
        r.intensity = 1f;
        r.drift = plan.has("figure") ? 0.35f : 0.6f;
        ScenePlan.Scene scene = plan.scene;
        String time = scene == null ? null : scene.time;
        String weather = scene == null ? null : scene.weather;

        // sky: clouds
        boolean hasCloudElement = plan.has("clouds");
        if (firstMoving(plan, "clouds", "drift", "wind_wave", "flow") != null) {
            r.cloudMode = LivingRecipe.CLOUDS_WARP;
            r.cloudWarpAmount = 0.6f;
            r.cloudWarpPeriod = 90f;
        }
        if (plan.has("sky") || hasCloudElement) {
            r.skyStars = "night".equals(time) || stats.skyLum < DARK_SKY;
        }

        // water
        ScenePlan.Element water = firstMoving(plan, "water");
        if (water != null) {
            String mode;
            switch (water.motion) {
                case "ripple": mode = LivingRecipe.WATER_LAKE; break;
                case "flow": mode = LivingRecipe.WATER_NOISE; break;
                case "none": case "still": mode = LivingRecipe.WATER_REFLECTION; break;
                default: mode = LivingRecipe.WATER_LAKE; break;
            }
            r.waterMode = mode;
            r.waterParams = waterParams(mode);
        }
        if (firstMoving(plan, "falling_water") != null) r.pour = 1f;

        // sway: trees and flowers
        if (firstMoving(plan, "trees", "sway") != null || firstMoving(plan, "flowers", "sway") != null) {
            r.swaySpeed = 1.0f;
            r.swayAmp = 0.005f;
        }

        // wind: grass and ground
        if (firstMoving(plan, "grass", "sway", "wind_wave") != null
            || firstMoving(plan, "ground", "sway", "wind_wave") != null) {
            String light = scene == null || scene.lightDirection == null ? ""
                : scene.lightDirection.toLowerCase(java.util.Locale.ROOT);
            r.windDirDeg = light.contains("left") ? 0f : light.contains("right") ? 180f : 20f;
            r.windSpeed = 1f;
            r.windAmp = 0.006f;
        }

        // glow: lights
        for (ScenePlan.Element e : plan.elementsOfKind("lights")) {
            if (e.still) continue;
            if ("glow".equals(e.motion)) {
                r.glowMode = LivingRecipe.GLOW_BREATHE;
                r.glowGain = 1.0f;
                break;
            } else if ("flicker".equals(e.motion)) {
                r.glowMode = LivingRecipe.GLOW_FLICKER;
                r.glowGain = 1.0f;
                break;
            } else if ("twinkle".equals(e.motion)) {
                r.glowMode = LivingRecipe.GLOW_FLICKER;
                r.glowGain = 0.5f;
                break;
            }
        }

        // particles
        String p = scene == null ? null : scene.particles;
        r.particles = LivingRecipe.PARTICLES_NONE;
        if (p != null) {
            switch (p) {
                case "dust": r.particles = LivingRecipe.PARTICLES_DUST; break;
                case "petals": r.particles = LivingRecipe.PARTICLES_PETALS; break;
                case "leaves": r.particles = LivingRecipe.PARTICLES_LEAVES; break;
                case "fireflies": r.particles = LivingRecipe.PARTICLES_FIREFLIES; break;
                case "snow": r.particles = LivingRecipe.PARTICLES_SNOW; break;
                case "rain": r.particles = LivingRecipe.PARTICLES_RAIN; break;
                case "embers": r.particles = LivingRecipe.PARTICLES_EMBERS; break;
                case "sparks": r.particles = LivingRecipe.PARTICLES_SPARKS; break;
                case "glints":
                    if (plan.has("water")) r.particles = LivingRecipe.PARTICLES_GLINTS;
                    break;
                default: break;
            }
        }

        // mist
        r.mistColour = stats.farColour.clone();
        if ("misty".equals(weather) || stats.depthSpread > MIST_SPREAD) {
            float amount = 0.12f + 0.13f * Math.min(1f, Math.max(0f, stats.depthSpread - MIST_SPREAD) / 0.3f);
            r.mistAmount = hasCloudElement ? 0.12f : amount;
        }
        return r;
    }

    /**
     * The first element of {@code kind} that is not still and whose motion is one of {@code motions}
     * (any motion when none are given), or null.
     */
    @Nullable
    private static ScenePlan.Element firstMoving(@NonNull ScenePlan plan, @NonNull String kind,
                                                 @NonNull String... motions) {
        for (ScenePlan.Element e : plan.elementsOfKind(kind)) {
            if (e.still) continue;
            if (motions.length == 0) return e;
            for (String m : motions) if (m.equals(e.motion)) return e;
        }
        return null;
    }

    @NonNull
    static Map<String, Float> waterParams(@NonNull String mode) {
        Map<String, Float> p = new LinkedHashMap<>();
        switch (mode) {
            case LivingRecipe.WATER_LAKE: set(p, 5f, 26f, 0.0045f, 0.0035f); break;
            case LivingRecipe.WATER_POOL: set(p, 4f, 4f, 0.0028f, 0.0028f); break;
            case LivingRecipe.WATER_REFLECTION: set(p, 2f, 80f, 0.007f, 0.0005f); break;
            case LivingRecipe.WATER_NOISE: set(p, 6f, 6f, 0.003f, 0.003f); break;
            default: break;
        }
        return p;
    }

    private static void set(Map<String, Float> p, float fx, float fy, float ax, float ay) {
        p.put("freqX", fx);
        p.put("freqY", fy);
        p.put("ampX", ax);
        p.put("ampY", ay);
    }
}
