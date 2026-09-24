package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class TaiModelCatalog {
    private static final String UNVERIFIED_ARTIFACT_POLICY = "Import-only: models.yaml provides repository URL and estimates, but no verified artifact path, revision, and checksum policy exists in code.";
    private static final Map<String, CatalogEntry> BUILT_IN_ENTRIES = buildEntries();
    private static volatile Map<String, CatalogEntry> entries = BUILT_IN_ENTRIES;
    private TaiModelCatalog() {}

    @NonNull public static Map<String, CatalogEntry> entries() { return entries; }
    @Nullable public static CatalogEntry get(@Nullable String modelId) { return modelId == null ? null : entries.get(modelId); }

    /** {@link #entries()} minus speech-to-text models — the chat catalog screen, the installed
     *  chat-model list, and the default-assistant picker should never show a Whisper entry
     *  alongside chat models. Speech models get their own "Speech-to-text" section instead. */
    @NonNull
    public static Map<String, CatalogEntry> chatEntries() {
        LinkedHashMap<String, CatalogEntry> chat = new LinkedHashMap<>();
        for (Map.Entry<String, CatalogEntry> entry : entries.entrySet()) {
            if (entry.getValue().endpointCapabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)) continue;
            chat.put(entry.getKey(), entry.getValue());
        }
        return chat;
    }

    /** Speech-to-text catalog entries only (the TAI "Speech-to-text" settings section). */
    @NonNull
    public static Map<String, CatalogEntry> speechEntries() {
        LinkedHashMap<String, CatalogEntry> speech = new LinkedHashMap<>();
        for (Map.Entry<String, CatalogEntry> entry : entries.entrySet()) {
            if (entry.getValue().endpointCapabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)) {
                speech.put(entry.getKey(), entry.getValue());
            }
        }
        return speech;
    }

    /** Synthetic catalog entry for an installed model that isn't in the curated catalog (imported or
     *  added by Hugging Face URL), so the catalog screen lists and manages it alongside built-ins. */
    @NonNull
    public static CatalogEntry installedModelEntry(@NonNull TaiModelSpec spec) {
        return new CatalogEntry(
            spec.id, spec.displayName,
            spec.roleHint == null || spec.roleHint.isEmpty() ? "Added model" : spec.roleHint,
            "", "main", null, spec.license, spec.sizeBytes, false,
            spec.backend, spec.format, spec.architecture, spec.quantization,
            spec.endpointContextWindow, spec.sourceContextWindow, spec.defaultMaxOutputTokens,
            spec.recommendedRamGb, spec.sha256,
            new LinkedHashSet<>(spec.sourceCapabilities),
            new LinkedHashSet<>(spec.endpointCapabilities), spec.toolMode,
            "installed", "installed",
            displayTagsFor(new LinkedHashSet<>(spec.sourceCapabilities)), "", "", false, false, "Already installed");
    }

    @Nullable
    public static CatalogEntry downloadEntry(@NonNull JSONObject download) {
        String modelId = download.optString("modelId", "").trim();
        if (modelId.isEmpty() || entries().containsKey(modelId)) return null;
        String path = download.optString("path", download.optString("url", ""));
        String backend = download.optString("backend", TaiModelSpec.inferBackend(path));
        String format = download.optString("format", TaiModelSpec.inferFormat(path));
        LinkedHashSet<String> sourceCapabilities = capabilities(download.optJSONArray("capabilities"));
        if (sourceCapabilities.isEmpty()) sourceCapabilities.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        long sizeBytes = download.optLong("totalBytes", download.optLong("bytesRead", 0L));
        return new CatalogEntry(
            modelId,
            download.optString("displayName", modelId),
            "Downloading model",
            repoId(download.optString("url", "")),
            "main",
            null,
            download.optString("license", "User accepted provider terms externally"),
            Math.max(0L, sizeBytes),
            false,
            backend,
            format,
            download.optString("architecture", ""),
            emptyToNull(download.optString("quantization", "")),
            download.optInt("contextWindow", TaiModelSpec.defaultEndpointContextWindowFor(modelId, backend)),
            download.optInt("contextWindow", TaiModelSpec.defaultEndpointContextWindowFor(modelId, backend)),
            TaiModelSpec.defaultMaxOutputTokensFor(modelId, backend),
            download.optInt("recommendedRamGb", 0),
            emptyToNull(download.optString("sha256", "")),
            sourceCapabilities,
            null,
            null,
            "downloaded",
            "downloaded",
            displayTagsFor(sourceCapabilities),
            sizeBytes > 0L ? "" : "unknown size",
            "",
            false,
            false,
            "Download in progress");
    }

    static synchronized void applyRemotePayload(@NonNull JSONObject payload, boolean allowEqualVersion) {
        JSONArray remoteEntries = payload.optJSONArray("entries");
        if (remoteEntries == null) return;
        LinkedHashMap<String, CatalogEntry> merged = new LinkedHashMap<>(BUILT_IN_ENTRIES);
        for (int i = 0; i < remoteEntries.length(); i++) {
            JSONObject item = remoteEntries.optJSONObject(i);
            if (item == null) continue;
            try {
                LinkedHashSet<String> capabilities = new LinkedHashSet<>();
                JSONArray values = item.optJSONArray("capabilities");
                if (values != null) for (int j = 0; j < values.length(); j++) capabilities.add(values.getString(j));
                LinkedHashSet<String> sourceCapabilities = new LinkedHashSet<>();
                JSONArray sourceValues = item.optJSONArray("sourceCapabilities");
                if (sourceValues != null) for (int j = 0; j < sourceValues.length(); j++) sourceCapabilities.add(sourceValues.getString(j));
                if (sourceCapabilities.isEmpty()) sourceCapabilities.addAll(capabilities);
                LinkedHashSet<String> endpointCapabilities = new LinkedHashSet<>();
                JSONArray endpointValues = item.optJSONArray("endpointCapabilities");
                if (endpointValues != null) for (int j = 0; j < endpointValues.length(); j++) endpointCapabilities.add(endpointValues.getString(j));
                CatalogEntry entry = new CatalogEntry(item.getString("modelId"), item.getString("displayName"),
                    item.optString("roleHint", "Curated model"), item.getString("repositoryId"),
                    item.getString("revision"), item.isNull("artifactPath") ? null : item.optString("artifactPath"),
                    item.getString("license"), item.getLong("sizeBytes"), item.optBoolean("gated", false),
                    item.getString("backend"), item.getString("format"), item.optString("architecture", ""),
                    item.isNull("quantization") ? null : item.optString("quantization"),
                    item.optInt("endpointContextWindow", item.optInt("contextWindow", 4096)),
                    item.optInt("sourceContextWindow", item.optInt("contextWindow", 4096)),
                    item.optInt("defaultMaxOutputTokens", TaiModelSpec.defaultMaxOutputTokensFor(item.getString("modelId"), item.getString("backend"))),
                    item.optInt("recommendedRamGb", 0), item.isNull("sha256") ? null : item.optString("sha256"),
                    sourceCapabilities, endpointCapabilities.isEmpty() ? null : endpointCapabilities,
                    item.isNull("toolMode") ? null : item.optString("toolMode", null),
                    item.optString("jobGroup", "remote"), item.optString("priority", "remote"),
                    displayTags(item.optJSONArray("displayCapabilityTags")), item.optString("sizeEstimate", ""),
                    item.optString("ramTier", ""), item.optBoolean("recommended", false),
                    item.optBoolean("downloadAvailable", true), item.optString("unavailableReason", ""));
                if (!TaiModelSpec.isSupportedBackendFormat(entry.backend, entry.format)) continue;
                merged.put(entry.modelId, entry);
            } catch (Exception ignored) {}
        }
        entries = Collections.unmodifiableMap(merged);
    }

    @NonNull
    private static Map<String, CatalogEntry> buildEntries() {
        LinkedHashMap<String, CatalogEntry> entries = new LinkedHashMap<>();
        entries.put(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT, liteRtAvailable(
            TaiModelRegistry.MODEL_GEMMA_4_E2B_IT, "Gemma 4 E2B IT", "general_multimodal", "recommended_default", true,
            "Fast assistant", "litert-community/gemma-4-E2B-it-litert-lm", "6e5c4f1e395deb959c494953478fa5cec4b8008f",
            "gemma-4-E2B-it.litertlm", "Apache-2.0", 2_588_147_712L, "2.4 GB", "8GB+", false,
            tags("Text", "Vision", "Audio", "Tools"), setOf("text_chat", "image_input", "audio_input", "tool_use", "llm_thinking", "speculative_decoding")));
        entries.put(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT, liteRtAvailable(
            TaiModelRegistry.MODEL_GEMMA_4_E4B_IT, "Gemma 4 E4B IT", "general_multimodal", "premium_default", false,
            "Coding and reasoning", "litert-community/gemma-4-E4B-it-litert-lm", "28299f30ee4d43294517a4ac93abd6163412f07f",
            "gemma-4-E4B-it.litertlm", "Apache-2.0", 3_659_530_240L, "3.7 GB", "12GB+", false,
            tags("Text", "Vision", "Audio", "Code", "Reasoning", "Tools"),
            setOf("text_chat", "image_input", "audio_input", "tool_use", "code", "reasoning", "llm_thinking", "speculative_decoding")));
        entries.put("qwen2.5-1.5b-instruct-litert-lm", liteRtImportOnly(
            "qwen2.5-1.5b-instruct-litert-lm", "Qwen2.5 1.5B Instruct", "lightweight_text", "lightweight_alternative", false,
            "Lightweight text, code, and multilingual", "litert-community/Qwen2.5-1.5B-Instruct",
            1_597_931_520L, "1.5 GB", "6GB+", "q8", "Apache-2.0",
            tags("Text", "Code", "Multilingual"), setOf("text_chat", "code", "multilingual")));
        entries.put("deepseek-r1-distill-qwen-1.5b-litert-lm", liteRtAvailable(
            "deepseek-r1-distill-qwen-1.5b-litert-lm", "DeepSeek-R1 Distill 1.5B", "reasoning", "reasoning_small", false,
            "Small reasoning model", "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            "2f8b8ee90d8f93b15305b699e8772b277d074a9a",
            "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm", "MIT",
            1_833_451_520L, "1.7 GB", "6GB+", false, "deepseek-r1-distill-qwen", "q8",
            "69b35f01759eed765641ab4af589bbe98131fd2825662a086d9037409b8c1295",
            tags("Reasoning", "Text"), setOf("text_chat", "reasoning")));
        entries.put(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M, liteRtAvailable(
            TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M, "FunctionGemma 270M", "tool_calling", "experimental_launcher_agent", false,
            "Mobile actions tool-call model", "litert-community/functiongemma-270m-ft-mobile-actions", "38942192c9b723af836d489074823ff33d4a3e7a",
            "mobile_actions_q8_ekv1024.litertlm", "Gemma Terms of Use", 288_964_608L, "0.3 GB", "6GB+", true,
            tags("Tools"), setOf("text_chat", "tool_use", "mobile_actions")));
        // EmbeddingGemma is a raw .tflite served by LiteRtEmbeddingRuntime, not a chat .litertlm package.
        // The text_embeddings capability + .tflite artifact make the downloader fetch the sentencepiece.model
        // sidecar and route requests to the embedding runtime rather than the LiteRT-LM chat engine.
        entries.put("embeddinggemma-300m", liteRtAvailable(
            "embeddinggemma-300m", "EmbeddingGemma 300M", "embedding", "embedding_default", false,
            "Text embeddings", "litert-community/embeddinggemma-300m", "870cbe05ef460385363c6b574c851ae5d8989ce3",
            "embeddinggemma-300M_seq1024_mixed-precision.tflite", "Gemma Terms of Use", 183_329_528L, "183 MB", "4GB+", true,
            "gemma", null, null,
            tags("Embeddings"), setOf(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)));
        // MNN-format embedding model — routes to MnnEmbeddingRuntime via the MNN Transformer::Embedding
        // engine. config.json + text_embeddings capability make it an embedding package, not a chat model.
        entries.put("qwen3-embedding-0.6b-mnn", mnnAvailable(
            "qwen3-embedding-0.6b-mnn", "Qwen3 Embedding 0.6B", "embedding", "embedding_mnn", false,
            "Text embeddings (MNN)", "taobao-mnn/Qwen3-Embedding-0.6B-MNN", 377_998_519L,
            "378 MB", "4GB+", "qwen3", "int4", tags("Embeddings"), setOf(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)));

        entries.put("qwen2.5-coder-1.5b-instruct-mnn", mnnAvailable(
            "qwen2.5-coder-1.5b-instruct-mnn", "Qwen2.5-Coder 1.5B", "coding", "recommended_coder", true,
            "Code and terminal assistant", "taobao-mnn/Qwen2.5-Coder-1.5B-Instruct-MNN", 971_254_765L,
            "971 MB", "4GB-6GB+", "qwen2.5-coder", "int4", tags("Code", "Text", "Tools"), setOf("text_chat", "code", "tool_use")));
        entries.put("qwen2.5-coder-7b-instruct-mnn", mnnAvailable(
            "qwen2.5-coder-7b-instruct-mnn", "Qwen2.5-Coder 7B", "coding", "advanced_coder", false,
            "Higher quality code model", "taobao-mnn/Qwen2.5-Coder-7B-Instruct-MNN", 4_426_674_424L,
            "4.4 GB", "10GB-12GB+", "qwen2.5-coder", "int4", tags("Code", "Text"), setOf("text_chat", "code")));
        entries.put("qwen2.5-0.5b-instruct-mnn", mnnAvailable(
            "qwen2.5-0.5b-instruct-mnn", "Qwen2.5 0.5B", "lightweight_text", "tiny_general", false,
            "Tiny general chat", "taobao-mnn/Qwen2.5-0.5B-Instruct-MNN", 556_808_791L,
            "557 MB", "3GB+", "qwen2.5", "int4", tags("Text", "Multilingual"), setOf("text_chat", "multilingual")));
        entries.put("qwen2.5-1.5b-instruct-mnn", mnnAvailable(
            "qwen2.5-1.5b-instruct-mnn", "Qwen2.5 1.5B", "general_text", "lightweight_general", false,
            "Lightweight text and multilingual", "taobao-mnn/Qwen2.5-1.5B-Instruct-MNN", 879_484_183L,
            "879 MB", "4GB-6GB+", "qwen2.5", "int4", tags("Text", "Multilingual"), setOf("text_chat", "multilingual")));
        entries.put("qwen2.5-3b-instruct-mnn", mnnAvailable(
            "qwen2.5-3b-instruct-mnn", "Qwen2.5 3B", "general_text", "balanced_general", false,
            "Balanced local multilingual assistant", "taobao-mnn/Qwen2.5-3B-Instruct-MNN", 2_369_484_250L,
            "2.4 GB", "6GB-8GB+", "qwen2.5", "int4", tags("Text", "Multilingual"), setOf("text_chat", "multilingual")));
        // Whisper ACFT speech-to-text (litert-community/whisper-acft, phase 1: catalog + downloader
        // only — MultiBackendTaiRuntime routing is phase 2). Each size/language id ships two window
        // graphs (5s, 10s, chosen at download time); the default artifact here is the 10s graph and
        // the 5s graph is offered as a CatalogEntry.WindowVariant swapped in by CatalogEntry#withWindow.
        // The tokenizer.json sidecar comes from the matching openai/whisper-{size}{.en} repo.
        final String whisperAcftRevision = "f8ab0a00ea95f6e0f2cee200b18671a599a0b0d6";
        entries.put("whisper-acft-base", whisperAvailable(
            "whisper-acft-base", "Whisper ACFT Base", "Speech-to-text (multilingual)",
            "litert-community/whisper-acft", whisperAcftRevision,
            "base/acft_whisper_base_10s_drq.tflite", 101_391_632L,
            "61d7dba161c3c1b77a940e5c658eaa9012b6f7cbec1058f845276abeed2805b5",
            "97 MB", "6GB+",
            whisperTokenizerSidecar("whisper-base", "e37978b90ca9030d5170a5c07aadb050351a65bb",
                "27fc476bfe7f17299480be2273fc0608e4d5a99aba2ab5dec5374b4482d1a566"),
            whisperWindows(
                "base/acft_whisper_base_5s_drq.tflite", 100_879_632L,
                "7a9dcec5528c37577cfe5df0bd53720cebaeb63981549fffc70825bab689e225",
                "base/acft_whisper_base_10s_drq.tflite", 101_391_632L,
                "61d7dba161c3c1b77a940e5c658eaa9012b6f7cbec1058f845276abeed2805b5")));
        entries.put("whisper-acft-base-en", whisperAvailable(
            "whisper-acft-base-en", "Whisper ACFT Base (English)", "Speech-to-text (English only)",
            "litert-community/whisper-acft", whisperAcftRevision,
            "base.en/acft_whisper_base.en_10s_drq.tflite", 101_390_600L,
            "d993bf12bb49bb7ddf94779613293cca2c277d5c0582e4f04b4d4f7d8d331102",
            "97 MB", "6GB+",
            whisperTokenizerSidecar("whisper-base.en", "911407f4214e0e1d82085af863093ec0b66f9cd6",
                "5eb60cec1e77aeeb6869a2bb5a8e01a84c3fe5d072d75369343021fe6f5310d0"),
            whisperWindows(
                "base.en/acft_whisper_base.en_5s_drq.tflite", 100_878_600L,
                "aacded4e706c559d7e840716c54d872167754f57467ad4a80ac4e4d05a8d6d2f",
                "base.en/acft_whisper_base.en_10s_drq.tflite", 101_390_600L,
                "d993bf12bb49bb7ddf94779613293cca2c277d5c0582e4f04b4d4f7d8d331102")));
        entries.put("whisper-acft-small", whisperAvailable(
            "whisper-acft-small", "Whisper ACFT Small", "Speech-to-text (multilingual)",
            "litert-community/whisper-acft", whisperAcftRevision,
            "small/acft_whisper_small_10s_drq.tflite", 286_277_672L,
            "f74c4c464b96ee1f52afb3d876e5eb89a87212cf538404b2747febf61c00ba1b",
            "273 MB", "8GB+",
            whisperTokenizerSidecar("whisper-small", "973afd24965f72e36ca33b3055d56a652f456b4d",
                "27fc476bfe7f17299480be2273fc0608e4d5a99aba2ab5dec5374b4482d1a566"),
            whisperWindows(
                "small/acft_whisper_small_5s_drq.tflite", 285_509_672L,
                "4695243bffbe1b7c8799b06e058395dec02c798f8a0a3dcff093c0797d562fc7",
                "small/acft_whisper_small_10s_drq.tflite", 286_277_672L,
                "f74c4c464b96ee1f52afb3d876e5eb89a87212cf538404b2747febf61c00ba1b")));
        entries.put("whisper-acft-small-en", whisperAvailable(
            "whisper-acft-small-en", "Whisper ACFT Small (English)", "Speech-to-text (English only)",
            "litert-community/whisper-acft", whisperAcftRevision,
            "small.en/acft_whisper_small.en_10s_drq.tflite", 286_276_128L,
            "58edc288e8aad1da2a3df0545edadf5f1c6119ff70682e37031119ad89130daf",
            "273 MB", "8GB+",
            whisperTokenizerSidecar("whisper-small.en", "e8727524f962ee844a7319d92be39ac1bd25655a",
                "5eb60cec1e77aeeb6869a2bb5a8e01a84c3fe5d072d75369343021fe6f5310d0"),
            whisperWindows(
                "small.en/acft_whisper_small.en_5s_drq.tflite", 285_508_128L,
                "7c71a5d8f9b59f93ab17e63b568ef674716420bc8bbabcc5da315ab0576b96ef",
                "small.en/acft_whisper_small.en_10s_drq.tflite", 286_276_128L,
                "58edc288e8aad1da2a3df0545edadf5f1c6119ff70682e37031119ad89130daf")));

        entries.put("deepseek-r1-1.5b-qwen-mnn", mnnAvailable(
            "deepseek-r1-1.5b-qwen-mnn", "DeepSeek-R1 1.5B Qwen", "reasoning", "lightweight_reasoning", false,
            "Small reasoning model", "taobao-mnn/DeepSeek-R1-1.5B-Qwen-MNN", 1_020_644_237L,
            "1.0 GB", "4GB-6GB+", "deepseek-r1-qwen", "int4", tags("Reasoning", "Text"), setOf("text_chat", "reasoning")));

        return Collections.unmodifiableMap(entries);
    }

    private static CatalogEntry liteRtAvailable(String id, String name, String jobGroup, String priority, boolean recommended,
                                                 String role, String repo, String revision, String file, String license, long size,
                                                 String sizeEstimate, String ramTier, boolean gated, LinkedHashSet<String> displayTags,
                                                 LinkedHashSet<String> capabilities) {
        return liteRtAvailable(id, name, jobGroup, priority, recommended, role, repo, revision, file, license, size,
            sizeEstimate, ramTier, gated, "gemma", null, null, displayTags, capabilities);
    }

    private static CatalogEntry liteRtAvailable(String id, String name, String jobGroup, String priority, boolean recommended,
                                                 String role, String repo, String revision, String file, String license, long size,
                                                 String sizeEstimate, String ramTier, boolean gated, String architecture,
                                                 @Nullable String quantization, @Nullable String sha256,
                                                 LinkedHashSet<String> displayTags, LinkedHashSet<String> capabilities) {
        return entry(id, name, role, repo, revision, file, license, size, gated, TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM, architecture, quantization,
            TaiModelSpec.defaultEndpointContextWindowFor(id, TaiModelSpec.BACKEND_LITERT_LM),
            ramGb(ramTier), sha256, capabilities,
            jobGroup, priority, displayTags, sizeEstimate, ramTier, recommended, true, "");
    }

    private static CatalogEntry liteRtImportOnly(String id, String name, String jobGroup, String priority, boolean recommended,
                                                  String role, String repo, long size, String sizeEstimate, String ramTier,
                                                  @Nullable String quantization, String license, LinkedHashSet<String> displayTags,
                                                  LinkedHashSet<String> capabilities) {
        return entry(id, name, role, repo, "main", null, license, size, false,
            TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, "", quantization, 4096, ramGb(ramTier), null,
            capabilities, jobGroup, priority, displayTags, sizeEstimate, ramTier, recommended, false, UNVERIFIED_ARTIFACT_POLICY);
    }

    private static CatalogEntry mnnAvailable(String id, String name, String jobGroup, String priority, boolean recommended,
                                             String role, String repo, long size, String sizeEstimate, String ramTier,
                                             String architecture, String quantization, LinkedHashSet<String> displayTags,
                                             LinkedHashSet<String> capabilities) {
        int endpointContextWindow = id.startsWith("qwen2.5-coder-") ? 16_384 : 8192;
        return entry(id, name, role, repo, "main", "config.json", "Apache-2.0", size, false,
            TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.FORMAT_MNN, architecture, quantization, endpointContextWindow,
            ramGb(ramTier), null, capabilities, jobGroup, priority, displayTags, sizeEstimate, ramTier,
            recommended, true, "");
    }

    /** A Whisper ACFT speech-to-text entry: {@code defaultArtifactPath/defaultSize/defaultSha256} is
     *  the 10s window (the entry's resting state); {@code windows} carries both window variants so
     *  {@link CatalogEntry#withWindow} can swap in the 5s graph at download time. */
    private static CatalogEntry whisperAvailable(String id, String name, String role, String repo, String revision,
                                                  String defaultArtifactPath, long defaultSize, String defaultSha256,
                                                  String sizeEstimate, String ramTier,
                                                  CatalogEntry.Sidecar tokenizerSidecar,
                                                  Map<Integer, CatalogEntry.WindowVariant> windows) {
        List<CatalogEntry.Sidecar> sidecars = Collections.singletonList(tokenizerSidecar);
        LinkedHashSet<String> capabilities = setOf(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
        return new CatalogEntry(id, name, role, repo, revision, defaultArtifactPath, "Apache-2.0", defaultSize,
            false, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, "whisper-acft", "int8_drq",
            128, 128, 128, ramGb(ramTier), defaultSha256, capabilities, null, null,
            "speech_to_text", "speech_to_text", tags("Speech"), sizeEstimate, ramTier, false, true, "",
            sidecars, windows);
    }

    /** {@code url/localName/sha256} for the {@code tokenizer.json} sidecar of the matching
     *  {@code openai/whisper-{size}{.en}} repo, at a pinned revision. */
    private static CatalogEntry.Sidecar whisperTokenizerSidecar(String tokenizerRepo, String revision, String sha256) {
        return new CatalogEntry.Sidecar(
            "https://huggingface.co/openai/" + tokenizerRepo + "/resolve/" + revision + "/tokenizer.json",
            "tokenizer.json", sha256);
    }

    private static Map<Integer, CatalogEntry.WindowVariant> whisperWindows(
            String path5s, long size5s, String sha5s, String path10s, long size10s, String sha10s) {
        LinkedHashMap<Integer, CatalogEntry.WindowVariant> windows = new LinkedHashMap<>();
        windows.put(5, new CatalogEntry.WindowVariant(5, path5s, size5s, sha5s));
        windows.put(10, new CatalogEntry.WindowVariant(10, path10s, size10s, sha10s));
        return windows;
    }

    private static CatalogEntry entry(String id, String name, String role, String repo, String revision, @Nullable String file,
                                      String license, long size, boolean gated, String backend, String format, String architecture,
                                      @Nullable String quantization, int contextWindow, int ramGb, @Nullable String sha256,
                                      LinkedHashSet<String> capabilities, String jobGroup, String priority,
                                      LinkedHashSet<String> displayTags, String sizeEstimate, String ramTier,
                                      boolean recommended, boolean downloadAvailable, String unavailableReason) {
        return new CatalogEntry(id, name, role, repo, revision, file, license, size, gated, backend, format, architecture,
            quantization, contextWindow, sourceContextWindowFor(id, backend, contextWindow),
            TaiModelSpec.defaultMaxOutputTokensFor(id, backend), ramGb, sha256,
            capabilities, null, null, jobGroup, priority, displayTags, sizeEstimate,
            ramTier, recommended, downloadAvailable, unavailableReason);
    }

    private static int sourceContextWindowFor(String id, String backend, int endpointContextWindow) {
        if (TaiModelRegistry.MODEL_GEMMA_4_E2B_IT.equals(id) || TaiModelRegistry.MODEL_GEMMA_4_E4B_IT.equals(id)) return 32_768;
        if (TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M.equals(id)) return 1024;
        if ("qwen2.5-coder-7b-instruct-mnn".equals(id)) return 131_072;
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend) && id.startsWith("qwen2.5-")) return 32_768;
        return endpointContextWindow;
    }

    private static LinkedHashSet<String> setOf(String... values) { return new LinkedHashSet<>(Arrays.asList(values)); }
    private static LinkedHashSet<String> tags(String... values) { return setOf(values); }
    private static LinkedHashSet<String> capabilities(@Nullable JSONArray values) {
        LinkedHashSet<String> caps = new LinkedHashSet<>();
        if (values != null) for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "");
            if (!value.isEmpty()) caps.add(value);
        }
        return caps;
    }
    private static LinkedHashSet<String> displayTagsFor(@NonNull LinkedHashSet<String> capabilities) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) tags.add("Text");
        if (capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)) tags.add("Embeddings");
        if (capabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)) tags.add("Speech");
        if (capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT)) tags.add("Vision");
        if (capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT)) tags.add("Audio");
        if (capabilities.contains(TaiModelSpec.CAPABILITY_CODE)) tags.add("Code");
        if (capabilities.contains(TaiModelSpec.CAPABILITY_TOOL_USE)) tags.add("Tools");
        return tags;
    }
    private static String repoId(@NonNull String url) {
        String prefix = "https://huggingface.co/";
        if (!url.startsWith(prefix)) return "local/" + Integer.toHexString(url.hashCode());
        String path = url.substring(prefix.length());
        int resolve = path.indexOf("/resolve/");
        if (resolve >= 0) path = path.substring(0, resolve);
        String[] parts = path.split("/");
        return parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()
            ? parts[0] + "/" + parts[1]
            : "local/" + Integer.toHexString(url.hashCode());
    }
    @Nullable private static String emptyToNull(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
    private static LinkedHashSet<String> displayTags(@Nullable JSONArray values) throws Exception {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (values != null) for (int i = 0; i < values.length(); i++) tags.add(values.getString(i));
        return tags;
    }
    private static int ramGb(String ramTier) {
        if (ramTier == null || ramTier.isEmpty()) return 0;
        StringBuilder digits = new StringBuilder();
        for (int i = ramTier.length() - 1; i >= 0; i--) {
            char c = ramTier.charAt(i);
            if (Character.isDigit(c)) digits.insert(0, c);
            else if (digits.length() > 0) break;
        }
        if (digits.length() == 0) return 0;
        try { return Integer.parseInt(digits.toString()); } catch (NumberFormatException e) { return 0; }
    }

    public static final class CatalogEntry {
        public final String modelId, displayName, roleHint, repositoryId, revision, artifactPath, license;
        public final long sizeBytes;
        public final boolean gated;
        public final String backend, format, architecture, quantization;
        public final String jobGroup, priority, sizeEstimate, ramTier, unavailableReason;
        public final int contextWindow, endpointContextWindow, sourceContextWindow, defaultMaxOutputTokens, recommendedRamGb;
        public final boolean recommended, downloadAvailable;
        @Nullable public final String sha256;
        @Nullable public final String toolMode;
        public final LinkedHashSet<String> capabilities, endpointCapabilities, sourceCapabilities, displayCapabilityTags;
        public final String providerPageUrl, downloadUrl;
        /** Extra files a download must also fetch (e.g. a Whisper {@code tokenizer.json} from the
         *  matching {@code openai/whisper-*} repo), reusing the downloader's .part/resume/hash helpers. */
        public final List<Sidecar> sidecars;
        /** Whisper-only: the alternate window graphs (5s/10s) this entry's model id can be downloaded
         *  as. Empty for every non-speech entry. {@link #withWindow} swaps the active artifact. */
        public final Map<Integer, WindowVariant> speechWindows;

        private CatalogEntry(String modelId, String displayName, String roleHint, String repositoryId,
                             String revision, @Nullable String artifactPath, String license, long sizeBytes,
                             boolean gated, String backend, String format, String architecture,
                             @Nullable String quantization, int endpointContextWindow, int sourceContextWindow,
                             int defaultMaxOutputTokens, int recommendedRamGb, @Nullable String sha256,
                             LinkedHashSet<String> sourceCapabilities,
                             @Nullable LinkedHashSet<String> endpointCapabilities, @Nullable String toolMode,
                             String jobGroup, String priority,
                             LinkedHashSet<String> displayCapabilityTags, String sizeEstimate, String ramTier,
                             boolean recommended, boolean downloadAvailable, String unavailableReason) {
            this(modelId, displayName, roleHint, repositoryId, revision, artifactPath, license, sizeBytes, gated,
                backend, format, architecture, quantization, endpointContextWindow, sourceContextWindow,
                defaultMaxOutputTokens, recommendedRamGb, sha256, sourceCapabilities, endpointCapabilities,
                toolMode, jobGroup, priority, displayCapabilityTags, sizeEstimate, ramTier, recommended,
                downloadAvailable, unavailableReason, Collections.<Sidecar>emptyList(),
                Collections.<Integer, WindowVariant>emptyMap());
        }

        private CatalogEntry(String modelId, String displayName, String roleHint, String repositoryId,
                             String revision, @Nullable String artifactPath, String license, long sizeBytes,
                             boolean gated, String backend, String format, String architecture,
                             @Nullable String quantization, int endpointContextWindow, int sourceContextWindow,
                             int defaultMaxOutputTokens, int recommendedRamGb, @Nullable String sha256,
                             LinkedHashSet<String> sourceCapabilities,
                             @Nullable LinkedHashSet<String> endpointCapabilities, @Nullable String toolMode,
                             String jobGroup, String priority,
                             LinkedHashSet<String> displayCapabilityTags, String sizeEstimate, String ramTier,
                             boolean recommended, boolean downloadAvailable, String unavailableReason,
                             @Nullable List<Sidecar> sidecars, @Nullable Map<Integer, WindowVariant> speechWindows) {
            this.modelId = modelId; this.displayName = displayName; this.roleHint = roleHint;
            this.repositoryId = repositoryId; this.revision = revision; this.artifactPath = artifactPath;
            this.license = license; this.sizeBytes = sizeBytes; this.gated = gated; this.backend = backend;
            this.format = format; this.architecture = architecture; this.quantization = quantization;
            this.endpointContextWindow = endpointContextWindow; this.sourceContextWindow = sourceContextWindow;
            this.contextWindow = endpointContextWindow; this.defaultMaxOutputTokens = defaultMaxOutputTokens;
            this.recommendedRamGb = recommendedRamGb; this.sha256 = sha256;
            this.sourceCapabilities = sourceCapabilities;
            this.endpointCapabilities = endpointCapabilities == null
                ? TaiModelSpec.endpointCapabilitiesFor(modelId, backend, format, sourceCapabilities, null)
                : endpointCapabilities;
            this.capabilities = this.endpointCapabilities;
            this.toolMode = toolMode == null ? TaiModelSpec.toolModeFor(backend, this.endpointCapabilities) : toolMode;
            this.jobGroup = jobGroup; this.priority = priority;
            this.displayCapabilityTags = displayCapabilityTags; this.sizeEstimate = sizeEstimate; this.ramTier = ramTier;
            this.recommended = recommended; this.downloadAvailable = downloadAvailable; this.unavailableReason = unavailableReason;
            this.providerPageUrl = "https://huggingface.co/" + repositoryId;
            this.downloadUrl = !downloadAvailable || artifactPath == null ? null : providerPageUrl + "/resolve/" + revision + "/" + artifactPath + "?download=true";
            this.sidecars = sidecars == null || sidecars.isEmpty()
                ? Collections.<Sidecar>emptyList() : Collections.unmodifiableList(new ArrayList<>(sidecars));
            this.speechWindows = speechWindows == null || speechWindows.isEmpty()
                ? Collections.<Integer, WindowVariant>emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(speechWindows));
        }

        /** Returns this entry with the given window's graph as its active artifact (id/capabilities
         *  unchanged) so a download can fetch the 5s graph instead of the 10s default, or vice versa.
         *  Returns {@code this} unchanged for a window this entry doesn't have (or a non-speech entry). */
        @NonNull
        public CatalogEntry withWindow(int windowSeconds) {
            WindowVariant variant = speechWindows.get(windowSeconds);
            if (variant == null) return this;
            return new CatalogEntry(modelId, displayName, roleHint, repositoryId, revision, variant.artifactPath,
                license, variant.sizeBytes, gated, backend, format, architecture, quantization,
                endpointContextWindow, sourceContextWindow, defaultMaxOutputTokens, recommendedRamGb,
                variant.sha256, sourceCapabilities, endpointCapabilities, toolMode, jobGroup, priority,
                displayCapabilityTags, sizeEstimate, ramTier, recommended, downloadAvailable, unavailableReason,
                sidecars, speechWindows);
        }

        /** A required extra file (e.g. Whisper's {@code tokenizer.json} from the paired
         *  {@code openai/whisper-*} repo) downloaded alongside the main artifact. */
        public static final class Sidecar {
            public final String url;
            public final String localName;
            @Nullable public final String sha256;
            public Sidecar(@NonNull String url, @NonNull String localName, @Nullable String sha256) {
                this.url = url; this.localName = localName; this.sha256 = sha256;
            }
        }

        /** One window-length graph of a speech-to-text model (Whisper's 5s/10s ACFT exports). */
        public static final class WindowVariant {
            public final int windowSeconds;
            public final String artifactPath;
            public final long sizeBytes;
            @Nullable public final String sha256;
            public WindowVariant(int windowSeconds, @NonNull String artifactPath, long sizeBytes, @Nullable String sha256) {
                this.windowSeconds = windowSeconds; this.artifactPath = artifactPath;
                this.sizeBytes = sizeBytes; this.sha256 = sha256;
            }
        }
    }
}
