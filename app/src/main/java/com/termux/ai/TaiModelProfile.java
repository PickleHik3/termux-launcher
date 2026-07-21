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

public final class TaiModelProfile {
    public static final String SOURCE_EDGE_GALLERY_1_0_15 = "google-ai-edge-gallery-1.0.15";
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
        if ("deepseekr1distillqwen15blitertlm".equals(id)
            || path.contains("deepseek-r1-distill-qwen-1.5b_multi-prefill-seq_q8_ekv4096.litertlm")) {
            return edgeGalleryProfile(Arrays.asList("gpu", "cpu"), 4096, 1.0d, 6);
        }
        if ("qwen2515binstructlitertlm".equals(id)
            || path.contains("qwen2.5-1.5b-instruct_multi-prefill-seq_q8_ekv4096.litertlm")) {
            return new TaiModelProfile(Arrays.asList("gpu", "cpu"), 4096, 20, 0.80d, 0.70d, 6,
                SOURCE_EDGE_GALLERY_1_0_15);
        }
        if ("tinygarden270m".equals(id) || path.contains("tiny_garden_q8_ekv1024")) {
            return edgeGalleryProfile(Collections.singletonList("cpu"), 1024, 0.0d, 6);
        }

        List<String> accelerators = modelSpec.builtInCatalogEntry
            ? Arrays.asList("gpu", "cpu")
            : Collections.singletonList("cpu");
        return new TaiModelProfile(accelerators, 1024, 64, 0.95d, 1.0d, null,
            modelSpec.builtInCatalogEntry ? "tai-catalog-default" : "edge-gallery-import-default");
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
            nullableString(profile, "thinkingChannelEnd", fallback.thinkingChannelEnd)
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
            nullableString(json, "thinkingChannelEnd", null)
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
    private static TaiModelProfile edgeGalleryThinkingProfile(List<String> accelerators, int maxTokens, double temperature, int minMemoryGb) {
        return new TaiModelProfile(accelerators, maxTokens, 64, 0.95d, temperature, minMemoryGb,
            SOURCE_EDGE_GALLERY_1_0_15, THINKING_TOGGLEABLE, null, null);
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
