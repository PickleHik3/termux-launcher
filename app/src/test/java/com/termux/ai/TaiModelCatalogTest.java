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

    @Test
    public void builtInCatalog_matchesYamlModelCountsAndUniqueIds() {
        Map<String, TaiModelCatalog.CatalogEntry> entries = TaiModelCatalog.entries();
        int liteRtCount = 0;
        int mnnCount = 0;

        for (TaiModelCatalog.CatalogEntry entry : entries.values()) {
            if (TaiModelSpec.BACKEND_LITERT_LM.equals(entry.backend)) liteRtCount++;
            if (TaiModelSpec.BACKEND_MNN_LLM.equals(entry.backend)) mnnCount++;
        }

        assertEquals(18, entries.size());
        assertEquals(18, new HashSet<>(entries.keySet()).size());
        assertEquals(11, liteRtCount);
        assertEquals(7, mnnCount);
    }

    @Test
    public void builtInCatalog_usesCanonicalYamlIdsAndUiMetadata() {
        TaiModelCatalog.CatalogEntry recommended = TaiModelCatalog.get("gemma-4-e2b-it-litert-lm");
        TaiModelCatalog.CatalogEntry coder = TaiModelCatalog.get("qwen2.5-coder-1.5b-instruct-mnn");

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

        assertNotNull(coder);
        assertEquals("coding", coder.jobGroup);
        assertEquals("int4", coder.quantization);
        assertEquals("4GB-6GB+", coder.ramTier);
        assertTrue(coder.recommended);
        assertTrue(coder.displayCapabilityTags.contains("Code"));
        assertTrue(coder.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        assertTrue(coder.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_CODE));
        assertTrue(coder.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_TOOL_USE));
        assertFalse(coder.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        assertFalse(coder.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertFalse(coder.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));
        assertEquals(TaiModelSpec.TOOL_MODE_PROMPT_FALLBACK, coder.toolMode);
        assertEquals(16384, coder.endpointContextWindow);
        assertEquals(32768, coder.sourceContextWindow);
        assertEquals(1024, coder.defaultMaxOutputTokens);
    }

    @Test
    public void builtInCatalog_correctsModelSpecificEndpointMetadata() {
        TaiModelCatalog.CatalogEntry e4b = TaiModelCatalog.get(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT);
        TaiModelCatalog.CatalogEntry mobileActions = TaiModelCatalog.get(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M);

        assertNotNull(e4b);
        assertEquals("3.7 GB", e4b.sizeEstimate);
        assertTrue(e4b.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertTrue(e4b.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertTrue(e4b.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_TOOL_USE));
        assertTrue(e4b.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_CODE));
        assertTrue(e4b.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_SPECULATIVE_DECODING));
        assertTrue(e4b.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));

        assertNotNull(mobileActions);
        assertEquals("Mobile actions tool-call model", mobileActions.roleHint);
        assertEquals(1024, mobileActions.endpointContextWindow);
        assertEquals(1024, mobileActions.sourceContextWindow);
        assertEquals(1024, mobileActions.defaultMaxOutputTokens);
        assertEquals(6, mobileActions.recommendedRamGb);
        assertEquals("6GB+", mobileActions.ramTier);
        assertTrue(mobileActions.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_TOOL_USE));
        assertTrue(mobileActions.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_MOBILE_ACTIONS));
        assertEquals(TaiModelSpec.TOOL_MODE_NATIVE, mobileActions.toolMode);
    }

    @Test
    public void builtInCatalog_gatesDownloadsWithoutVerifiedArtifactMetadata() {
        TaiModelCatalog.CatalogEntry knownArtifact = TaiModelCatalog.get(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
        TaiModelCatalog.CatalogEntry importOnlyLiteRt = TaiModelCatalog.get("qwen2.5-1.5b-instruct-litert-lm");
        TaiModelCatalog.CatalogEntry mnn = TaiModelCatalog.get("qwen2.5-coder-1.5b-instruct-mnn");

        assertNotNull(knownArtifact);
        assertTrue(knownArtifact.downloadAvailable);
        assertNotNull(knownArtifact.artifactPath);
        assertNotNull(knownArtifact.downloadUrl);

        assertNotNull(importOnlyLiteRt);
        assertFalse(importOnlyLiteRt.downloadAvailable);
        assertNull(importOnlyLiteRt.artifactPath);
        assertNull(importOnlyLiteRt.downloadUrl);
        assertTrue(importOnlyLiteRt.unavailableReason.contains("Import-only"));

        assertNotNull(mnn);
        assertTrue(mnn.downloadAvailable);
        assertEquals("taobao-mnn/Qwen2.5-Coder-1.5B-Instruct-MNN", mnn.repositoryId);
        assertNotNull(mnn.downloadUrl);
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
        assertEquals(TaiModelCatalog.entries().size() - speech.size(), chat.size());
    }

    @Test
    public void oldBuiltInIds_migrateToCanonicalYamlIds() {
        assertEquals("gemma-4-e2b-it-litert-lm", TaiSettings.migrateBuiltInModelId("Gemma-4-E2B-it"));
        assertEquals("gemma-4-e4b-it-litert-lm", TaiSettings.migrateBuiltInModelId("Gemma-4-E4B-it"));
        assertEquals("functiongemma-270m-mobile-actions-litert-lm", TaiSettings.migrateBuiltInModelId("MobileActions-270M"));
        assertEquals("user-model", TaiSettings.migrateBuiltInModelId("user-model"));
    }
}
