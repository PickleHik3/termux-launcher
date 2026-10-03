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
    /** The one Parakeet speech-to-text entry; the speech model picker's "Parakeet" engine. */
    public static final String PARAKEET_TDT_V3_ID = "parakeet-tdt-0.6b-v3";
    /** The one speech-output entry: KittenTTS nano 0.8, the Model centre's "Voice output" row. */
    public static final String KITTEN_TTS_NANO_ID = "kittentts-nano-0.8";
    /** The one embedding entry: EmbeddingGemma 300M, the Model centre's "Embeddings" row. */
    public static final String EMBEDDING_GEMMA_300M_ID = "embeddinggemma-300m";
    /** Wallpaper vision graphs (the Model centre's Vision segment), run by the analysis job. */
    public static final String DEPTH_ANYTHING_3_SMALL_ID = "depth-anything-3-small";
    public static final String DEPTH_ANYTHING_V2_SMALL_ID = "depth-anything-v2-small";
    public static final String SEGFORMER_B0_ADE20K_ID = "segformer-b0-ade20k";
    public static final String U2NET_ID = "u2net";
    private static final Map<String, CatalogEntry> BUILT_IN_ENTRIES = buildEntries();
    private static volatile Map<String, CatalogEntry> entries = BUILT_IN_ENTRIES;
    private TaiModelCatalog() {}

    /** Drops a remote overlay a test applied, so the next test sees the built-in catalogue. */
    @androidx.annotation.VisibleForTesting
    public static synchronized void resetForTesting() {
        entries = BUILT_IN_ENTRIES;
    }

    @NonNull public static Map<String, CatalogEntry> entries() { return entries; }
    @Nullable public static CatalogEntry get(@Nullable String modelId) { return modelId == null ? null : entries.get(modelId); }

    /** {@link #entries()} minus speech models (speech-to-text and speech output) and embedding-only
     *  models — the chat catalog screen, the installed chat-model list, and the default-assistant
     *  picker should never show a Whisper, KittenTTS or EmbeddingGemma entry alongside chat models.
     *  Speech models get their own sections on the Speech segment, embedders their own section at
     *  the foot of the Chat segment ({@link #embeddingEntries()}). */
    @NonNull
    public static Map<String, CatalogEntry> chatEntries() {
        LinkedHashMap<String, CatalogEntry> chat = new LinkedHashMap<>();
        for (Map.Entry<String, CatalogEntry> entry : entries.entrySet()) {
            if (entry.getValue().endpointCapabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)) continue;
            if (entry.getValue().endpointCapabilities.contains(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH)) continue;
            if (TaiModelSpec.isVisionTool(entry.getValue().endpointCapabilities)) continue;
            if (isImageGeneration(entry.getValue())) continue;
            if (isEmbeddingOnly(entry.getValue())) continue;
            chat.put(entry.getKey(), entry.getValue());
        }
        return chat;
    }

    /** Embedding-only catalog entries (the Chat segment's "Embeddings" section). */
    @NonNull
    public static Map<String, CatalogEntry> embeddingEntries() {
        LinkedHashMap<String, CatalogEntry> embeddings = new LinkedHashMap<>();
        for (Map.Entry<String, CatalogEntry> entry : entries.entrySet()) {
            if (isEmbeddingOnly(entry.getValue())) embeddings.put(entry.getKey(), entry.getValue());
        }
        return embeddings;
    }

    private static boolean isImageGeneration(@NonNull CatalogEntry entry) {
        return TaiModelSpec.BACKEND_MNN_DIFFUSION.equals(entry.backend)
            || entry.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_GENERATION);
    }

    private static boolean isEmbeddingOnly(@NonNull CatalogEntry entry) {
        return entry.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)
            && !entry.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT);
    }

    /** Wallpaper vision catalog entries only (the Model centre's Vision segment). */
    @NonNull
    public static Map<String, CatalogEntry> visionEntries() {
        LinkedHashMap<String, CatalogEntry> vision = new LinkedHashMap<>();
        for (Map.Entry<String, CatalogEntry> entry : entries.entrySet()) {
            if (TaiModelSpec.isVisionTool(entry.getValue().endpointCapabilities)) {
                vision.put(entry.getKey(), entry.getValue());
            }
        }
        return vision;
    }

    /** Speech-output catalog entries only (the Speech segment's "Voice output" section). */
    @NonNull
    public static Map<String, CatalogEntry> ttsEntries() {
        LinkedHashMap<String, CatalogEntry> tts = new LinkedHashMap<>();
        for (Map.Entry<String, CatalogEntry> entry : entries.entrySet()) {
            if (entry.getValue().endpointCapabilities.contains(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH)) {
                tts.put(entry.getKey(), entry.getValue());
            }
        }
        return tts;
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
                // Remote payloads never add image models: they install through the importer, and a
                // remote entry would have nowhere to download an MNN diffusion directory to yet.
                if (TaiModelSpec.BACKEND_MNN_DIFFUSION.equals(entry.backend)) continue;
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
        // The built-in catalogue is deliberately short: the two Gemma 4 chat models above, the
        // speech models and EmbeddingGemma below. Every other model (Qwen, DeepSeek, FunctionGemma,
        // the MNN packages, other embedding models) still runs when imported or added by link; it
        // just is not offered here, so the model centre stays a list a person can read in one glance.
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
        // NVIDIA Parakeet TDT 0.6B v3 (25 European languages, auto-detected; CC-BY-4.0 weights),
        // Google's int8 stateful LiteRT conversion: one 5 s graph, no window choice, served by
        // ParakeetSttRuntime. The tokenizer.json sidecar comes from NVIDIA's own repo (the
        // conversion repo ships none). Sizes and hashes from the Hugging Face API (LFS sha256 for
        // the graph; the tokenizer is a plain git blob, hashed after download). See
        // project-docs/reference/voice-ai/parakeet-stt-research.md.
        entries.put(PARAKEET_TDT_V3_ID, parakeetAvailable(
            PARAKEET_TDT_V3_ID, "Parakeet TDT 0.6B v3", "Speech-to-text (25 European languages)",
            "litert-community/parakeet-tdt-0.6b-v3", "50dae0cb8c7b39dda477966eff7150cd7fe206ae",
            "parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite", 614_261_072L,
            "334745b8bc7fd372b1c213516f0b6338bb827b1a2abb3e77ad35fe6fea5cd16b",
            "586 MB", "8GB+",
            new CatalogEntry.Sidecar(
                "https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3/resolve/541d1f99c6b0c3cd0b11a95167540bb8edefd82b/tokenizer.json",
                "tokenizer.json", "bd321b096832a3f270bd3b2a88823957920f1a5c5ada71114a26ea729d0cbe91")));
        // KittenTTS nano 0.8 speech output (litert-community/kitten-tts-nano-0.8, Apache-2.0),
        // served by KittenTtsRuntime. The main artifact is the fp32 predictor graph; the prosody
        // and vocoder graphs and voices.npz are sidecars from the same repo, and the GPL-free
        // phonemizer (the Clear BSD OpenPhonemizer dictionary, the MIT DeepPhonemizer graph and its
        // vocabulary) comes from litert-community/Matcha-TTS, which publishes it. fp32 because the
        // model card deploys fp32. sizeBytes is the whole package (94,365,672 bytes), so the space
        // check and the progress bar cover every file; sha256 is the predictor's. Sizes and hashes
        // from the Hugging Face API (LFS sha256; g2p_meta.json is a plain git blob, hashed after
        // download). See scripts/tts-eval/.
        final String kittenRevision = "d4662d891f9bf54b3d93432610d0d296d229e026";
        final String matchaRevision = "8d650e794583c0b0869c87027c2f3a7c293902cb";
        entries.put(KITTEN_TTS_NANO_ID, kittenTtsAvailable(
            KITTEN_TTS_NANO_ID, "KittenTTS Nano 0.8", "Voice output (English)",
            "litert-community/kitten-tts-nano-0.8", kittenRevision,
            KittenTtsRuntime.PREDICTOR_FILE, 94_365_672L,
            "0ca50bbf3c2fa1ba2c779e3851a5d3c8e59dbb68790a6c392d03eff4fac49296",
            "90 MB", "4GB+",
            Arrays.asList(
                hfSidecar("litert-community/kitten-tts-nano-0.8", kittenRevision, KittenTtsRuntime.PROSODY_FILE,
                    "99b90a4ac4f564068d57eab9eff04534dba31d430c2c831a825c81c328324532"),
                hfSidecar("litert-community/kitten-tts-nano-0.8", kittenRevision, KittenTtsRuntime.VOCODER_FILE,
                    "87afb43780fb78434418a143de100740e68d804aa8f45adfdeb0370c85ec4eaa"),
                hfSidecar("litert-community/kitten-tts-nano-0.8", kittenRevision, KittenTtsRuntime.VOICES_FILE,
                    "8aa7cee235abb0739cb51e6559685f65a4dacd95568833d05699b1633f519b3f"),
                hfSidecar("litert-community/Matcha-TTS", matchaRevision, KittenTtsRuntime.PHONEMIZER_FILE,
                    "6e4b481f6874dfabc32ce73bf6f0ea1ba6ab5986ee6f76a27779364be8a53c73"),
                hfSidecar("litert-community/Matcha-TTS", matchaRevision, KittenTtsRuntime.DICTIONARY_FILE,
                    "5b3493a8cd4d20b72c7b91415afaf3f32335ebd81f349698e1cedc898c59f979"),
                hfSidecar("litert-community/Matcha-TTS", matchaRevision, KittenTtsRuntime.PHONEMIZER_META_FILE,
                    "7b87bfeaaa072be236e8491d771b0cb97cc92c3e5d83e3558fff8849868810f5"))));

        // EmbeddingGemma 300M (litert-community/embeddinggemma-300m, Gemma Terms of Use), served on
        // demand by LiteRtEmbeddingRuntime behind /v1/embeddings. Gated on Hugging Face, so the
        // download needs the saved token. The portable mixed-precision graphs only (not the
        // chip-specific ones): seq1024 is the artifact, the smaller seq512/seq256 windows and the
        // SentencePiece tokenizer are hash-pinned sidecars. sizeBytes is the seq1024 graph's; the
        // size estimate covers the whole package. Sizes and hashes from the Hugging Face API.
        final String embeddingGemmaRepo = "litert-community/embeddinggemma-300m";
        final String embeddingGemmaRevision = "29888fcee3216acadc7e844906e5fe0d79a61875";
        entries.put(EMBEDDING_GEMMA_300M_ID, embeddingGemmaAvailable(
            EMBEDDING_GEMMA_300M_ID, "EmbeddingGemma 300M", "Text embeddings (search, memory)",
            embeddingGemmaRepo, embeddingGemmaRevision,
            "embeddinggemma-300M_seq1024_mixed-precision.tflite", 183_329_528L,
            "8b0b8bbd0aa95f9f747c25a6c87cd05a8286933282660f6a50da877662917e31",
            "~520 MB", "4GB+",
            Arrays.asList(
                hfSidecar(embeddingGemmaRepo, embeddingGemmaRevision,
                    "embeddinggemma-300M_seq512_mixed-precision.tflite",
                    "ad09e81557203cb0e177abf9bf8727dfe138a7d394aa0f70f0b2ed16432e121a"),
                hfSidecar(embeddingGemmaRepo, embeddingGemmaRevision,
                    "embeddinggemma-300M_seq256_mixed-precision.tflite",
                    "37115ef7bff76cd37dd86abe503ff511b1032bf85fc624a85c49c84899e92bc5"),
                hfSidecar(embeddingGemmaRepo, embeddingGemmaRevision, "sentencepiece.model",
                    "d6daa52d93d7aad10e8388bd526c4e501d914b47177398d1d9621f1fe48438c7"))));

        // Wallpaper vision graphs for the analysis job (living stills), all LiteRT .tflite run on CPU
        // through XNNPACK by WallpaperVisionRuntime. None is gated; SegFormer is NVIDIA's source-code
        // licence (non-commercial), so the launcher offers it for testing only. Sizes and hashes from
        // the Hugging Face API (LFS sha256), revisions pinned.
        entries.put(DEPTH_ANYTHING_3_SMALL_ID, visionAvailable(
            DEPTH_ANYTHING_3_SMALL_ID, "Depth Anything 3 Small", "Depth for wallpapers (sharper)",
            "litert-community/Depth-Anything-3-Small", "5cd25d936e3fde2edef68f53b4123401454ac9c8",
            "da3_small_gpu_fp16.tflite", "Apache-2.0", 55_035_456L,
            "e170369a72ba1bba7486a4d2de555639fccd0595a9bb5b5349f7733ed4aebd1f",
            "depth-anything-3", "fp16", TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION, "Depth", "55 MB", "4GB+"));
        entries.put(DEPTH_ANYTHING_V2_SMALL_ID, visionAvailable(
            DEPTH_ANYTHING_V2_SMALL_ID, "Depth Anything V2 Small", "Depth for wallpapers (smaller, faster)",
            "litert-community/depth-anything-v2-small", "178427e448dbf4da93b1e7b1b2abc103ad329bd6",
            "tflite/depth_anything_v2_small_wi8_afp32.tflite", "Apache-2.0", 27_733_680L,
            "f74509422e4a9270a354b249a9193abdd4903354be63701262238a7f4b869611",
            "depth-anything-v2", "int8", TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION, "Depth", "28 MB", "4GB+"));
        entries.put(SEGFORMER_B0_ADE20K_ID, visionAvailable(
            SEGFORMER_B0_ADE20K_ID, "SegFormer B0 (ADE20K)", "Scene regions: water, sky, foliage, lights",
            "sollaholla/segformer_b0_ade20k", "1ba929c2bc51ea2bdcc3a7374a504b0e3e5196af",
            "segformer_b0_ade20k.tflite", "NVIDIA source code licence (non-commercial, testing only)", 15_533_492L,
            "59849627a23803db4471eeb61996c77bcfce92dd7347aa8fb9308050941c8cc9",
            "segformer-ade20k", "fp32", TaiModelSpec.CAPABILITY_SCENE_SEGMENTATION, "Scene", "16 MB", "4GB+"));
        entries.put(U2NET_ID, visionAvailable(
            U2NET_ID, "U-2-Net", "Subject cut-out for wallpapers",
            "litert-community/U-2-Net", "defc203955a46f3cc760deb8c76d96f10331a46c",
            "u2net_fp16.tflite", "Apache-2.0", 88_230_272L,
            "dd338f190a538ca3de9792b20b7617038cd56f8e440f38ff25da5554f32b9df2",
            "u2net", "fp16", TaiModelSpec.CAPABILITY_SUBJECT_SEGMENTATION, "Subject", "88 MB", "4GB+"));

        return Collections.unmodifiableMap(entries);
    }

    /** A wallpaper vision entry: one hash-pinned .tflite, no sidecars, one vision capability. */
    private static CatalogEntry visionAvailable(String id, String name, String role, String repo, String revision,
                                                String artifactPath, String license, long size, String sha256,
                                                String architecture, String quantization, String capability,
                                                String tag, String sizeEstimate, String ramTier) {
        return new CatalogEntry(id, name, role, repo, revision, artifactPath, license, size,
            false, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, architecture, quantization,
            128, 128, 128, ramGb(ramTier), sha256, setOf(capability), null, null,
            "wallpaper_vision", "wallpaper_vision", tags(tag), sizeEstimate, ramTier, false, true, "",
            null, null);
    }

    private static CatalogEntry liteRtAvailable(String id, String name, String jobGroup, String priority, boolean recommended,
                                                 String role, String repo, String revision, String file, String license, long size,
                                                 String sizeEstimate, String ramTier, boolean gated, LinkedHashSet<String> displayTags,
                                                 LinkedHashSet<String> capabilities) {
        return entry(id, name, role, repo, revision, file, license, size, gated, TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM, "gemma", null,
            TaiModelSpec.defaultEndpointContextWindowFor(id, TaiModelSpec.BACKEND_LITERT_LM),
            ramGb(ramTier), null, capabilities,
            jobGroup, priority, displayTags, sizeEstimate, ramTier, recommended, true, "");
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

    /** A Parakeet TDT speech-to-text entry: one 5 s graph (its only window variant, so
     *  {@link CatalogEntry#withWindow} is a no-op for any other window), the NVIDIA tokenizer as
     *  its sidecar, {@link ParakeetSttRuntime#ARCHITECTURE} so the router picks that engine. */
    private static CatalogEntry parakeetAvailable(String id, String name, String role, String repo, String revision,
                                                   String artifactPath, long size, String sha256,
                                                   String sizeEstimate, String ramTier,
                                                   CatalogEntry.Sidecar tokenizerSidecar) {
        List<CatalogEntry.Sidecar> sidecars = Collections.singletonList(tokenizerSidecar);
        LinkedHashSet<String> capabilities = setOf(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
        LinkedHashMap<Integer, CatalogEntry.WindowVariant> windows = new LinkedHashMap<>();
        windows.put(5, new CatalogEntry.WindowVariant(5, artifactPath, size, sha256));
        return new CatalogEntry(id, name, role, repo, revision, artifactPath, "CC-BY-4.0", size,
            false, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, ParakeetSttRuntime.ARCHITECTURE, "int8",
            128, 128, 128, ramGb(ramTier), sha256, capabilities, null, null,
            "speech_to_text", "speech_to_text", tags("Speech", "Multilingual"), sizeEstimate, ramTier, false, true, "",
            sidecars, windows);
    }

    /** A speech-output entry: the predictor graph as the artifact, the rest of the package as
     *  sidecars, {@link KittenTtsRuntime#ARCHITECTURE} so the router picks that engine. No window
     *  variants: speech output has no window to choose. */
    private static CatalogEntry kittenTtsAvailable(String id, String name, String role, String repo, String revision,
                                                   String artifactPath, long packageSize, String sha256,
                                                   String sizeEstimate, String ramTier,
                                                   List<CatalogEntry.Sidecar> sidecars) {
        LinkedHashSet<String> capabilities = setOf(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH);
        return new CatalogEntry(id, name, role, repo, revision, artifactPath, "Apache-2.0", packageSize,
            false, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, KittenTtsRuntime.ARCHITECTURE, "fp32",
            128, 128, 128, ramGb(ramTier), sha256, capabilities, null, null,
            "text_to_speech", "text_to_speech", tags("Voice"), sizeEstimate, ramTier, false, true, "",
            sidecars, null);
    }

    /** A text-embedding entry: the widest window graph as the artifact, the smaller windows and the
     *  tokenizer as hash-pinned sidecars. Gated (the Gemma terms), so the download needs the saved
     *  Hugging Face token. Embeddings only, never offered as a chat model or bench pick. */
    private static CatalogEntry embeddingGemmaAvailable(String id, String name, String role, String repo, String revision,
                                                        String artifactPath, long primarySize, String sha256,
                                                        String sizeEstimate, String ramTier,
                                                        List<CatalogEntry.Sidecar> sidecars) {
        LinkedHashSet<String> capabilities = setOf(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);
        return new CatalogEntry(id, name, role, repo, revision, artifactPath, "Gemma", primarySize,
            true, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, TaiImportProfiles.FAMILY_EMBEDDINGGEMMA,
            "mixed-precision", 1024, 1024, 128, ramGb(ramTier), sha256, capabilities, null, null,
            "text_embeddings", "text_embeddings", tags("Embeddings"), sizeEstimate, ramTier, false, true, "",
            sidecars, null);
    }

    /** A sidecar file from a Hugging Face repo at a pinned revision. */
    private static CatalogEntry.Sidecar hfSidecar(String repo, String revision, String file, String sha256) {
        return new CatalogEntry.Sidecar("https://huggingface.co/" + repo + "/resolve/" + revision + "/" + file, file, sha256);
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
        if (capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH)) tags.add("Voice");
        if (capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_GENERATION)) tags.add("Image");
        if (capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT)) tags.add("Vision");
        if (capabilities.contains(TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION)) tags.add("Depth");
        if (capabilities.contains(TaiModelSpec.CAPABILITY_SCENE_SEGMENTATION)) tags.add("Scene");
        if (capabilities.contains(TaiModelSpec.CAPABILITY_SUBJECT_SEGMENTATION)) tags.add("Subject");
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
        /** Speech-only: the window graphs this entry's model id can be downloaded as (Whisper's
         *  5s/10s pair; Parakeet's one 5s graph). Empty for every non-speech entry.
         *  {@link #withWindow} swaps the active artifact. */
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
