package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

        assertEquals(13, entries.size());
        assertEquals(13, new HashSet<>(entries.keySet()).size());
        assertEquals(6, liteRtCount);
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
        assertFalse(recommended.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));
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
        assertFalse(e4b.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));

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
    public void oldBuiltInIds_migrateToCanonicalYamlIds() {
        assertEquals("gemma-4-e2b-it-litert-lm", TaiSettings.migrateBuiltInModelId("Gemma-4-E2B-it"));
        assertEquals("gemma-4-e4b-it-litert-lm", TaiSettings.migrateBuiltInModelId("Gemma-4-E4B-it"));
        assertEquals("functiongemma-270m-mobile-actions-litert-lm", TaiSettings.migrateBuiltInModelId("MobileActions-270M"));
        assertEquals("user-model", TaiSettings.migrateBuiltInModelId("user-model"));
    }
}
