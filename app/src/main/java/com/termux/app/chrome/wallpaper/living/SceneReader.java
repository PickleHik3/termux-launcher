package com.termux.app.chrome.wallpaper.living;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The v2 director (living-stills-part-c-v2.md, piece A). The set-of-mark prompt made Gemma match
 * meaning to numbered colour blobs, and that is where it failed; here it is asked in words for the
 * scene's elements, each with a kind, a box, a depth and a motion, from the one photo. The model call
 * sits behind {@link Chat}, so tests fake it. Any error, timeout or unusable answer
 * gives {@code null}.
 */
public final class SceneReader {
    private SceneReader() {}

    public static final String E4B_ID = "gemma-4-e4b-it-litert-lm";
    public static final String E2B_ID = "gemma-4-e2b-it-litert-lm";
    /** The suffix the TAI gives a model id for its vision-enabled load. */
    public static final String VISION_SUFFIX = "-vision";
    /**
     * The whole call, load included. On pong the GPU load alone took 15 s and the elements answer
     * about 45 s more with speculative decoding, so 60 s cut the first on-device reading off and
     * the recipe fell back to rules; the job runs behind a progress bar, so it may take its time.
     */
    public static final long TIMEOUT_MS = 180_000L;
    public static final int CONTEXT_WINDOW = 2048;
    static final int MAX_TOKENS = 700;

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

    /** The Gallery prompt, minus the {@code note} (about 40% of the output tokens). */
    public static final String PROMPT =
        "You are helping animate this picture as a phone wallpaper. The wallpaper only moves gently: parts of the scene can drift, sway, ripple, glow, or release small particles. First look carefully, then describe every visible element of the scene as a list.\n"
        + "\n"
        + "For each element give:\n"
        + "- name: what it is, in two or three words (for example \"pink grass field\", \"cumulus clouds\", \"lone cloaked figure\", \"snow-capped mountain\", \"dark pine trees\", \"calm lake\", \"neon sign\").\n"
        + "- kind: exactly one of sky, clouds, water, falling_water, grass, trees, flowers, mountain, ground, building, lights, figure, animal, vehicle, other.\n"
        + "- box: where it is, as [top, left, bottom, right] on a 0 to 1000 grid where 0,0 is the top-left corner of the picture and 1000,1000 the bottom-right.\n"
        + "- depth: near, middle, or far.\n"
        + "- motion: how this element would move in real life if a light breeze came through, as one of none, drift, sway, wind_wave, ripple, flow, glow, flicker, twinkle, or still.\n"
        + "\n"
        + "Then describe the whole scene:\n"
        + "- time: dawn, day, dusk, or night.\n"
        + "- weather: clear, cloudy, misty, rain, or snow.\n"
        + "- light_direction: where the light comes from (for example \"low sun from the right\").\n"
        + "- particles: small things that could float in the air here, one of none, petals, leaves, dust, fireflies, snow, rain, embers, glints, or sparks.\n"
        + "- mood: two or three words.\n"
        + "- do_not_animate: anything that must stay perfectly still (for example a face or a figure), as a list of names.\n"
        + "\n"
        + "Rules: only list what is really in the picture. Do not invent water, sky, or lights that are not there. Be exact about the boxes. Answer with one JSON object and nothing else, in this shape:\n"
        + "\n"
        + "{\"elements\": [{\"name\": \"\", \"kind\": \"\", \"box\": [0, 0, 0, 0], \"depth\": \"\", \"motion\": \"\"}], \"scene\": {\"time\": \"\", \"weather\": \"\", \"light_direction\": \"\", \"particles\": \"\", \"mood\": \"\", \"do_not_animate\": []}}";

    // Which model reads the photo is the WALLPAPER_READER function's resolution: see LivingReader.

    /**
     * The chat-completions body: one user message with the prompt and the one photo ({@code photoDataUrl},
     * a data URL the caller makes). {@code context_window}, {@code load_class},
     * {@code speculative_decoding}, {@code thinking} and {@code accelerator} are TAI-local extensions:
     * the remote seam in {@code TaiManager} strips them for a {@code remote/<id>} model. Thinking stays
     * off because it corrupted the JSON on both models; speculative decoding made E4B 2.4x faster.
     */
    @NonNull
    public static String buildRequest(@NonNull String modelId, @NonNull String photoDataUrl) throws JSONException {
        return buildRequest(modelId, null, photoDataUrl);
    }

    /**
     * As above with the resolved {@code accelerator} ({@code gpu} or {@code cpu}) for an on-device
     * model; {@code null}, or a {@code remote/<id>} model, sends none.
     */
    @NonNull
    public static String buildRequest(@NonNull String modelId, @Nullable String accelerator,
                                      @NonNull String photoDataUrl) throws JSONException {
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", PROMPT));
        content.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", photoDataUrl)));
        JSONObject message = new JSONObject().put("role", "user").put("content", content);
        JSONObject body = new JSONObject();
        body.put("model", modelId);
        body.put("messages", new JSONArray().put(message));
        body.put("temperature", 0);
        body.put("max_tokens", MAX_TOKENS);
        body.put("stream", false);
        body.put("context_window", CONTEXT_WINDOW);
        body.put("load_class", "momentary");
        body.put("speculative_decoding", true);
        body.put("thinking", false);
        if (accelerator != null && !modelId.startsWith("remote/")) body.put("accelerator", accelerator);
        return body.toString();
    }

    /** {@link #buildRequest(String, String)} for the E4B vision model. */
    @NonNull
    public static String buildRequest(@NonNull String photoDataUrl) throws JSONException {
        return buildRequest(E4B_ID + VISION_SUFFIX, photoDataUrl);
    }

    /**
     * Asks the E4B vision model and validates the answer.
     *
     * @return the plan, or {@code null} on any error, timeout or unusable answer
     */
    @Nullable
    public static ScenePlan read(@NonNull Chat chat, @NonNull String photoUrl) {
        return read(chat, E4B_ID + VISION_SUFFIX, photoUrl);
    }

    /** As {@link #read(Chat, String)} with the model id from {@link LivingReader}. */
    @Nullable
    public static ScenePlan read(@NonNull Chat chat, @NonNull String modelId,
                                 @NonNull String photoUrl) {
        return read(chat, modelId, null, photoUrl);
    }

    /** As above, asking on {@code accelerator} ({@code null} leaves it to the runtime). */
    @Nullable
    public static ScenePlan read(@NonNull Chat chat, @NonNull String modelId, @Nullable String accelerator,
                                 @NonNull String photoUrl) {
        try {
            return parse(chat.complete(buildRequest(modelId, accelerator, photoUrl), TIMEOUT_MS));
        } catch (IOException | JSONException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Validation of the model's text. Code fences and prose around the object are tolerated, and up to
     * two closing braces the model forgot are added. Bad elements are dropped, not the answer; the answer
     * is rejected when fewer than two elements survive or {@code scene} is missing.
     */
    @Nullable
    public static ScenePlan parse(@Nullable String text) {
        if (text == null) return null;
        int a = text.indexOf('{');
        int b = text.lastIndexOf('}');
        if (a < 0 || b <= a) return null;
        String body = text.substring(a, b + 1);
        for (int missing = 0; missing <= 2; missing++) {
            try {
                ScenePlan plan = parseObject(new JSONObject(body));
                // Two elements is the floor for a model answer; a persisted plan (ScenePlan.fromJson)
                // may legitimately hold one, so the rule lives here and not in parseObject.
                return plan == null || plan.elements.size() < 2 ? null : plan;
            } catch (JSONException e) {
                body += "}";
            }
        }
        return null;
    }

    @Nullable
    static ScenePlan parseObject(@NonNull JSONObject o) {
        JSONArray els = o.optJSONArray("elements");
        JSONObject sceneJson = o.optJSONObject("scene");
        if (els == null || sceneJson == null) return null;

        List<String> dnaNames = new ArrayList<>();
        JSONArray dna = sceneJson.optJSONArray("do_not_animate");
        if (dna != null) {
            for (int i = 0; i < dna.length(); i++) {
                Object v = dna.opt(i);
                if (v instanceof String && !((String) v).trim().isEmpty()) dnaNames.add(((String) v).trim());
            }
        }
        Set<String> dnaLower = new HashSet<>();
        for (String n : dnaNames) dnaLower.add(ScenePlan.lower(n));

        List<ScenePlan.Element> elements = new ArrayList<>();
        for (int i = 0; i < els.length(); i++) {
            JSONObject e = els.optJSONObject(i);
            if (e == null) continue;
            String kind = enumValue(e.opt("kind"), ScenePlan.KINDS);
            String motion = enumValue(e.opt("motion"), ScenePlan.MOTIONS);
            if (kind == null || motion == null) continue;
            int[] box = box(e.opt("box"));
            if (box == null) continue;
            String depth = enumValue(e.opt("depth"), ScenePlan.DEPTHS);
            if (depth == null) depth = "middle";
            Object nameObj = e.opt("name");
            String name = nameObj instanceof String ? ((String) nameObj).trim() : "";
            if (name.isEmpty()) name = kind;
            elements.add(new ScenePlan.Element(name, kind, box, depth, motion, dnaLower.contains(ScenePlan.lower(name))));
        }
        if (elements.isEmpty()) return null;

        String particles = enumValue(sceneJson.opt("particles"), ScenePlan.PARTICLES);
        ScenePlan.Scene scene = new ScenePlan.Scene(
            enumValue(sceneJson.opt("time"), ScenePlan.TIMES),
            enumValue(sceneJson.opt("weather"), ScenePlan.WEATHERS),
            text(sceneJson.opt("light_direction")),
            particles == null ? "none" : particles,
            text(sceneJson.opt("mood")),
            dnaNames);
        return new ScenePlan(elements, scene);
    }

    @Nullable
    private static String enumValue(Object v, List<String> allowed) {
        if (!(v instanceof String)) return null;
        String s = ScenePlan.lower((String) v);
        return allowed.contains(s) ? s : null;
    }

    @NonNull
    private static String text(Object v) {
        return v instanceof String ? ((String) v).trim() : "";
    }

    /** {top, left, bottom, right} in 0..1000 with top &lt; bottom and left &lt; right, or {@code null}. */
    @Nullable
    private static int[] box(Object v) {
        if (!(v instanceof JSONArray)) return null;
        JSONArray a = (JSONArray) v;
        if (a.length() != 4) return null;
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            Object x = a.opt(i);
            if (x instanceof String) {
                try {
                    x = Double.valueOf(((String) x).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            if (!(x instanceof Number)) return null;
            double d = ((Number) x).doubleValue();
            if (Double.isNaN(d) || d < 0 || d > 1000) return null;
            out[i] = (int) Math.round(d);
        }
        if (out[0] >= out[2] || out[1] >= out[3]) return null;
        return out;
    }
}
