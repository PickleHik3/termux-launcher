package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TaiModelRegistryTest {

    @Test
    public void registry_containsInitialRecommendedModels() {
        TaiModelRegistry registry = new TaiModelRegistry();

        assertNotNull(registry.getModel(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT));
        assertNotNull(registry.getModel(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT));
        assertNotNull(registry.getModel(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M));
        assertEquals(TaiModelCatalog.entries().size(), registry.getBuiltInModels().size());
    }

    @Test
    public void modelJson_exposesCapabilitiesWithoutSamplingDefaults() throws Exception {
        TaiModelSpec spec = new TaiModelRegistry().getModel(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
        JSONObject json = spec.toJson();
        JSONArray capabilities = json.getJSONArray("capabilities");

        assertEquals("gemma-4-e2b-it-litert-lm", json.getString("id"));
        assertTrue(capabilities.toString().contains("text_chat"));
        assertTrue(capabilities.toString().contains("image_input"));
        assertTrue(json.getJSONArray("endpointCapabilities").toString().contains("image_input"));
        assertTrue(json.getJSONArray("sourceCapabilities").toString().contains("llm_thinking"));
        assertTrue(capabilities.toString().contains("llm_thinking"));
        assertEquals(4096, json.getInt("endpointContextWindow"));
        assertEquals(32768, json.getInt("sourceContextWindow"));
        assertEquals(4000, json.getInt("defaultMaxOutputTokens"));
        assertTrue(json.isNull("localPath"));
        assertTrue(!json.has("temperature"));
        assertTrue(!json.has("topK"));
        assertTrue(!json.has("topP"));
        assertTrue(!json.has("maxTokens"));
    }

    @Test
    public void modelSpec_roundTripsImportedModelMetadata() throws Exception {
        TaiModelSpec spec = new TaiModelSpec(
            "local-test",
            "Local Test",
            "Imported local model",
            "imported",
            "/tmp/local-test.task",
            "User accepted license externally",
            1234L,
            new java.util.LinkedHashSet<>(java.util.Arrays.asList("text_chat", "tool_use")),
            false
        );

        TaiModelSpec roundTrip = TaiModelSpec.fromJson(spec.toJson());

        assertEquals("local-test", roundTrip.id);
        assertEquals("Local Test", roundTrip.displayName);
        assertEquals("imported", roundTrip.source);
        assertEquals("/tmp/local-test.task", roundTrip.localPath);
        assertEquals(1234L, roundTrip.sizeBytes);
        assertTrue(!roundTrip.builtInCatalogEntry);
        assertTrue(roundTrip.capabilities.contains("text_chat"));
        assertTrue(roundTrip.capabilities.contains("tool_use"));
    }

    @Test
    public void modelSpec_infersEdgeGalleryProfileForExistingMobileActionsMetadata() throws Exception {
        JSONObject persisted = new JSONObject();
        persisted.put("id", "MobileActions-270M");
        persisted.put("displayName", "MobileActions-270M");
        persisted.put("localPath", "/models/MobileActions-270M/mobile_actions_q8_ekv1024.litertlm");

        TaiModelSpec spec = TaiModelSpec.fromJson(persisted);
        TaiModelProfile profile = TaiModelProfile.forModel(spec);

        assertEquals(java.util.Collections.singletonList("cpu"), profile.compatibleAccelerators);
        assertEquals(1024, profile.defaultMaxTokens);
        assertEquals(0.0d, profile.defaultTemperature, 0.001d);
        assertEquals(Integer.valueOf(6), profile.minDeviceMemoryInGb);
        assertEquals(TaiModelProfile.SOURCE_EDGE_GALLERY_1_0_15, profile.source);
    }

    @Test
    public void modelSpec_usesEdgeGalleryDefaultsForGemma4Models() {
        TaiModelProfile e2b = TaiModelProfile.forModel(
            new TaiModelRegistry().getModel(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT));
        TaiModelProfile e4b = TaiModelProfile.forModel(
            new TaiModelRegistry().getModel(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT));

        assertEquals(java.util.Arrays.asList("gpu", "cpu"), e2b.compatibleAccelerators);
        assertEquals(4000, e2b.defaultMaxTokens);
        assertEquals(Integer.valueOf(8), e2b.minDeviceMemoryInGb);
        assertEquals(Integer.valueOf(12), e4b.minDeviceMemoryInGb);
    }

    @Test
    public void importedUnknownModel_defaultsToCpuLikeEdgeGalleryImport() throws Exception {
        TaiModelSpec imported = new TaiModelSpec(
            "unknown-local",
            "Unknown Local",
            "Imported model",
            "imported",
            "/tmp/unknown.litertlm",
            "user-provided",
            100L,
            java.util.Collections.singleton("text_chat"),
            false
        );

        TaiModelSpec roundTrip = TaiModelSpec.fromJson(imported.toJson());

        assertEquals(java.util.Collections.singletonList("cpu"),
            TaiModelProfile.forModel(roundTrip).compatibleAccelerators);
    }

    @Test
    public void qwen3ThinkingProfile_matchesProviderGpuAndThinkingDefaults() throws Exception {
        TaiModelSpec imported = new TaiModelSpec(
            "Qwen3-4B-Thinking-2507", "Qwen3 Thinking", "chat", "imported",
            "/models/Qwen3-4B-Thinking-2507/model.litertlm", "provider", 1L,
            java.util.Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT), false);

        TaiModelProfile profile = TaiModelProfile.forModel(imported);

        assertEquals(java.util.Arrays.asList("gpu", "cpu"), profile.compatibleAccelerators);
        assertEquals(2048, profile.defaultMaxTokens);
        assertEquals(TaiModelProfile.THINKING_ALWAYS, profile.thinkingMode);
        assertEquals("<think>", profile.thinkingChannelStart);
        assertEquals("</think>", profile.thinkingChannelEnd);
    }

    @Test
    public void profileRequestWithNullableFallbackMemory_doesNotUnboxNull() throws Exception {
        TaiModelProfile fallback = new TaiModelProfile(java.util.Collections.singletonList("cpu"),
            1024, 64, 0.95d, 1.0d, null, "test");

        TaiModelProfile parsed = TaiModelProfile.fromRequest(new JSONObject(), fallback);

        assertEquals(null, parsed.minDeviceMemoryInGb);
    }

    @Test
    public void liteRtThinkingRequest_usesNativeExtraContextAndChannel() {
        TaiModelProfile always = TaiModelProfile.qwen3Thinking2507Profile();

        java.util.Map<String, Object> extra = LiteRtTaiRuntime.thinkingExtraContext(null, always);

        assertEquals("true", extra.get("enable_thinking"));
        assertEquals(1, LiteRtTaiRuntime.thinkingChannels(always).size());
    }

    @Test
    public void importedModel_acceptsExplicitGalleryStyleRuntimeProfile() throws Exception {
        JSONObject request = new JSONObject();
        request.put("compatibleAccelerators", new JSONArray().put("gpu").put("cpu"));
        request.put("defaultMaxTokens", 2048);
        request.put("defaultTopK", 32);
        request.put("defaultTopP", 0.8d);
        request.put("defaultTemperature", 0.7d);
        request.put("minDeviceMemoryInGb", 8);

        TaiModelProfile profile = TaiModelProfile.fromRequest(request,
            new TaiModelProfile(java.util.Collections.singletonList("cpu"), 1024, 64, 0.95d, 1.0d, null, "fallback"));
        TaiModelProfile roundTrip = TaiModelProfile.fromJson(profile.toJson());

        assertEquals(java.util.Arrays.asList("gpu", "cpu"), roundTrip.compatibleAccelerators);
        assertEquals(2048, roundTrip.defaultMaxTokens);
        assertEquals(32, roundTrip.defaultTopK);
        assertEquals(0.8d, roundTrip.defaultTopP, 0.001d);
        assertEquals(0.7d, roundTrip.defaultTemperature, 0.001d);
        assertEquals(Integer.valueOf(8), roundTrip.minDeviceMemoryInGb);
    }

    @Test
    public void runtimeOptions_exposeIdleUnloadAndRequestOverrides() throws Exception {
        TaiRuntimeOptions options = new TaiRuntimeOptions(
            null,
            64,
            0.95d,
            null,
            null,
            null,
            true,
            10
        ).withGenerationOverrides(512, null, null, 0.7d, "cpu", null, null);

        JSONObject json = options.toJson();

        assertEquals(512, json.getInt("maxTokens"));
        assertEquals(64, json.getInt("topK"));
        assertEquals(0.95d, json.getDouble("topP"), 0.001d);
        assertEquals(0.7d, json.getDouble("temperature"), 0.001d);
        assertEquals("cpu", json.getString("accelerator"));
        assertTrue(json.getBoolean("speculativeDecodingEnabled"));
        assertEquals(10, json.getInt("idleUnloadMinutes"));
    }

    @Test
    public void runtimeStateJson_exposesLifecycleAndTimers() throws Exception {
        TaiRuntimeState state = new TaiRuntimeState(
            true,
            "local-test",
            "litert-lm",
            "idle-warm",
            "Model loaded.",
            "gpu",
            "Auto selected GPU first.",
            "/tmp/local-test.litertlm",
            false,
            null,
            0L,
            System.currentTimeMillis() + 60_000L,
            System.currentTimeMillis() + 120_000L,
            100L,
            200L
        );

        JSONObject json = state.toJson();

        assertTrue(json.getBoolean("loaded"));
        assertEquals("local-test", json.getString("loadedModelId"));
        assertEquals("idle-warm", json.getString("state"));
        assertEquals("gpu", json.getString("backend"));
        assertTrue(json.getLong("keepWarmRemainingMs") > 0L);
        assertTrue(json.getLong("idleUnloadRemainingMs") > 0L);
    }

    @Test
    public void apiEndpointSettings_validateStablePortAndTokenValues() {
        assertEquals(41237, TaiSettings.normalizeApiPort("41237"));
        assertEquals(TaiSettings.DEFAULT_API_PORT, TaiSettings.normalizeApiPort("bad-port"));
        assertTrue(TaiSettings.isValidApiPort("49152"));
        assertTrue(!TaiSettings.isValidApiPort("80"));
        assertTrue(TaiSettings.isValidApiToken("1234567890abcdef"));
        assertTrue(!TaiSettings.isValidApiToken("short"));
        assertTrue(!TaiSettings.isValidApiToken("12345678 90abcdef"));
    }

}
