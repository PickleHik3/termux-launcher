package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public final class TaiRuntimeOptions {
    @Nullable public final Integer maxTokens;
    @Nullable public final Integer topK;
    @Nullable public final Double topP;
    @Nullable public final Double temperature;
    @Nullable public final String accelerator;
    @Nullable public final Integer contextWindow;
    @Nullable public final Integer threadCount;
    @Nullable public final String precision;
    @Nullable public final String memoryMode;
    @Nullable public final Boolean thinkingEnabled;
    @Nullable public final Boolean speculativeDecodingEnabled;
    @Nullable public final Integer idleUnloadMinutes;

    public TaiRuntimeOptions(
        @Nullable Integer maxTokens,
        @Nullable Integer topK,
        @Nullable Double topP,
        @Nullable Double temperature,
        @Nullable String accelerator,
        @Nullable Boolean thinkingEnabled,
        @Nullable Boolean speculativeDecodingEnabled,
        @Nullable Integer idleUnloadMinutes
    ) {
        this(maxTokens, topK, topP, temperature, accelerator, null,
            null, null, null, thinkingEnabled, speculativeDecodingEnabled, idleUnloadMinutes);
    }

    public TaiRuntimeOptions(
        @Nullable Integer maxTokens,
        @Nullable Integer topK,
        @Nullable Double topP,
        @Nullable Double temperature,
        @Nullable String accelerator,
        @Nullable Integer contextWindow,
        @Nullable Boolean thinkingEnabled,
        @Nullable Boolean speculativeDecodingEnabled,
        @Nullable Integer idleUnloadMinutes
    ) {
        this(maxTokens, topK, topP, temperature, accelerator, contextWindow,
            null, null, null, thinkingEnabled, speculativeDecodingEnabled, idleUnloadMinutes);
    }

    public TaiRuntimeOptions(
        @Nullable Integer maxTokens,
        @Nullable Integer topK,
        @Nullable Double topP,
        @Nullable Double temperature,
        @Nullable String accelerator,
        @Nullable Integer contextWindow,
        @Nullable Integer threadCount,
        @Nullable String precision,
        @Nullable String memoryMode,
        @Nullable Boolean thinkingEnabled,
        @Nullable Boolean speculativeDecodingEnabled,
        @Nullable Integer idleUnloadMinutes
    ) {
        this.maxTokens = maxTokens;
        this.topK = topK;
        this.topP = topP;
        this.temperature = temperature;
        this.accelerator = accelerator;
        this.contextWindow = contextWindow;
        this.threadCount = threadCount;
        this.precision = precision;
        this.memoryMode = memoryMode;
        this.thinkingEnabled = thinkingEnabled;
        this.speculativeDecodingEnabled = speculativeDecodingEnabled;
        this.idleUnloadMinutes = idleUnloadMinutes;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        putNullable(json, "maxTokens", maxTokens);
        putNullable(json, "topK", topK);
        putNullable(json, "topP", topP);
        putNullable(json, "temperature", temperature);
        putNullable(json, "accelerator", accelerator);
        putNullable(json, "contextWindow", contextWindow);
        putNullable(json, "threadCount", threadCount);
        putNullable(json, "precision", precision);
        putNullable(json, "memoryMode", memoryMode);
        putNullable(json, "thinkingEnabled", thinkingEnabled);
        putNullable(json, "speculativeDecodingEnabled", speculativeDecodingEnabled);
        putNullable(json, "idleUnloadMinutes", idleUnloadMinutes);
        json.put("usesGalleryGenerationDefaultsForNulls", true);
        return json;
    }

    @NonNull
    public static TaiRuntimeOptions fromJson(@NonNull JSONObject json) {
        return new TaiRuntimeOptions(
            nullableInteger(json, "maxTokens"),
            nullableInteger(json, "topK"),
            nullableDouble(json, "topP"),
            nullableDouble(json, "temperature"),
            nullableString(json, "accelerator"),
            nullableInteger(json, "contextWindow"),
            nullableInteger(json, "threadCount"),
            nullableString(json, "precision"),
            nullableString(json, "memoryMode"),
            nullableBoolean(json, "thinkingEnabled"),
            nullableBoolean(json, "speculativeDecodingEnabled"),
            nullableInteger(json, "idleUnloadMinutes")
        );
    }

    @NonNull
    public TaiRuntimeOptions withAccelerator(@Nullable String overrideAccelerator) {
        return new TaiRuntimeOptions(
            maxTokens,
            topK,
            topP,
            temperature,
            overrideAccelerator,
            contextWindow,
            threadCount,
            precision,
            memoryMode,
            thinkingEnabled,
            speculativeDecodingEnabled,
            idleUnloadMinutes
        );
    }

    @NonNull
    public TaiRuntimeOptions withGenerationOverrides(
        @Nullable Integer overrideMaxTokens,
        @Nullable Integer overrideTopK,
        @Nullable Double overrideTopP,
        @Nullable Double overrideTemperature,
        @Nullable String overrideAccelerator,
        @Nullable Integer overrideContextWindow,
        @Nullable Integer overrideThreadCount,
        @Nullable String overridePrecision,
        @Nullable String overrideMemoryMode,
        @Nullable Boolean overrideThinkingEnabled,
        @Nullable Boolean overrideSpeculativeDecodingEnabled
    ) {
        return new TaiRuntimeOptions(
            overrideMaxTokens != null ? overrideMaxTokens : maxTokens,
            overrideTopK != null ? overrideTopK : topK,
            overrideTopP != null ? overrideTopP : topP,
            overrideTemperature != null ? overrideTemperature : temperature,
            overrideAccelerator != null ? overrideAccelerator : accelerator,
            overrideContextWindow != null ? overrideContextWindow : contextWindow,
            overrideThreadCount != null ? overrideThreadCount : threadCount,
            overridePrecision != null ? overridePrecision : precision,
            overrideMemoryMode != null ? overrideMemoryMode : memoryMode,
            overrideThinkingEnabled != null ? overrideThinkingEnabled : thinkingEnabled,
            overrideSpeculativeDecodingEnabled != null ? overrideSpeculativeDecodingEnabled : speculativeDecodingEnabled,
            idleUnloadMinutes
        );
    }

    @NonNull
    public TaiRuntimeOptions withGenerationOverrides(
        @Nullable Integer overrideMaxTokens,
        @Nullable Integer overrideTopK,
        @Nullable Double overrideTopP,
        @Nullable Double overrideTemperature,
        @Nullable String overrideAccelerator,
        @Nullable Boolean overrideThinkingEnabled,
        @Nullable Boolean overrideSpeculativeDecodingEnabled
    ) {
        return withGenerationOverrides(overrideMaxTokens, overrideTopK, overrideTopP, overrideTemperature,
            overrideAccelerator, null, null, null, null, overrideThinkingEnabled, overrideSpeculativeDecodingEnabled);
    }

    private static void putNullable(JSONObject json, String key, Object value) throws JSONException {
        json.put(key, value == null ? JSONObject.NULL : value);
    }

    @Nullable
    private static Integer nullableInteger(@NonNull JSONObject json, @NonNull String key) {
        return json.has(key) && !json.isNull(key) ? json.optInt(key) : null;
    }

    @Nullable
    private static Double nullableDouble(@NonNull JSONObject json, @NonNull String key) {
        return json.has(key) && !json.isNull(key) ? json.optDouble(key) : null;
    }

    @Nullable
    private static Boolean nullableBoolean(@NonNull JSONObject json, @NonNull String key) {
        return json.has(key) && !json.isNull(key) ? json.optBoolean(key) : null;
    }

    @Nullable
    private static String nullableString(@NonNull JSONObject json, @NonNull String key) {
        if (!json.has(key) || json.isNull(key)) return null;
        String value = json.optString(key, "").trim();
        return value.isEmpty() ? null : value;
    }
}
