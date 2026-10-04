package com.termux.app.chrome.wallpaper.living;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which effects a living still plays and in what style (living-stills.md, Part C.4 and C.5): the
 * parsed form of {@code recipe.json}. A plain data holder; {@link RecipeRules} fills it and
 * {@link GemmaSceneReader} may steer it. The numbers are the ones the developer approved in the
 * browser prototype ({@code wall-alive/page.html}, {@code RECIPES}); the renderer reads them as
 * uniforms. The file is versioned: a reader refuses a version it does not know.
 */
public final class LivingRecipe {
    /** The recipe format this code writes and reads. */
    public static final int VERSION = 1;

    public static final String WATER_NONE = "none";
    public static final String WATER_NOISE = "noise";
    public static final String WATER_LAKE = "lake";
    public static final String WATER_POOL = "pool";
    public static final String WATER_REFLECTION = "reflection";

    public static final String GLOW_NONE = "none";
    public static final String GLOW_BREATHE = "breathe";
    public static final String GLOW_FLICKER = "flicker";
    public static final String GLOW_TRAILS = "trails";

    public static final String PARTICLES_NONE = "none";
    public static final String PARTICLES_GLINTS = "glints";
    public static final String PARTICLES_FIREFLIES = "fireflies";
    public static final String PARTICLES_RAIN = "rain";
    public static final String PARTICLES_SNOW = "snow";
    public static final String PARTICLES_DUST = "dust";
    public static final String PARTICLES_DEBRIS = "debris";

    public int version = VERSION;
    /** Depth drift strength, 0..1. Always on. */
    public float drift = 0.7f;
    public float swaySpeed;
    /** Sway amplitude in screen fractions; 0 means off. */
    public float swayAmp;
    @NonNull public String waterMode = WATER_NONE;
    /** {@code freqX, freqY, ampX, ampY} for the water modes; empty when off. */
    @NonNull public Map<String, Float> waterParams = new LinkedHashMap<>();
    public float skyFlow;
    public boolean skyStars;
    /** Falling-water strength, 0 = no pour. */
    public float pour;
    @NonNull public String glowMode = GLOW_NONE;
    public float glowGain;
    public float glowTrailAngleDeg;
    /** Fog colour, 0..1 each. */
    @NonNull public float[] mistColour = {0.5f, 0.5f, 0.5f};
    public float mistAmount;
    @NonNull public String particles = PARTICLES_NONE;
    public float intensity = 1f;
    /** Gemma picked regions or styles for this recipe. */
    public boolean gemma;
    @Nullable public String gemmaModel;
    /** The backend Gemma's step ran on ("gpu", "cpu"), when the TAI said. */
    @Nullable public String gemmaAccelerator;
    /** Why that was not the model's first choice (a recorded failure or the memory budget), when it was not. */
    @Nullable public String gemmaFallbackReason;
    /** Model ids the analysis used, by role. */
    @NonNull public Map<String, String> models = new LinkedHashMap<>();
    /** Wall-clock timings of the build, by stage, in milliseconds. */
    @NonNull public Map<String, Long> timingsMs = new LinkedHashMap<>();

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("version", version);
        o.put("drift", drift);
        o.put("sway", new JSONObject().put("speed", swaySpeed).put("amp", swayAmp));
        JSONObject params = new JSONObject();
        for (Map.Entry<String, Float> e : waterParams.entrySet()) params.put(e.getKey(), e.getValue());
        o.put("water", new JSONObject().put("mode", waterMode).put("params", params));
        o.put("sky", new JSONObject().put("flow", skyFlow).put("stars", skyStars));
        o.put("pour", pour);
        o.put("glow", new JSONObject().put("mode", glowMode).put("gain", glowGain)
            .put("trailAngleDeg", glowTrailAngleDeg));
        JSONArray colour = new JSONArray();
        for (float c : mistColour) colour.put(c);
        o.put("mist", new JSONObject().put("colour", colour).put("amount", mistAmount));
        o.put("particles", new JSONObject().put("kind", particles));
        o.put("intensity", intensity);
        o.put("gemma", gemma);
        if (gemmaModel != null) o.put("gemmaModel", gemmaModel);
        if (gemmaAccelerator != null) o.put("gemmaAccelerator", gemmaAccelerator);
        if (gemmaFallbackReason != null) o.put("gemmaFallbackReason", gemmaFallbackReason);
        JSONObject m = new JSONObject();
        for (Map.Entry<String, String> e : models.entrySet()) m.put(e.getKey(), e.getValue());
        o.put("models", m);
        JSONObject t = new JSONObject();
        for (Map.Entry<String, Long> e : timingsMs.entrySet()) t.put(e.getKey(), e.getValue());
        o.put("timingsMs", t);
        return o;
    }

    /** @throws JSONException when the text is not a recipe, or its version is not {@link #VERSION} */
    @NonNull
    public static LivingRecipe fromJson(@NonNull String text) throws JSONException {
        JSONObject o = new JSONObject(text);
        int version = o.getInt("version");
        if (version != VERSION) throw new JSONException("Unsupported recipe version " + version);
        LivingRecipe r = new LivingRecipe();
        r.version = version;
        r.drift = (float) o.optDouble("drift", r.drift);
        JSONObject sway = o.optJSONObject("sway");
        if (sway != null) {
            r.swaySpeed = (float) sway.optDouble("speed", 0);
            r.swayAmp = (float) sway.optDouble("amp", 0);
        }
        JSONObject water = o.optJSONObject("water");
        if (water != null) {
            r.waterMode = water.optString("mode", WATER_NONE);
            JSONObject p = water.optJSONObject("params");
            if (p != null) {
                Iterator<String> keys = p.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    r.waterParams.put(k, (float) p.optDouble(k, 0));
                }
            }
        }
        JSONObject sky = o.optJSONObject("sky");
        if (sky != null) {
            r.skyFlow = (float) sky.optDouble("flow", 0);
            r.skyStars = sky.optBoolean("stars", false);
        }
        r.pour = (float) o.optDouble("pour", 0);
        JSONObject glow = o.optJSONObject("glow");
        if (glow != null) {
            r.glowMode = glow.optString("mode", GLOW_NONE);
            r.glowGain = (float) glow.optDouble("gain", 0);
            r.glowTrailAngleDeg = (float) glow.optDouble("trailAngleDeg", 0);
        }
        JSONObject mist = o.optJSONObject("mist");
        if (mist != null) {
            JSONArray c = mist.optJSONArray("colour");
            if (c != null && c.length() >= 3) {
                r.mistColour = new float[] {(float) c.optDouble(0, 0.5), (float) c.optDouble(1, 0.5), (float) c.optDouble(2, 0.5)};
            }
            r.mistAmount = (float) mist.optDouble("amount", 0);
        }
        JSONObject particles = o.optJSONObject("particles");
        if (particles != null) r.particles = particles.optString("kind", PARTICLES_NONE);
        r.intensity = (float) o.optDouble("intensity", 1);
        r.gemma = o.optBoolean("gemma", false);
        r.gemmaModel = o.has("gemmaModel") ? o.optString("gemmaModel") : null;
        r.gemmaAccelerator = o.has("gemmaAccelerator") ? o.optString("gemmaAccelerator") : null;
        r.gemmaFallbackReason = o.has("gemmaFallbackReason") ? o.optString("gemmaFallbackReason") : null;
        JSONObject models = o.optJSONObject("models");
        if (models != null) {
            Iterator<String> keys = models.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                r.models.put(k, models.optString(k, ""));
            }
        }
        JSONObject timings = o.optJSONObject("timingsMs");
        if (timings != null) {
            Iterator<String> keys = timings.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                r.timingsMs.put(k, timings.optLong(k, 0));
            }
        }
        return r;
    }
}
