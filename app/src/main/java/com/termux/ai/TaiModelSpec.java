package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ai.edge.litertlm.Capabilities;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class TaiModelSpec {
    public static final String BACKEND_LITERT_LM = "litert-lm";
    public static final String BACKEND_MITERT_LM = BACKEND_LITERT_LM;
    public static final String BACKEND_MNN_LLM = "mnn-llm";
    public static final String FORMAT_LITERTLM = "litertlm";
    public static final String FORMAT_MNN = "mnn";
    public static final String FORMAT_GGUF = "gguf";
    public static final String CAPABILITY_TEXT_CHAT = "text_chat";
    public static final String CAPABILITY_TEXT_EMBEDDINGS = "text_embeddings";
    public static final String CAPABILITY_IMAGE_INPUT = "image_input";
    public static final String CAPABILITY_AUDIO_INPUT = "audio_input";
    // Declared-only intent: no runtime processes video yet, so this rides on sourceCapabilities
    // (informational) and is deliberately kept out of endpointCapabilitiesFor.
    public static final String CAPABILITY_VIDEO_INPUT = "video_input";
    public static final String CAPABILITY_TOOL_USE = "tool_use";
    public static final String CAPABILITY_MOBILE_ACTIONS = "mobile_actions";
    public static final String CAPABILITY_CODE = "code";
    public static final String CAPABILITY_SPECULATIVE_DECODING = "speculative_decoding";
    public static final String CAPABILITY_LLM_THINKING = "llm_thinking";
    public static final String TOOL_MODE_NATIVE = "native";
    public static final String TOOL_MODE_PROMPT_FALLBACK = "prompt_fallback";

    public final String id;
    public final String displayName;
    public final String roleHint;
    public final String source;
    @Nullable public final String localPath;
    public final String license;
    public final long sizeBytes;
    public final Set<String> capabilities;
    public final Set<String> endpointCapabilities;
    public final Set<String> sourceCapabilities;
    public final boolean builtInCatalogEntry;
    @Nullable public final TaiModelProfile runtimeProfile;
    @NonNull public final String backend;
    @NonNull public final String format;
    @Nullable public final String architecture;
    @Nullable public final String quantization;
    public final int contextWindow;
    public final int endpointContextWindow;
    public final int sourceContextWindow;
    public final int defaultMaxOutputTokens;
    public final int recommendedRamGb;
    @Nullable public final String sha256;
    @Nullable public final String toolMode;

    public TaiModelSpec(
        @NonNull String id,
        @NonNull String displayName,
        @NonNull String roleHint,
        @NonNull String source,
        @Nullable String localPath,
        @NonNull String license,
        long sizeBytes,
        @NonNull Set<String> capabilities
    ) {
        this(id, displayName, roleHint, source, localPath, license, sizeBytes, capabilities, true);
    }

    public TaiModelSpec(
        @NonNull String id,
        @NonNull String displayName,
        @NonNull String roleHint,
        @NonNull String source,
        @Nullable String localPath,
        @NonNull String license,
        long sizeBytes,
        @NonNull Set<String> capabilities,
        boolean builtInCatalogEntry
    ) {
        this(id, displayName, roleHint, source, localPath, license, sizeBytes, capabilities,
            builtInCatalogEntry, null);
    }

    public TaiModelSpec(
        @NonNull String id,
        @NonNull String displayName,
        @NonNull String roleHint,
        @NonNull String source,
        @Nullable String localPath,
        @NonNull String license,
        long sizeBytes,
        @NonNull Set<String> capabilities,
        boolean builtInCatalogEntry,
        @Nullable TaiModelProfile runtimeProfile
    ) {
        this(id, displayName, roleHint, source, localPath, license, sizeBytes, capabilities,
            builtInCatalogEntry, runtimeProfile, inferBackend(localPath), inferFormat(localPath),
            null, null, 4096, 0, null);
    }

    public TaiModelSpec(
        @NonNull String id,
        @NonNull String displayName,
        @NonNull String roleHint,
        @NonNull String source,
        @Nullable String localPath,
        @NonNull String license,
        long sizeBytes,
        @NonNull Set<String> capabilities,
        boolean builtInCatalogEntry,
        @Nullable TaiModelProfile runtimeProfile,
        @NonNull String backend,
        @NonNull String format,
        @Nullable String architecture,
        @Nullable String quantization,
        int contextWindow,
        int recommendedRamGb,
        @Nullable String sha256
    ) {
        this(id, displayName, roleHint, source, localPath, license, sizeBytes, capabilities,
            builtInCatalogEntry, runtimeProfile, backend, format, architecture, quantization,
            contextWindow, contextWindow, defaultMaxOutputTokensFor(id, backend),
            recommendedRamGb, sha256, null, null);
    }

    public TaiModelSpec(
        @NonNull String id,
        @NonNull String displayName,
        @NonNull String roleHint,
        @NonNull String source,
        @Nullable String localPath,
        @NonNull String license,
        long sizeBytes,
        @NonNull Set<String> sourceCapabilities,
        boolean builtInCatalogEntry,
        @Nullable TaiModelProfile runtimeProfile,
        @NonNull String backend,
        @NonNull String format,
        @Nullable String architecture,
        @Nullable String quantization,
        int endpointContextWindow,
        int sourceContextWindow,
        int defaultMaxOutputTokens,
        int recommendedRamGb,
        @Nullable String sha256,
        @Nullable Set<String> endpointCapabilities,
        @Nullable String toolMode
    ) {
        this.id = id;
        this.displayName = displayName;
        this.roleHint = roleHint;
        this.source = source;
        this.localPath = localPath;
        this.license = license;
        this.sizeBytes = sizeBytes;
        this.builtInCatalogEntry = builtInCatalogEntry;
        this.runtimeProfile = runtimeProfile;
        this.backend = requireSupportedBackend(backend);
        this.format = requireSupportedFormat(format);
        if (!isSupportedBackendFormat(this.backend, this.format)) {
            throw new IllegalArgumentException("unsupported_backend_format");
        }
        this.architecture = architecture;
        this.quantization = quantization;
        LinkedHashSet<String> sourceCaps = normalizedCapabilities(sourceCapabilities);
        if (sourceCaps.isEmpty()) sourceCaps.add(CAPABILITY_TEXT_CHAT);
        LinkedHashSet<String> supportedEndpointCaps = endpointCapabilitiesFor(
            id, this.backend, this.format, sourceCaps, localPath);
        LinkedHashSet<String> endpointCaps = endpointCapabilities == null
            ? supportedEndpointCaps
            : normalizedCapabilities(endpointCapabilities);
        // Explicit endpoint metadata may narrow a model (for example a -vision variant), but it
        // may never widen what this backend and package shape can actually execute.
        endpointCaps.retainAll(supportedEndpointCaps);
        this.sourceCapabilities = Collections.unmodifiableSet(sourceCaps);
        this.endpointCapabilities = Collections.unmodifiableSet(endpointCaps);
        this.capabilities = this.endpointCapabilities;
        this.endpointContextWindow = endpointContextWindow > 0 ? endpointContextWindow : defaultEndpointContextWindowFor(id, this.backend);
        this.sourceContextWindow = sourceContextWindow > 0 ? sourceContextWindow : this.endpointContextWindow;
        this.contextWindow = this.endpointContextWindow;
        this.defaultMaxOutputTokens = defaultMaxOutputTokens > 0 ? defaultMaxOutputTokens : defaultMaxOutputTokensFor(id, this.backend);
        this.recommendedRamGb = Math.max(0, recommendedRamGb);
        this.sha256 = sha256;
        this.toolMode = normalizedToolMode(toolMode, this.backend, endpointCaps);
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("displayName", displayName);
        json.put("roleHint", roleHint);
        json.put("source", source);
        json.put("localPath", localPath == null ? JSONObject.NULL : localPath);
        json.put("license", license);
        json.put("sizeBytes", sizeBytes);
        json.put("builtInCatalogEntry", builtInCatalogEntry);
        json.put("runtimeProfile", TaiModelProfile.forModel(this).toJson());
        json.put("backend", backend);
        json.put("format", format);
        json.put("architecture", architecture == null ? JSONObject.NULL : architecture);
        json.put("quantization", quantization == null ? JSONObject.NULL : quantization);
        json.put("contextWindow", contextWindow);
        json.put("endpointContextWindow", endpointContextWindow);
        json.put("sourceContextWindow", sourceContextWindow);
        json.put("defaultMaxOutputTokens", defaultMaxOutputTokens);
        json.put("recommendedRamGb", recommendedRamGb);
        json.put("sha256", sha256 == null ? JSONObject.NULL : sha256);
        json.put("capabilities", capabilityArray(endpointCapabilities));
        json.put("endpointCapabilities", capabilityArray(endpointCapabilities));
        json.put("sourceCapabilities", capabilityArray(sourceCapabilities));
        json.put("toolMode", toolMode == null ? JSONObject.NULL : toolMode);
        json.put("capabilitiesVerified", builtInCatalogEntry);
        json.put("capabilitySource", builtInCatalogEntry ? "catalog" : "import_or_user_metadata");
        return json;
    }

    @NonNull
    public static TaiModelSpec fromJson(@NonNull JSONObject json) {
        String localPath = json.isNull("localPath") ? null : json.optString("localPath", null);
        String backend = json.optString("backend", inferBackend(localPath));
        String format = json.optString("format", inferFormat(localPath));
        LinkedHashSet<String> legacyCapabilities = capabilitiesFromJson(json.optJSONArray("capabilities"));
        LinkedHashSet<String> sourceCapabilities = capabilitiesFromJson(json.optJSONArray("sourceCapabilities"));
        LinkedHashSet<String> endpointCapabilities = capabilitiesFromJson(json.optJSONArray("endpointCapabilities"));
        if (sourceCapabilities.isEmpty()) sourceCapabilities.addAll(legacyCapabilities);
        if (sourceCapabilities.isEmpty()) sourceCapabilities.addAll(endpointCapabilities);
        Set<String> endpointOverride = endpointCapabilities.isEmpty() ? null : endpointCapabilities;
        return new TaiModelSpec(
            json.optString("id", ""),
            json.optString("displayName", json.optString("id", "")),
            json.optString("roleHint", "Imported model"),
            json.optString("source", "imported"),
            localPath,
            json.optString("license", "User-provided model; license accepted externally"),
            json.optLong("sizeBytes", 0L),
            sourceCapabilities,
            json.optBoolean("builtInCatalogEntry", false),
            json.optJSONObject("runtimeProfile") == null ? null : TaiModelProfile.fromJson(json.optJSONObject("runtimeProfile")),
            backend,
            format,
            json.isNull("architecture") ? null : json.optString("architecture", null),
            json.isNull("quantization") ? null : json.optString("quantization", null),
            json.optInt("endpointContextWindow", json.optInt("contextWindow", defaultEndpointContextWindowFor(json.optString("id", ""), backend))),
            json.optInt("sourceContextWindow", json.optInt("contextWindow", defaultEndpointContextWindowFor(json.optString("id", ""), backend))),
            json.optInt("defaultMaxOutputTokens", defaultMaxOutputTokensFor(json.optString("id", ""), backend)),
            json.optInt("recommendedRamGb", 0),
            json.isNull("sha256") ? null : json.optString("sha256", null),
            endpointOverride,
            json.isNull("toolMode") ? null : json.optString("toolMode", null)
        );
    }

    @NonNull
    public static String inferBackend(@Nullable String path) {
        if (hasMnnHint(path)) return BACKEND_MNN_LLM;
        return BACKEND_LITERT_LM;
    }

    @NonNull
    public static String inferFormat(@Nullable String path) {
        if (hasKnownUnsupportedWeightHint(path)) return FORMAT_GGUF;
        if (hasMnnHint(path)) return FORMAT_MNN;
        return FORMAT_LITERTLM;
    }

    public static boolean isSupportedBackendFormat(@Nullable String backend, @Nullable String format) {
        return (BACKEND_LITERT_LM.equals(backend) && FORMAT_LITERTLM.equals(format))
            || (BACKEND_MNN_LLM.equals(backend) && FORMAT_MNN.equals(format));
    }

    @NonNull
    private static String requireSupportedBackend(@Nullable String backend) {
        if (BACKEND_LITERT_LM.equals(backend) || BACKEND_MNN_LLM.equals(backend)) return backend;
        throw new IllegalArgumentException("unsupported_backend");
    }

    @NonNull
    private static String requireSupportedFormat(@Nullable String format) {
        if (FORMAT_LITERTLM.equals(format) || FORMAT_MNN.equals(format)) return format;
        throw new IllegalArgumentException("unsupported_format");
    }

    @NonNull
    public static LinkedHashSet<String> endpointCapabilitiesFor(
        @NonNull String id,
        @NonNull String backend,
        @NonNull String format,
        @NonNull Set<String> sourceCapabilities,
        @Nullable String localPath
    ) {
        LinkedHashSet<String> source = normalizedCapabilities(sourceCapabilities);
        LinkedHashSet<String> endpoint = new LinkedHashSet<>();
        if (!isSupportedBackendFormat(backend, format)) return endpoint;
        String normalizedId = normalizedIdentity(id);

        if (BACKEND_MNN_LLM.equals(backend)) {
            // MNN embedding packages (config.json declaring text_embeddings) route to MnnEmbeddingRuntime,
            // not the chat engine, so they expose embeddings exclusively.
            if (source.contains(CAPABILITY_TEXT_EMBEDDINGS)) {
                endpoint.add(CAPABILITY_TEXT_EMBEDDINGS);
                return endpoint;
            }
            addIfPresent(endpoint, source, CAPABILITY_TEXT_CHAT, source.isEmpty());
            // MNN multimodal models take image/audio paths injected as inline <img>/<audio> markup.
            // Video has no MNN runtime path, so it stays source-only.
            addIfPresent(endpoint, source, CAPABILITY_IMAGE_INPUT, false);
            addIfPresent(endpoint, source, CAPABILITY_AUDIO_INPUT, false);
            addIfPresent(endpoint, source, CAPABILITY_CODE, false);
            addIfPresent(endpoint, source, "multilingual", false);
            addIfPresent(endpoint, source, "reasoning", false);
            addIfPresent(endpoint, source, CAPABILITY_TOOL_USE, false);
            return endpoint;
        }

        // The only raw .tflite path in this app is the LiteRT embedding runtime. Chat packages
        // must be .litertlm/.task containers even if user metadata incorrectly declares chat.
        if (localPath != null && localPath.toLowerCase(Locale.ROOT).endsWith(".tflite")) {
            addIfPresent(endpoint, source, CAPABILITY_TEXT_EMBEDDINGS, false);
            return endpoint;
        }

        if (normalizedIdentity(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M).equals(normalizedId)) {
            endpoint.add(CAPABILITY_TEXT_CHAT);
            endpoint.add(CAPABILITY_TOOL_USE);
            endpoint.add(CAPABILITY_MOBILE_ACTIONS);
            return endpoint;
        }

        addIfPresent(endpoint, source, CAPABILITY_TEXT_CHAT, source.isEmpty());
        addIfPresent(endpoint, source, CAPABILITY_TEXT_EMBEDDINGS, false);
        addIfPresent(endpoint, source, CAPABILITY_IMAGE_INPUT, false);
        addIfPresent(endpoint, source, CAPABILITY_AUDIO_INPUT, false);
        addIfPresent(endpoint, source, CAPABILITY_TOOL_USE, false);
        addIfPresent(endpoint, source, CAPABILITY_CODE, false);
        addIfPresent(endpoint, source, "multilingual", false);
        addIfPresent(endpoint, source, "reasoning", false);
        if (source.contains(CAPABILITY_SPECULATIVE_DECODING) && liteRtPackageHasSpeculativeDecoding(localPath)) {
            endpoint.add(CAPABILITY_SPECULATIVE_DECODING);
        }
        return endpoint;
    }

    @Nullable
    public static String toolModeFor(@NonNull String backend, @NonNull Set<String> endpointCapabilities) {
        if (!endpointCapabilities.contains(CAPABILITY_TOOL_USE)) return null;
        return BACKEND_MNN_LLM.equals(backend) ? TOOL_MODE_PROMPT_FALLBACK : TOOL_MODE_NATIVE;
    }

    @NonNull
    public static String redactToken(@Nullable String token) {
        if (token == null) return "";
        String value = token.trim();
        if (value.isEmpty()) return "";
        if (value.length() <= 12) return "..." + value.substring(Math.max(0, value.length() - 4));
        return value.substring(0, 6) + "..." + value.substring(value.length() - 4);
    }

    public static int defaultEndpointContextWindowFor(@Nullable String id, @Nullable String backend) {
        if (normalizedIdentity(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M).equals(normalizedIdentity(id))) return 1024;
        if (BACKEND_MNN_LLM.equals(backend)) return 8192;
        return 4096;
    }

    public static int defaultMaxOutputTokensFor(@Nullable String id, @Nullable String backend) {
        String normalizedId = normalizedIdentity(id);
        if (normalizedIdentity(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M).equals(normalizedId)) return 1024;
        if (normalizedIdentity(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT).equals(normalizedId)
            || normalizedIdentity(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT).equals(normalizedId)) {
            return 4000;
        }
        if (BACKEND_MNN_LLM.equals(backend)) return 1024;
        if (normalizedId.contains("qwen") || normalizedId.contains("deepseek")) return 4096;
        return 1024;
    }

    @NonNull
    private static JSONArray capabilityArray(@NonNull Set<String> values) {
        JSONArray capabilityArray = new JSONArray();
        for (String capability : values) capabilityArray.put(capability);
        return capabilityArray;
    }

    @NonNull
    private static LinkedHashSet<String> capabilitiesFromJson(@Nullable JSONArray capabilityArray) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        if (capabilityArray != null) {
            for (int i = 0; i < capabilityArray.length(); i++) {
                String capability = capabilityArray.optString(i, "");
                if (!capability.isEmpty()) capabilities.add(capability);
            }
        }
        return capabilities;
    }

    @NonNull
    private static LinkedHashSet<String> normalizedCapabilities(@NonNull Set<String> capabilities) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String capability : capabilities) {
            if (capability == null) continue;
            String value = capability.trim();
            if (!value.isEmpty()) normalized.add(value);
        }
        return normalized;
    }

    private static void addIfPresent(
        @NonNull LinkedHashSet<String> output,
        @NonNull Set<String> source,
        @NonNull String capability,
        boolean whenSourceEmpty
    ) {
        if (source.contains(capability) || whenSourceEmpty) output.add(capability);
    }

    @Nullable
    private static String normalizedToolMode(@Nullable String toolMode, @NonNull String backend, @NonNull Set<String> endpointCapabilities) {
        if (!endpointCapabilities.contains(CAPABILITY_TOOL_USE)) return null;
        if (TOOL_MODE_NATIVE.equals(toolMode) || TOOL_MODE_PROMPT_FALLBACK.equals(toolMode)) return toolMode;
        return toolModeFor(backend, endpointCapabilities);
    }

    private static boolean liteRtPackageHasSpeculativeDecoding(@Nullable String localPath) {
        if (localPath == null || localPath.trim().isEmpty()) return false;
        File file = new File(localPath);
        if (!file.isFile() || !file.canRead()) return false;
        try (Capabilities capabilities = new Capabilities(file.getAbsolutePath())) {
            return capabilities.hasSpeculativeDecodingSupport();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasMnnHint(@Nullable String path) {
        String value = path == null ? "" : path.toLowerCase(java.util.Locale.ROOT);
        return value.contains("mnn") || value.endsWith("config.json") || value.endsWith("llm.mnn");
    }

    private static boolean hasKnownUnsupportedWeightHint(@Nullable String path) {
        String value = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return value.endsWith(".gguf")
            || value.endsWith(".safetensors")
            || value.endsWith(".bin")
            || value.endsWith(".pt")
            || value.endsWith(".onnx");
    }

    @NonNull
    private static String normalizedIdentity(@Nullable String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
