package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public final class TaiRuntimeState {
    public final boolean loaded;
    @Nullable public final String loadedModelId;
    @NonNull public final String runtimeName;
    @NonNull public final String state;
    @NonNull public final String status;
    @NonNull public final String backend;
    @Nullable public final String backendFallbackReason;
    @Nullable public final String loadedModelPath;
    public final boolean activeGeneration;
    @Nullable public final String activeGenerationId;
    public final long activeGenerationStartedAtMs;
    public final long keepWarmUntilMs;
    public final long idleUnloadAtMs;
    public final long loadedAtMs;
    public final long lastUsedAtMs;
    @Nullable private final JSONObject extraJson;

    public TaiRuntimeState(boolean loaded, @Nullable String loadedModelId, @NonNull String runtimeName, @NonNull String status) {
        this(
            loaded,
            loadedModelId,
            runtimeName,
            loaded ? "loaded" : "unloaded",
            status,
            "none",
            null,
            null,
            false,
            null,
            0L,
            0L,
            0L,
            0L,
            0L,
            null
        );
    }

    public TaiRuntimeState(
        boolean loaded,
        @Nullable String loadedModelId,
        @NonNull String runtimeName,
        @NonNull String state,
        @NonNull String status,
        @NonNull String backend,
        @Nullable String backendFallbackReason,
        @Nullable String loadedModelPath,
        boolean activeGeneration,
        @Nullable String activeGenerationId,
        long activeGenerationStartedAtMs,
        long keepWarmUntilMs,
        long idleUnloadAtMs,
        long loadedAtMs,
        long lastUsedAtMs
    ) {
        this(
            loaded,
            loadedModelId,
            runtimeName,
            state,
            status,
            backend,
            backendFallbackReason,
            loadedModelPath,
            activeGeneration,
            activeGenerationId,
            activeGenerationStartedAtMs,
            keepWarmUntilMs,
            idleUnloadAtMs,
            loadedAtMs,
            lastUsedAtMs,
            null
        );
    }

    public TaiRuntimeState(
        boolean loaded,
        @Nullable String loadedModelId,
        @NonNull String runtimeName,
        @NonNull String state,
        @NonNull String status,
        @NonNull String backend,
        @Nullable String backendFallbackReason,
        @Nullable String loadedModelPath,
        boolean activeGeneration,
        @Nullable String activeGenerationId,
        long activeGenerationStartedAtMs,
        long keepWarmUntilMs,
        long idleUnloadAtMs,
        long loadedAtMs,
        long lastUsedAtMs,
        @Nullable JSONObject extraJson
    ) {
        this.loaded = loaded;
        this.loadedModelId = loadedModelId;
        this.runtimeName = runtimeName;
        this.state = state;
        this.status = status;
        this.backend = backend;
        this.backendFallbackReason = backendFallbackReason;
        this.loadedModelPath = loadedModelPath;
        this.activeGeneration = activeGeneration;
        this.activeGenerationId = activeGenerationId;
        this.activeGenerationStartedAtMs = activeGenerationStartedAtMs;
        this.keepWarmUntilMs = keepWarmUntilMs;
        this.idleUnloadAtMs = idleUnloadAtMs;
        this.loadedAtMs = loadedAtMs;
        this.lastUsedAtMs = lastUsedAtMs;
        this.extraJson = extraJson;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        long now = System.currentTimeMillis();
        JSONObject json = new JSONObject();
        json.put("loaded", loaded);
        json.put("loadedModelId", loadedModelId == null ? JSONObject.NULL : loadedModelId);
        json.put("runtimeName", runtimeName);
        json.put("state", state);
        json.put("status", status);
        json.put("backend", backend);
        json.put("backendFallbackReason", backendFallbackReason == null || backendFallbackReason.isEmpty() ? JSONObject.NULL : backendFallbackReason);
        json.put("loadedModelPath", loadedModelPath == null ? JSONObject.NULL : loadedModelPath);
        json.put("activeGeneration", activeGeneration);
        json.put("activeGenerationId", activeGenerationId == null ? JSONObject.NULL : activeGenerationId);
        json.put("activeGenerationStartedAtMs", activeGenerationStartedAtMs);
        json.put("keepWarmUntilMs", keepWarmUntilMs);
        json.put("keepWarmRemainingMs", keepWarmUntilMs > now ? keepWarmUntilMs - now : 0L);
        json.put("idleUnloadAtMs", idleUnloadAtMs);
        json.put("idleUnloadRemainingMs", idleUnloadAtMs > now ? idleUnloadAtMs - now : 0L);
        json.put("loadedAtMs", loadedAtMs);
        json.put("lastUsedAtMs", lastUsedAtMs);
        if (extraJson != null) {
            Iterator<String> keys = extraJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                json.put(key, extraJson.opt(key));
            }
        }
        return json;
    }

    @NonNull
    public static TaiRuntimeState fromJson(@Nullable JSONObject json) {
        if (json == null) {
            return new TaiRuntimeState(false, null, "tai-runtime-service", "Runtime status unavailable.");
        }
        return new TaiRuntimeState(
            json.optBoolean("loaded", false),
            json.isNull("loadedModelId") ? null : json.optString("loadedModelId", null),
            json.optString("runtimeName", "tai-runtime-service"),
            json.optString("state", json.optBoolean("loaded", false) ? "loaded" : "unloaded"),
            json.optString("status", ""),
            json.optString("backend", "none"),
            json.isNull("backendFallbackReason") ? null : json.optString("backendFallbackReason", null),
            json.isNull("loadedModelPath") ? null : json.optString("loadedModelPath", null),
            json.optBoolean("activeGeneration", false),
            json.isNull("activeGenerationId") ? null : json.optString("activeGenerationId", null),
            json.optLong("activeGenerationStartedAtMs", 0L),
            json.optLong("keepWarmUntilMs", 0L),
            json.optLong("idleUnloadAtMs", 0L),
            json.optLong("loadedAtMs", 0L),
            json.optLong("lastUsedAtMs", 0L),
            json
        );
    }
}
