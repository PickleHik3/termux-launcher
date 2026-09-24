package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TaiModelProfile {
    // Value is Gallery's model_allowlist 1_0_19 (the constant name is kept for source
    // compatibility with existing profile source strings). The allowlist values are unchanged
    // from 1_0_15 through 1_0_19 (checked by parsing each file); only the label was stale.
    public static final String SOURCE_EDGE_GALLERY_1_0_15 = "google-ai-edge-gallery-1.0.19";
    public static final String SOURCE_LITERT_COMMUNITY = "litert-community-model-card";
    public static final String THINKING_NONE = "none";
    public static final String THINKING_TOGGLEABLE = "toggleable";
    public static final String THINKING_ALWAYS = "always";

    public final List<String> compatibleAccelerators;
    public final int defaultMaxTokens;
    public final int defaultTopK;
    public final double defaultTopP;
    public final double defaultTemperature;
    @Nullable public final Integer minDeviceMemoryInGb;
    public final String source;
    public final String thinkingMode;
    public final int maxContextTokens;
    @Nullable public final String thinkingChannelStart;
    @Nullable public final String thinkingChannelEnd;

    public TaiModelProfile(
        @NonNull List<String> compatibleAccelerators,
        int defaultMaxTokens,
        int defaultTopK,
        double defaultTopP,
        double defaultTemperature,
        @Nullable Integer minDeviceMemoryInGb,
        @NonNull String source
    ) {
        this(compatibleAccelerators, defaultMaxTokens, defaultTopK, defaultTopP,
            defaultTemperature, minDeviceMemoryInGb, source, THINKING_NONE, null, null);
    }

    public TaiModelProfile(
        @NonNull List<String> compatibleAccelerators,
        int defaultMaxTokens,
        int defaultTopK,
        double defaultTopP,
        double defaultTemperature,
        @Nullable Integer minDeviceMemoryInGb,
        @NonNull String source,
        @NonNull String thinkingMode,
        @Nullable String thinkingChannelStart,
        @Nullable String thinkingChannelEnd
    ) {
        this(compatibleAccelerators, defaultMaxTokens, defaultTopK, defaultTopP, defaultTemperature,
            minDeviceMemoryInGb, source, thinkingMode, thinkingChannelStart, thinkingChannelEnd, 0);
    }

    public TaiModelProfile(List<String> compatibleAccelerators, int defaultMaxTokens, int defaultTopK,
            double defaultTopP, double defaultTemperature, Integer minDeviceMemoryInGb, String source,
            String thinkingMode, String thinkingChannelStart, String thinkingChannelEnd, int maxContextTokens) {
        this.maxContextTokens = Math.max(0, maxContextTokens);
        ArrayList<String> normalized = new ArrayList<>();
        for (String accelerator : compatibleAccelerators) {
            String value = normalizeAccelerator(accelerator);
            if (value != null && !normalized.contains(value)) normalized.add(value);
        }
        if (normalized.isEmpty()) normalized.add("cpu");
        this.compatibleAccelerators = Collections.unmodifiableList(normalized);
        this.defaultMaxTokens = Math.max(1, defaultMaxTokens);
        this.defaultTopK = Math.max(1, defaultTopK);
        this.defaultTopP = defaultTopP;
        this.defaultTemperature = defaultTemperature;
        this.minDeviceMemoryInGb = minDeviceMemoryInGb;
        this.source = source;
        this.thinkingMode = normalizeThinkingMode(thinkingMode);
        this.thinkingChannelStart = emptyToNull(thinkingChannelStart);
        this.thinkingChannelEnd = emptyToNull(thinkingChannelEnd);
    }

    @NonNull
    public static TaiModelProfile forModel(@NonNull TaiModelSpec modelSpec) {
        String id = normalizedIdentity(modelSpec.id);
        String path = modelSpec.localPath == null ? "" : modelSpec.localPath.toLowerCase(Locale.ROOT);
        // This provider package has fixed, published runtime behavior. Override the legacy generic
        // CPU-only profile that older importer versions persisted so existing installations heal
        // without requiring the user to delete and re-import a multi-gigabyte model.
        if (TaiModelSpec.BACKEND_LITERT_LM.equals(modelSpec.backend)
            && isQwen3Thinking2507(id, path)
            && (modelSpec.runtimeProfile == null || isLegacyCpuOnlyImportProfile(modelSpec.runtimeProfile))) {
            return qwen3Thinking2507Profile();
        }
        if (modelSpec.runtimeProfile != null) return modelSpec.runtimeProfile;

        if (TaiModelSpec.BACKEND_MNN_LLM.equals(modelSpec.backend)) {
            return new TaiModelProfile(Collections.singletonList("cpu"), 1024, 40, 0.90d, 0.80d,
                modelSpec.recommendedRamGb > 0 ? modelSpec.recommendedRamGb : null, "tai-mnn-config-default");
        }
        // Gallery's maxTokens (AL:19,65) is EngineConfig.maxNumTokens, the total KV-cache budget.
        // Its 32000 maxContextLength slider (AL:18-19,64-65) is an app ceiling, not the model's:
        // the window stays on TAI's RAM tiers up to the catalog's 32768, capped on GPU by the
        // budget. 4000 stays the default output cap (doc: "the effect is harmless" for Gemma 4).
        if ("gemma4e2bit".equals(id) || "gemma4e2bitlitertlm".equals(id) || path.contains("gemma-4-e2b-it.litertlm")) {
            return edgeGalleryThinkingProfile(Arrays.asList("gpu", "cpu"), 4000, 1.0d, 8);
        }
        if ("gemma4e4bit".equals(id) || "gemma4e4bitlitertlm".equals(id) || path.contains("gemma-4-e4b-it.litertlm")) {
            return edgeGalleryThinkingProfile(Arrays.asList("gpu", "cpu"), 4000, 1.0d, 12);
        }
        if (normalizedIdentity(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M).equals(id)
            || path.contains("mobile_actions_q8_ekv1024")) {
            return edgeGalleryProfile(Collections.singletonList("cpu"), 1024, 0.0d, 6);
        }
        // Gallery gives DeepSeek/Qwen no separate maxContextLength (AL:169-185): the allowlist's
        // 4096 is both the total window and the value Gallery would otherwise use as an output
        // cap. TAI keeps 4096 as the context window but trims the output cap so a full prompt
        // still fits (doc recommendation 2: "an output cap of 4096 inside a 4096 window leaves
        // no room for the prompt").
        if ("deepseekr1distillqwen15blitertlm".equals(id)
            || path.contains("deepseek-r1-distill-qwen-1.5b_multi-prefill-seq_q8_ekv4096.litertlm")) {
            return edgeGalleryProfile(Arrays.asList("gpu", "cpu"), sensibleOutputCap(4096), 1.0d, 6, 4096);
        }
        if ("qwen2515binstructlitertlm".equals(id)
            || path.contains("qwen2.5-1.5b-instruct_multi-prefill-seq_q8_ekv4096.litertlm")) {
            return new TaiModelProfile(Arrays.asList("gpu", "cpu"), sensibleOutputCap(4096), 20, 0.80d, 0.70d, 6,
                SOURCE_EDGE_GALLERY_1_0_15, THINKING_NONE, null, null, 4096);
        }
        if ("tinygarden270m".equals(id) || path.contains("tiny_garden_q8_ekv1024")) {
            return edgeGalleryProfile(Collections.singletonList("cpu"), 1024, 0.0d, 6);
        }
        // Gemma 3n: Gallery defaults it CPU-first, unlike Gemma 4's GPU-first order
        // (AL:110,130 vs AL:20,66), with vision GPU regardless of the main backend ("must be
        // GPU for Gemma 3n", G:ui/llmchat/LlmChatModelHelper.kt:136). No separate
        // maxContextLength is given, so 4096 is both the window and (trimmed) the output cap.
        if (id.contains("gemma3ne2bit") || path.contains("gemma-3n-e2b-it")) {
            return edgeGalleryProfile(Arrays.asList("cpu", "gpu"), sensibleOutputCap(4096), 1.0d, 8, 4096);
        }
        if (id.contains("gemma3ne4bit") || path.contains("gemma-3n-e4b-it")) {
            return edgeGalleryProfile(Arrays.asList("cpu", "gpu"), sensibleOutputCap(4096), 1.0d, 12, 4096);
        }
        // Gemma3-1B-IT (AL:134-151): GPU-first, no vision/audio, fixed 1024 with no
        // maxContextLength slider — the same "no separate ceiling" shape as MobileActions/
        // TinyGarden above, so context and output cap both stay 1024.
        if (id.contains("gemma31bit") || path.contains("gemma3-1b-it")) {
            return edgeGalleryProfile(Arrays.asList("gpu", "cpu"), 1024, 1.0d, 6, 1024);
        }

        // litert-community files that carry an `_ekvNNNN` token (e.g. `..._ekv4096.litertlm`)
        // use that number as Gallery's own maxTokens for every allowlisted file that has it
        // (AL: `_ekv4096` -> 4096, `_ekv1024` -> 1024). Treat it as a default context window for
        // an otherwise-unmatched import, not a hard limit: the user's own runtimeProfile (checked
        // above) still overrides it (doc recommendation 3).
        Integer ekvContext = extractEkvContext(path);
        if (ekvContext != null) {
            List<String> ekvAccelerators = modelSpec.builtInCatalogEntry
                ? Arrays.asList("gpu", "cpu")
                : Collections.singletonList("cpu");
            return new TaiModelProfile(ekvAccelerators, sensibleOutputCap(ekvContext), 64, 0.95d, 1.0d, null,
                modelSpec.builtInCatalogEntry ? "tai-catalog-default" : "edge-gallery-import-default",
                THINKING_NONE, null, null, ekvContext);
        }

        // No filename match and no `ekv` token: Gallery's own import default is exactly this,
        // CPU-only with a 1024-token window (G:ui/modelmanager/ModelImportDialog.kt:97-103,
        // G:data/Consts.kt:43-46).
        List<String> accelerators = modelSpec.builtInCatalogEntry
            ? Arrays.asList("gpu", "cpu")
            : Collections.singletonList("cpu");
        return new TaiModelProfile(accelerators, 1024, 64, 0.95d, 1.0d, null,
            modelSpec.builtInCatalogEntry ? "tai-catalog-default" : "edge-gallery-import-default");
    }

    /** {@code min(1024, context/4)}: a default output cap that always leaves room for a prompt. */
    private static int sensibleOutputCap(int contextTokens) {
        return Math.max(1, Math.min(1024, contextTokens / 4));
    }

    private static final Pattern EKV_TOKEN = Pattern.compile("ekv(\\d+)");

    /** The `ekvNNNN` token from a litert-community file name, or {@code null} when absent. */
    @Nullable
    static Integer extractEkvContext(@Nullable String path) {
        if (path == null) return null;
        Matcher matcher = EKV_TOKEN.matcher(path);
        if (!matcher.find()) return null;
        try {
            int value = Integer.parseInt(matcher.group(1));
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    public static TaiModelProfile fromRequest(@NonNull JSONObject request, @NonNull TaiModelProfile fallback) {
        JSONObject profile = request.optJSONObject("runtimeProfile");
        if (profile == null) profile = request;
        List<String> accelerators = acceleratorsFromJson(profile.opt("compatibleAccelerators"));
        if (accelerators.isEmpty()) accelerators = fallback.compatibleAccelerators;
        Integer minMemory = profile.has("minDeviceMemoryInGb") && !profile.isNull("minDeviceMemoryInGb")
            ? Integer.valueOf(profile.optInt("minDeviceMemoryInGb")) : fallback.minDeviceMemoryInGb;
        return new TaiModelProfile(
            accelerators,
            positiveInt(profile, "defaultMaxTokens", fallback.defaultMaxTokens),
            positiveInt(profile, "defaultTopK", fallback.defaultTopK),
            profile.has("defaultTopP") ? profile.optDouble("defaultTopP", fallback.defaultTopP) : fallback.defaultTopP,
            profile.has("defaultTemperature") ? profile.optDouble("defaultTemperature", fallback.defaultTemperature) : fallback.defaultTemperature,
            minMemory,
            profile.optString("source", fallback.source),
            profile.optString("thinkingMode", fallback.thinkingMode),
            nullableString(profile, "thinkingChannelStart", fallback.thinkingChannelStart),
            nullableString(profile, "thinkingChannelEnd", fallback.thinkingChannelEnd),
            profile.optInt("maxContextTokens", fallback.maxContextTokens)
        );
    }

    @NonNull
    public static TaiModelProfile fromJson(@NonNull JSONObject json) {
        return new TaiModelProfile(
            acceleratorsFromJson(json.opt("compatibleAccelerators")),
            positiveInt(json, "defaultMaxTokens", 1024),
            positiveInt(json, "defaultTopK", 64),
            json.optDouble("defaultTopP", 0.95d),
            json.optDouble("defaultTemperature", 1.0d),
            json.has("minDeviceMemoryInGb") && !json.isNull("minDeviceMemoryInGb") ? Integer.valueOf(json.optInt("minDeviceMemoryInGb")) : null,
            json.optString("source", "persisted"),
            json.optString("thinkingMode", THINKING_NONE),
            nullableString(json, "thinkingChannelStart", null),
            nullableString(json, "thinkingChannelEnd", null),
            json.optInt("maxContextTokens", 0)
        );
    }

    public boolean supports(@NonNull String accelerator) {
        String normalized = normalizeAccelerator(accelerator);
        return normalized != null && compatibleAccelerators.contains(normalized);
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        JSONArray accelerators = new JSONArray();
        for (String accelerator : compatibleAccelerators) accelerators.put(accelerator);
        json.put("compatibleAccelerators", accelerators);
        json.put("defaultMaxTokens", defaultMaxTokens);
        json.put("defaultTopK", defaultTopK);
        json.put("defaultTopP", defaultTopP);
        json.put("defaultTemperature", defaultTemperature);
        json.put("minDeviceMemoryInGb", minDeviceMemoryInGb == null ? JSONObject.NULL : minDeviceMemoryInGb);
        json.put("source", source);
        json.put("thinkingMode", thinkingMode);
        json.put("maxContextTokens", maxContextTokens);
        json.put("thinkingChannelStart", thinkingChannelStart == null ? JSONObject.NULL : thinkingChannelStart);
        json.put("thinkingChannelEnd", thinkingChannelEnd == null ? JSONObject.NULL : thinkingChannelEnd);
        return json;
    }

    @NonNull
    private static TaiModelProfile edgeGalleryProfile(List<String> accelerators, int maxTokens, double temperature, int minMemoryGb) {
        return new TaiModelProfile(accelerators, maxTokens, 64, 0.95d, temperature, minMemoryGb,
            SOURCE_EDGE_GALLERY_1_0_15);
    }

    @NonNull
    private static TaiModelProfile edgeGalleryProfile(List<String> accelerators, int maxTokens, double temperature,
            int minMemoryGb, int maxContextTokens) {
        return new TaiModelProfile(accelerators, maxTokens, 64, 0.95d, temperature, minMemoryGb,
            SOURCE_EDGE_GALLERY_1_0_15, THINKING_NONE, null, null, maxContextTokens);
    }

    @NonNull
    private static TaiModelProfile edgeGalleryThinkingProfile(List<String> accelerators, int maxTokens, double temperature, int minMemoryGb) {
        return new TaiModelProfile(accelerators, maxTokens, 64, 0.95d, temperature, minMemoryGb,
            SOURCE_EDGE_GALLERY_1_0_15, THINKING_TOGGLEABLE, null, null);
    }

    @NonNull
    private static TaiModelProfile edgeGalleryThinkingProfile(List<String> accelerators, int maxTokens, double temperature,
            int minMemoryGb, int maxContextTokens) {
        return new TaiModelProfile(accelerators, maxTokens, 64, 0.95d, temperature, minMemoryGb,
            SOURCE_EDGE_GALLERY_1_0_15, THINKING_TOGGLEABLE, null, null, maxContextTokens);
    }

    @NonNull
    static TaiModelProfile qwen3Thinking2507Profile() {
        return new TaiModelProfile(Arrays.asList("gpu", "cpu"), 2048, 64, 0.95d, 1.0d,
            3, SOURCE_LITERT_COMMUNITY, THINKING_ALWAYS, "<think>", "</think>");
    }

    static boolean isQwen3Thinking2507(@Nullable String id, @Nullable String path) {
        return normalizedIdentity(id).contains("qwen34bthinking2507")
            || normalizedIdentity(path).contains("qwen34bthinking2507");
    }

    static boolean isLegacyCpuOnlyImportProfile(@Nullable TaiModelProfile profile) {
        return profile != null
            && "edge-gallery-import-default".equals(profile.source)
            && profile.compatibleAccelerators.size() == 1
            && profile.compatibleAccelerators.contains("cpu");
    }

    @NonNull
    private static String normalizeThinkingMode(@Nullable String value) {
        if (THINKING_ALWAYS.equals(value) || THINKING_TOGGLEABLE.equals(value)) return value;
        return THINKING_NONE;
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    @Nullable
    private static String nullableString(@NonNull JSONObject json, @NonNull String key, @Nullable String fallback) {
        if (!json.has(key)) return fallback;
        if (json.isNull(key)) return null;
        return emptyToNull(json.optString(key, fallback));
    }

    @NonNull
    private static List<String> acceleratorsFromJson(@Nullable Object value) {
        ArrayList<String> accelerators = new ArrayList<>();
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String accelerator = normalizeAccelerator(array.optString(i, ""));
                if (accelerator != null && !accelerators.contains(accelerator)) accelerators.add(accelerator);
            }
        } else if (value instanceof String) {
            for (String item : ((String) value).split(",")) {
                String accelerator = normalizeAccelerator(item);
                if (accelerator != null && !accelerators.contains(accelerator)) accelerators.add(accelerator);
            }
        }
        return accelerators;
    }

    private static int positiveInt(@NonNull JSONObject json, @NonNull String key, int fallback) {
        int value = json.optInt(key, fallback);
        return value > 0 ? value : fallback;
    }

    @Nullable
    private static String normalizeAccelerator(@Nullable String accelerator) {
        if (accelerator == null) return null;
        String value = accelerator.trim().toLowerCase(Locale.ROOT);
        if ("cpu".equals(value) || "gpu".equals(value)) return value;
        return null;
    }

    @NonNull
    private static String normalizedIdentity(@Nullable String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
