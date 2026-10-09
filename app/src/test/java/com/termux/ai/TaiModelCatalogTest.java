package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TaiModelCatalogTest {

    /** D1 of the model-centre design: only the two Gemma 4 chat models, the speech models, the
     *  voice model and EmbeddingGemma are built in. Everything else still runs when imported or added by link. */
    @Test
    public void builtInCatalog_isGemmaPlusSpeechOnly() {
        Map<String, TaiModelCatalog.CatalogEntry> entries = TaiModelCatalog.entries();
        int liteRtCount = 0;
        int mnnCount = 0;

        for (TaiModelCatalog.CatalogEntry entry : entries.values()) {
            if (TaiModelSpec.BACKEND_LITERT_LM.equals(entry.backend)) liteRtCount++;
            if (TaiModelSpec.BACKEND_MNN_LLM.equals(entry.backend)) mnnCount++;
        }

        assertEquals(11, entries.size());
        assertEquals(11, new HashSet<>(entries.keySet()).size());
        assertEquals(11, liteRtCount);
        assertEquals(0, mnnCount);
        assertTrue(entries.containsKey(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT));
        assertTrue(entries.containsKey(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT));
        assertEquals(2, TaiModelCatalog.chatEntries().size());
        assertEquals(5, TaiModelCatalog.speechEntries().size());
        assertEquals(1, TaiModelCatalog.ttsEntries().size());
        assertEquals(3, TaiModelCatalog.embeddingEntries().size());
        assertNull(TaiModelCatalog.get("qwen2.5-coder-1.5b-instruct-mnn"));
        assertNull(TaiModelCatalog.get("deepseek-r1-distill-qwen-1.5b-litert-lm"));
        assertNull(TaiModelCatalog.get(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M));
        assertNotNull(TaiModelCatalog.get("embeddinggemma-300m"));
        assertNull(TaiModelCatalog.get("qwen3-embedding-0.6b-mnn"));
    }

    @Test
    public void builtInCatalog_usesCanonicalYamlIdsAndUiMetadata() {
        TaiModelCatalog.CatalogEntry recommended = TaiModelCatalog.get("gemma-4-e2b-it-litert-lm");

        assertNotNull(recommended);
        assertEquals("general_multimodal", recommended.jobGroup);
        assertEquals("recommended_default", recommended.priority);
        assertEquals("2.4 GB", recommended.sizeEstimate);
        assertEquals("8GB+", recommended.ramTier);
        assertTrue(recommended.recommended);
        assertTrue(recommended.displayCapabilityTags.contains("Vision"));
        assertTrue(recommended.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        assertTrue(recommended.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertTrue(recommended.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));
        assertTrue(recommended.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));
        assertEquals(4096, recommended.endpointContextWindow);
        assertEquals(32768, recommended.sourceContextWindow);
        assertEquals(4000, recommended.defaultMaxOutputTokens);

    }

    @Test
    public void builtInCatalog_correctsModelSpecificEndpointMetadata() {
        TaiModelCatalog.CatalogEntry e4b = TaiModelCatalog.get(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT);

        assertNotNull(e4b);
        assertEquals("3.7 GB", e4b.sizeEstimate);
        assertTrue(e4b.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertTrue(e4b.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertTrue(e4b.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_TOOL_USE));
        assertTrue(e4b.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_CODE));
        assertTrue(e4b.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_SPECULATIVE_DECODING));
        assertTrue(e4b.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));

    }

    @Test
    public void builtInCatalog_everyEntryHasAVerifiedArtifact() {
        // With the import-only rows gone, every built-in entry must be downloadable as is: a pinned
        // revision, an artifact path and a URL, so the model centre never shows a dead Install pill.
        for (TaiModelCatalog.CatalogEntry entry : TaiModelCatalog.entries().values()) {
            assertTrue(entry.modelId, entry.downloadAvailable);
            assertNotNull(entry.modelId, entry.artifactPath);
            assertNotNull(entry.modelId, entry.downloadUrl);
            assertTrue(entry.modelId, entry.downloadUrl.startsWith("https://huggingface.co/"));
            assertFalse(entry.modelId, "main".equals(entry.revision));
        }
    }

    @Test
    public void downloadEntry_buildsCatalogRowForActiveCustomDownload() throws Exception {
        JSONObject download = new JSONObject()
            .put("modelId", "custom-embedding-model")
            .put("displayName", "Custom Embedding Model")
            .put("url", "https://huggingface.co/litert-community/embeddinggemma-300m/resolve/main/model.tflite")
            .put("path", "/models/embeddinggemma-300m/model.tflite")
            .put("status", TaiModelStore.STATE_DOWNLOADING)
            .put("backend", TaiModelSpec.BACKEND_LITERT_LM)
            .put("format", TaiModelSpec.FORMAT_LITERTLM)
            .put("totalBytes", 123L)
            .put("capabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));

        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.downloadEntry(download);

        assertNotNull(entry);
        assertEquals("custom-embedding-model", entry.modelId);
        assertEquals("Custom Embedding Model", entry.displayName);
        assertEquals("litert-community/embeddinggemma-300m", entry.repositoryId);
        assertTrue(entry.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));
        assertTrue(entry.displayCapabilityTags.contains("Embeddings"));
    }

    @Test
    public void whisperAcft_catalogEntriesHaveIdsUrlsSizesSidecarsAndCapability() {
        String[] ids = {"whisper-acft-base", "whisper-acft-base-en", "whisper-acft-small", "whisper-acft-small-en"};
        for (String id : ids) {
            TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(id);
            assertNotNull("missing catalog entry: " + id, entry);
            assertEquals(TaiModelSpec.BACKEND_LITERT_LM, entry.backend);
            assertEquals(TaiModelSpec.FORMAT_LITERTLM, entry.format);
            assertTrue(entry.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT));
            assertTrue(entry.displayCapabilityTags.contains("Speech"));
            assertTrue(entry.downloadAvailable);
            assertTrue(entry.sizeBytes > 0);
            assertNotNull(entry.downloadUrl);
            assertTrue(entry.downloadUrl.startsWith("https://huggingface.co/litert-community/whisper-acft/resolve/"));
            assertNotNull(entry.sha256);

            // Default artifact is the 10s window graph.
            assertTrue(entry.artifactPath.endsWith("_10s_drq.tflite"));

            // Tokenizer sidecar from the paired openai/whisper-{size}{.en} repo.
            assertEquals(1, entry.sidecars.size());
            TaiModelCatalog.CatalogEntry.Sidecar sidecar = entry.sidecars.get(0);
            assertEquals("tokenizer.json", sidecar.localName);
            assertTrue(sidecar.url.startsWith("https://huggingface.co/openai/whisper-"));
            assertTrue(sidecar.url.endsWith("/tokenizer.json"));
            assertNotNull(sidecar.sha256);

            // Both window variants are known, and withWindow(5) swaps to the 5s graph while keeping
            // the id, capability and sidecar untouched.
            assertEquals(2, entry.speechWindows.size());
            TaiModelCatalog.CatalogEntry fast = entry.withWindow(5);
            assertEquals(entry.modelId, fast.modelId);
            assertTrue(fast.artifactPath.endsWith("_5s_drq.tflite"));
            assertNotEquals(entry.sizeBytes, fast.sizeBytes);
            assertNotEquals(entry.sha256, fast.sha256);
            assertEquals(entry.sidecars, fast.sidecars);
            assertTrue(fast.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT));
        }

        // English-only ids point at the matching .en tokenizer repo; multilingual ids don't.
        assertTrue(TaiModelCatalog.get("whisper-acft-base-en").sidecars.get(0).url.contains("whisper-base.en/"));
        assertTrue(TaiModelCatalog.get("whisper-acft-small-en").sidecars.get(0).url.contains("whisper-small.en/"));
        assertFalse(TaiModelCatalog.get("whisper-acft-base").sidecars.get(0).url.contains(".en/"));
        assertFalse(TaiModelCatalog.get("whisper-acft-small").sidecars.get(0).url.contains(".en/"));

        // withWindow ignores an unknown window and returns the entry unchanged.
        TaiModelCatalog.CatalogEntry base = TaiModelCatalog.get("whisper-acft-base");
        assertEquals(base.artifactPath, base.withWindow(30).artifactPath);

        // A non-speech entry has no window variants and withWindow is a no-op.
        TaiModelCatalog.CatalogEntry chatEntry = TaiModelCatalog.get(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
        assertTrue(chatEntry.speechWindows.isEmpty());
        assertTrue(chatEntry.sidecars.isEmpty());
        assertEquals(chatEntry.artifactPath, chatEntry.withWindow(5).artifactPath);
    }

    @Test
    public void parakeet_catalogEntryPinsTheGraphTheTokenizerAndTheOneWindow() {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(TaiModelCatalog.PARAKEET_TDT_V3_ID);
        assertNotNull(entry);
        assertEquals("parakeet-tdt-0.6b-v3", entry.modelId);
        assertEquals(TaiModelSpec.BACKEND_LITERT_LM, entry.backend);
        assertEquals(TaiModelSpec.FORMAT_LITERTLM, entry.format);
        assertEquals("parakeet-tdt", entry.architecture);
        assertEquals("CC-BY-4.0", entry.license);
        assertEquals(8, entry.recommendedRamGb);
        assertTrue(entry.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT));
        assertTrue(entry.displayCapabilityTags.contains("Speech"));
        assertTrue(entry.downloadAvailable);
        assertEquals(614_261_072L, entry.sizeBytes);
        assertEquals("334745b8bc7fd372b1c213516f0b6338bb827b1a2abb3e77ad35fe6fea5cd16b", entry.sha256);
        assertEquals("https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3/resolve/50dae0cb8c7b39dda477966eff7150cd7fe206ae/"
            + "parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite?download=true", entry.downloadUrl);
        // The tokenizer comes from NVIDIA's own repo at a pinned revision, next to the graph.
        assertEquals(1, entry.sidecars.size());
        TaiModelCatalog.CatalogEntry.Sidecar sidecar = entry.sidecars.get(0);
        assertEquals("tokenizer.json", sidecar.localName);
        assertEquals("https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3/resolve/541d1f99c6b0c3cd0b11a95167540bb8edefd82b/tokenizer.json", sidecar.url);
        assertEquals("bd321b096832a3f270bd3b2a88823957920f1a5c5ada71114a26ea729d0cbe91", sidecar.sha256);
        // One 5 s graph: asking for any other window keeps it.
        assertEquals(1, entry.speechWindows.size());
        assertTrue(entry.speechWindows.containsKey(5));
        assertEquals(entry.artifactPath, entry.withWindow(10).artifactPath);
        assertEquals(entry.artifactPath, entry.withWindow(5).artifactPath);
        assertEquals(5, TaiSpeechModels.windowSeconds("/m/" + entry.modelId + "/" + entry.artifactPath));
    }

    @Test
    public void chatEntries_excludeSpeechToTextAndSpeechEntries_containOnlyThem() {
        Map<String, TaiModelCatalog.CatalogEntry> chat = TaiModelCatalog.chatEntries();
        Map<String, TaiModelCatalog.CatalogEntry> speech = TaiModelCatalog.speechEntries();

        assertFalse(chat.containsKey("whisper-acft-base"));
        assertFalse(chat.containsKey("whisper-acft-base-en"));
        assertFalse(chat.containsKey("whisper-acft-small"));
        assertFalse(chat.containsKey("whisper-acft-small-en"));
        assertTrue(chat.containsKey(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT));

        assertEquals(5, speech.size());
        assertFalse(chat.containsKey(TaiModelCatalog.PARAKEET_TDT_V3_ID));
        assertTrue(speech.containsKey(TaiModelCatalog.PARAKEET_TDT_V3_ID));
        for (TaiModelCatalog.CatalogEntry entry : speech.values()) {
            assertTrue(entry.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT));
        }
        assertEquals(TaiModelCatalog.entries().size() - speech.size() - TaiModelCatalog.ttsEntries().size()
            - TaiModelCatalog.embeddingEntries().size(), chat.size());
    }

    @Test
    public void embeddingGemma_isAGatedEmbeddingsOnlyEntryWithTheWindowsAndTokenizerPinned() {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID);
        assertNotNull(entry);
        assertEquals("embeddinggemma-300m", entry.modelId);
        assertEquals("litert-community/embeddinggemma-300m", entry.repositoryId);
        assertEquals("29888fcee3216acadc7e844906e5fe0d79a61875", entry.revision);
        assertEquals("embeddinggemma-300M_seq1024_mixed-precision.tflite", entry.artifactPath);
        assertEquals("Gemma", entry.license);
        assertTrue(entry.gated);
        assertFalse(entry.recommended);
        assertTrue(entry.downloadAvailable);
        assertEquals(183_329_528L, entry.sizeBytes);
        assertEquals(64, entry.sha256.length());
        assertEquals(TaiImportProfiles.FAMILY_EMBEDDINGGEMMA, entry.architecture);
        assertEquals(java.util.Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS), entry.endpointCapabilities);
        assertTrue(entry.downloadUrl.contains("/resolve/29888fcee3216acadc7e844906e5fe0d79a61875/"));

        java.util.List<String> names = new java.util.ArrayList<>();
        for (TaiModelCatalog.CatalogEntry.Sidecar sidecar : entry.sidecars) {
            names.add(sidecar.localName);
            assertNotNull(sidecar.localName, sidecar.sha256);
            assertTrue(sidecar.localName, sidecar.sha256.matches("[0-9a-f]{64}"));
            assertTrue(sidecar.url.startsWith(
                "https://huggingface.co/litert-community/embeddinggemma-300m/resolve/29888fcee3216acadc7e844906e5fe0d79a61875/"));
        }
        assertEquals(java.util.Arrays.asList(
            "embeddinggemma-300M_seq512_mixed-precision.tflite",
            "embeddinggemma-300M_seq256_mixed-precision.tflite",
            "sentencepiece.model"), names);
    }

    @Test
    public void embeddingGemma2_entriesArePinnedUngatedLiteRtLmBundlesWithNoSidecars() {
        TaiModelCatalog.CatalogEntry big = TaiModelCatalog.get(TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_VISION_440M_ID);
        assertNotNull(big);
        assertEquals("EmbeddingGemma 2 Text+Vision 440M", big.displayName);
        assertEquals("litert-community/embeddinggemma-2-text-vision-440m-litert-lm", big.repositoryId);
        assertEquals("e301f74d5551b0c2641bd5cb4652a76239d5c5f8", big.revision);
        assertEquals("embeddinggemma-2-text-vision-440m.litertlm", big.artifactPath);
        assertEquals(387_710_976L, big.sizeBytes);
        assertEquals("92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0", big.sha256);
        assertEquals("text_embeddings", big.priority);
        assertTrue(big.recommended);
        assertTrue(big.displayCapabilityTags.contains("Vision"));

        TaiModelCatalog.CatalogEntry small = TaiModelCatalog.get(TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_270M_ID);
        assertNotNull(small);
        assertEquals("EmbeddingGemma 2 Text 270M", small.displayName);
        assertEquals("litert-community/embeddinggemma-2-text-270m-litert-lm", small.repositoryId);
        assertEquals("9be6e8b90982095dc05c2bd162e4b954ee4dbac7", small.revision);
        assertEquals("embeddinggemma-2-text-270m.litertlm", small.artifactPath);
        assertEquals(164_626_432L, small.sizeBytes);
        assertEquals("2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb", small.sha256);
        assertFalse(small.recommended);

        for (TaiModelCatalog.CatalogEntry entry : new TaiModelCatalog.CatalogEntry[] {big, small}) {
            assertEquals("Apache-2.0", entry.license);
            assertFalse(entry.gated);
            assertTrue(entry.downloadAvailable);
            assertEquals(TaiModelSpec.BACKEND_LITERT_LM, entry.backend);
            assertEquals(TaiImportProfiles.FAMILY_EMBEDDINGGEMMA, entry.architecture);
            assertEquals("int4", entry.quantization);
            assertEquals(2048, entry.endpointContextWindow);
            assertTrue(entry.sidecars.isEmpty());
            assertEquals(java.util.Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS), entry.endpointCapabilities);
            assertTrue(TaiModelCatalog.embeddingEntries().containsKey(entry.modelId));
            assertFalse(TaiModelCatalog.chatEntries().containsKey(entry.modelId));
        }

        // The 440M leads the Embeddings section; the 300M stays listed, after both EmbeddingGemma 2 entries.
        java.util.List<String> order = new java.util.ArrayList<>(TaiModelCatalog.embeddingEntries().keySet());
        assertEquals(java.util.Arrays.asList(TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_VISION_440M_ID,
            TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_270M_ID, TaiModelCatalog.EMBEDDING_GEMMA_300M_ID), order);
        assertEquals("text_embeddings_legacy", TaiModelCatalog.get(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID).priority);
    }

    @Test
    public void embeddingGemma_isListedAsAnEmbedderAndNeverAsAChatModel() {
        assertTrue(TaiModelCatalog.embeddingEntries().containsKey(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID));
        assertFalse(TaiModelCatalog.chatEntries().containsKey(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID));
        assertFalse(TaiModelCatalog.speechEntries().containsKey(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID));
        assertFalse(TaiModelCatalog.ttsEntries().containsKey(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID));
        assertFalse(TaiModelCatalog.embeddingEntries().containsKey(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT));
    }

    @Test
    public void kittenTts_isOneVoiceEntryWithTheWholePackagePinnedAndHashed() {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(TaiModelCatalog.KITTEN_TTS_NANO_ID);
        assertNotNull(entry);
        assertEquals("litert-community/kitten-tts-nano-0.8", entry.repositoryId);
        assertEquals("d4662d891f9bf54b3d93432610d0d296d229e026", entry.revision);
        assertEquals(KittenTtsRuntime.PREDICTOR_FILE, entry.artifactPath);
        assertEquals("0ca50bbf3c2fa1ba2c779e3851a5d3c8e59dbb68790a6c392d03eff4fac49296", entry.sha256);
        assertEquals(KittenTtsRuntime.ARCHITECTURE, entry.architecture);
        assertEquals("Apache-2.0", entry.license);
        // The whole package, so the space check and the progress bar cover every file.
        assertEquals(94_365_672L, entry.sizeBytes);
        assertTrue(entry.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH));
        assertFalse(entry.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        assertFalse(entry.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT));
        assertTrue(entry.speechWindows.isEmpty());
        assertEquals(KittenTtsRuntime.SIDECAR_FILES.length, entry.sidecars.size());
        for (int i = 0; i < entry.sidecars.size(); i++) {
            TaiModelCatalog.CatalogEntry.Sidecar sidecar = entry.sidecars.get(i);
            assertEquals(KittenTtsRuntime.SIDECAR_FILES[i], sidecar.localName);
            assertNotNull(sidecar.localName, sidecar.sha256);
            assertEquals(sidecar.localName, 64, sidecar.sha256.length());
            assertTrue(sidecar.url, sidecar.url.startsWith("https://huggingface.co/litert-community/"));
            assertFalse(sidecar.url, sidecar.url.contains("/resolve/main/"));
            assertTrue(sidecar.url, sidecar.url.endsWith("/" + sidecar.localName));
        }
        assertFalse(TaiModelCatalog.chatEntries().containsKey(entry.modelId));
        assertFalse(TaiModelCatalog.speechEntries().containsKey(entry.modelId));
        assertTrue(TaiModelCatalog.ttsEntries().containsKey(entry.modelId));
    }

    @Test
    public void theWallpaperVisionModelsAreNotInTheCatalogue() {
        for (String id : new String[] {"depth-anything-3-small", "depth-anything-v2-small", "segformer-b0-ade20k", "u2net"}) {
            assertNull(id, TaiModelCatalog.get(id));
        }
    }

    @Test
    public void oldBuiltInIds_migrateToCanonicalYamlIds() {
        assertEquals("gemma-4-e2b-it-litert-lm", TaiSettings.migrateBuiltInModelId("Gemma-4-E2B-it"));
        assertEquals("gemma-4-e4b-it-litert-lm", TaiSettings.migrateBuiltInModelId("Gemma-4-E4B-it"));
        assertEquals("functiongemma-270m-mobile-actions-litert-lm", TaiSettings.migrateBuiltInModelId("MobileActions-270M"));
        assertEquals("user-model", TaiSettings.migrateBuiltInModelId("user-model"));
    }
}
