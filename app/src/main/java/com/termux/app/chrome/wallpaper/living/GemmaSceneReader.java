package com.termux.app.chrome.wallpaper.living;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The optional Gemma 4 E4B step (living-stills.md, Part C.3). Gemma cannot draw masks, so it is
 * shown the photo and the same photo cut into numbered colour regions ("set-of-mark" prompting)
 * and answers with strict JSON: which numbers are water, falling water, sky, foliage, lights and
 * the subject, plus the styles. Everything here is plain Java: the prompt, the request body, the
 * strict validation and the mapping of picks to clusters. The one call into the TAI sits behind
 * {@link Chat}, so tests fake it; {@code TaiGemmaChat} is the real one. Any error, bad answer or
 * timeout gives {@code null} and the caller falls back to rules.
 */
public final class GemmaSceneReader {
    private GemmaSceneReader() {}

    /** The model that is installed; the vision call appends {@link #VISION_SUFFIX}. */
    public static final String MODEL_ID = "gemma-4-e4b-it-litert-lm";
    public static final String VISION_SUFFIX = "-vision";
    public static final long TIMEOUT_MS = 60_000L;
    /** The engine window the call asks for: two images, the prompt and the answer, with room to spare. */
    static final int CONTEXT_WINDOW = 2048;

    public static final List<String> STYLES = Arrays.asList("photo", "illustration", "flat_graphic");
    public static final List<String> WATER_STYLES = Arrays.asList("lake", "pool", "reflection", "stream", "none");
    public static final List<String> SKY_MOTIONS = Arrays.asList("clouds", "stars", "none");
    public static final List<String> LIGHT_STYLES = Arrays.asList("neon", "lamps", "trails", "sun", "none");
    public static final List<String> PARTICLES = Arrays.asList("none", "glints", "fireflies", "rain", "snow", "dust", "debris");
    /** The region groups Gemma may name. */
    public static final List<String> REGION_KEYS =
        Arrays.asList("water", "falling_water", "sky", "foliage", "lights", "subject");

    /** The single call out to the model: a chat-completions body in, the reply text out. */
    public interface Chat {
        /** @throws IOException on any failure or when {@code timeoutMs} passes */
        @NonNull
        String complete(@NonNull String requestBody, long timeoutMs) throws IOException;

        /** The backend the latest successful {@link #complete} ran on, or {@code null} when unknown. */
        @Nullable
        default String lastAccelerator() {
            return null;
        }

        /** Why that backend was not the model's first choice, or {@code null} when it was or is unknown. */
        @Nullable
        default String lastFallbackReason() {
            return null;
        }
    }

    /** What Gemma decided. Style fields are {@code null} when it left them out. */
    public static final class Plan {
        @NonNull public final String style;
        /** Group key to 1-based cluster numbers; a missing or empty entry means no pick. */
        @NonNull public final Map<String, int[]> regions;
        @Nullable public final String waterStyle;
        @Nullable public final String skyMotion;
        @Nullable public final String lightStyle;
        @Nullable public final Float trailAngleDeg;
        @Nullable public final String particles;
        @Nullable public final Float intensity;

        Plan(@NonNull String style, @NonNull Map<String, int[]> regions, @Nullable String waterStyle,
             @Nullable String skyMotion, @Nullable String lightStyle, @Nullable Float trailAngleDeg,
             @Nullable String particles, @Nullable Float intensity) {
            this.style = style;
            this.regions = regions;
            this.waterStyle = waterStyle;
            this.skyMotion = skyMotion;
            this.lightStyle = lightStyle;
            this.trailAngleDeg = trailAngleDeg;
            this.particles = particles;
            this.intensity = intensity;
        }
    }

    /** The request model id for the vision call. */
    @NonNull
    public static String visionModelId() {
        return MODEL_ID + VISION_SUFFIX;
    }

    /**
     * The prompt, with one line per mark so the model can tie a number to a colour and a size.
     */
    @NonNull
    public static String buildPrompt(@NonNull ColourClusters.Result clusters) {
        StringBuilder sb = new StringBuilder(1400);
        sb.append("You are helping to animate a still picture used as a phone wallpaper. ")
            .append("Image 1 is the picture. Image 2 is the same picture cut into numbered colour regions: ")
            .append("each region is outlined and carries its number.\n")
            .append("The regions, by number, with their average colour and share of the picture:\n");
        for (int i = 0; i < clusters.k; i++) {
            sb.append(i + 1).append(": #").append(String.format(Locale.US, "%06x", clusters.meanRgb[i] & 0xFFFFFF))
                .append(", ").append(Math.round(clusters.area[i] * 100f)).append("%\n");
        }
        sb.append("Pick regions by number. List a region only when most of it really is the thing named; ")
            .append("leave a list empty when the picture has none of it. Regions are for: ")
            .append("water (lakes, sea, pools, puddles, including painted water), ")
            .append("falling_water (waterfalls, fountains, streams pouring), ")
            .append("sky (sky and clouds, or a night sky), ")
            .append("foliage (trees, grass, bushes, leaves that sway), ")
            .append("lights (lamps, neon signs, windows, glowing streaks), ")
            .append("subject (the main character or object in front).\n")
            .append("Then choose the styles: water_style lake, pool (flat water seen from above), reflection ")
            .append("(still water mirroring the scene), stream, or none; sky_motion clouds, stars, or none; ")
            .append("light_style neon, lamps, trails (light streaks, then give trail_angle_deg, the direction ")
            .append("they run in degrees, 0 = right, 90 = down), sun (dappled sunlight), or none; ")
            .append("particles none, glints, fireflies, rain, snow, dust, or debris; ")
            .append("intensity from 0.5 (calm) to 1.5 (lively).\n")
            .append("Answer with one JSON object and nothing else, in exactly this shape:\n")
            .append("{\"style\": \"photo|illustration|flat_graphic\", ")
            .append("\"regions\": {\"water\": [], \"falling_water\": [], \"sky\": [], \"foliage\": [], ")
            .append("\"lights\": [], \"subject\": []}, ")
            .append("\"water_style\": \"lake|pool|reflection|stream|none\", ")
            .append("\"sky_motion\": \"clouds|stars|none\", ")
            .append("\"light_style\": \"neon|lamps|trails|sun|none\", ")
            .append("\"trail_angle_deg\": null, ")
            .append("\"particles\": \"none|glints|fireflies|rain|snow|dust|debris\", ")
            .append("\"intensity\": 1.0}");
        return sb.toString();
    }

    /** The chat-completions body: the prompt and two images, given as data URLs. */
    @NonNull
    public static String buildRequest(@NonNull String prompt, @NonNull String photoUrl,
                                      @NonNull String markedUrl) throws JSONException {
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", prompt));
        content.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", photoUrl)));
        content.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", markedUrl)));
        JSONObject message = new JSONObject().put("role", "user").put("content", content);
        JSONObject body = new JSONObject();
        body.put("model", visionModelId());
        body.put("messages", new JSONArray().put(message));
        body.put("temperature", 0);
        body.put("max_tokens", 400);
        body.put("stream", false);
        // Two images, the prompt and a 400-token answer fit in about 1.5k tokens; the automatic
        // window (4096) only made the KV cache bigger.
        body.put("context_window", CONTEXT_WINDOW);
        return body.toString();
    }

    /**
     * Asks Gemma and validates the answer.
     *
     * @return the plan, or {@code null} on any error, timeout or invalid answer
     */
    @Nullable
    public static Plan read(@NonNull Chat chat, @NonNull String photoUrl, @NonNull String markedUrl,
                            @NonNull ColourClusters.Result clusters) {
        try {
            String body = buildRequest(buildPrompt(clusters), photoUrl, markedUrl);
            return parse(chat.complete(body, TIMEOUT_MS), clusters.k);
        } catch (IOException | JSONException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Strict validation of the model's text. Code fences and prose around the object are
     * tolerated; inside it every present field must be well formed. {@code style} and
     * {@code regions} are required, so an answer without them is rejected; the other keys may be
     * missing (partial answer) and stay {@code null} for the rules to decide.
     *
     * @param clusterCount marks are valid in 1..clusterCount
     * @return the plan, or {@code null} when the text is not a valid answer
     */
    @Nullable
    public static Plan parse(@Nullable String text, int clusterCount) {
        if (text == null) return null;
        int a = text.indexOf('{');
        int b = text.lastIndexOf('}');
        if (a < 0 || b <= a) return null;
        try {
            JSONObject o = new JSONObject(text.substring(a, b + 1));
            String style = enumValue(o, "style", STYLES, true);
            if (style == null) return null;
            Object regionsObj = o.opt("regions");
            if (!(regionsObj instanceof JSONObject)) return null;
            JSONObject regionsJson = (JSONObject) regionsObj;
            Map<String, int[]> regions = new LinkedHashMap<>();
            for (String key : REGION_KEYS) {
                if (!regionsJson.has(key) || regionsJson.isNull(key)) continue;
                Object v = regionsJson.get(key);
                if (!(v instanceof JSONArray)) return null;
                int[] marks = marks((JSONArray) v, clusterCount);
                if (marks == null) return null;
                if (marks.length > 0) regions.put(key, marks);
            }
            String water = enumValue(o, "water_style", WATER_STYLES, false);
            String sky = enumValue(o, "sky_motion", SKY_MOTIONS, false);
            String light = enumValue(o, "light_style", LIGHT_STYLES, false);
            String particles = enumValue(o, "particles", PARTICLES, false);
            if (o.has("water_style") && !o.isNull("water_style") && water == null) return null;
            if (o.has("sky_motion") && !o.isNull("sky_motion") && sky == null) return null;
            if (o.has("light_style") && !o.isNull("light_style") && light == null) return null;
            if (o.has("particles") && !o.isNull("particles") && particles == null) return null;
            Float angle = null;
            if (o.has("trail_angle_deg") && !o.isNull("trail_angle_deg")) {
                Object v = o.get("trail_angle_deg");
                if (!(v instanceof Number)) return null;
                double d = ((Number) v).doubleValue();
                if (Double.isNaN(d) || d < -360 || d > 360) return null;
                angle = (float) d;
            }
            Float intensity = null;
            if (o.has("intensity") && !o.isNull("intensity")) {
                Object v = o.get("intensity");
                if (!(v instanceof Number)) return null;
                double d = ((Number) v).doubleValue();
                if (Double.isNaN(d) || d < 0.5 || d > 1.5) return null;
                intensity = (float) d;
            }
            return new Plan(style, regions, water, sky, light, angle, particles, intensity);
        } catch (JSONException e) {
            return null;
        }
    }

    /** The value of a string field when it is one of {@code allowed}, else {@code null}. */
    @Nullable
    private static String enumValue(JSONObject o, String key, List<String> allowed, boolean required) {
        Object v = o.opt(key);
        if (!(v instanceof String)) return null;
        String s = ((String) v).trim().toLowerCase(Locale.US);
        return allowed.contains(s) ? s : null;
    }

    /**
     * Distinct integers in 1..count, or {@code null} when any entry is not a whole number. A number
     * past the last region is dropped rather than failing the answer: a small model miscounts. A
     * whole number written as a string ({@code "3"}) counts: on pong, Gemma 4 E2B quoted every mark
     * in four answers out of six (2026-10-04), and rejecting those threw the whole reading away.
     */
    @Nullable
    private static int[] marks(JSONArray a, int count) {
        int[] tmp = new int[a.length()];
        int n = 0;
        for (int i = 0; i < a.length(); i++) {
            Object v = a.opt(i);
            if (v instanceof String) {
                try {
                    v = Double.valueOf(((String) v).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            if (!(v instanceof Number)) return null;
            double d = ((Number) v).doubleValue();
            if (d != Math.rint(d)) return null;
            if (d < 1 || d > count) continue;
            int m = (int) d;
            boolean seen = false;
            for (int j = 0; j < n; j++) if (tmp[j] == m) seen = true;
            if (!seen) tmp[n++] = m;
        }
        return Arrays.copyOf(tmp, n);
    }
}
