package com.termux.ai;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Covers the importer-default gaps recorded in
 * {@code project-docs/gallery-gpu-loading-comparison.md} ("Official defaults (for the TAI
 * importer)"): Gemma 3n E2B/E4B and Gemma3-1B filename matches, the maxTokens-as-context-window
 * mapping, and the {@code ekvNNNN} filename fallback.
 */
public class TaiModelProfileGalleryDefaultsTest {

    private static TaiModelSpec importedSpec(String id, String localPath) {
        return new TaiModelSpec(
            id, id, "chat", "imported", localPath, "user-provided", 1L,
            new LinkedHashSet<>(Collections.singletonList(TaiModelSpec.CAPABILITY_TEXT_CHAT)), false);
    }

    @Test
    public void gemma3nE2B_matchesGalleryCpuFirstDefaults() {
        TaiModelProfile profile = TaiModelProfile.forModel(
            importedSpec("gemma-3n-E2B-it", "/models/gemma-3n-E2B-it/gemma-3n-E2B-it-int4.litertlm"));

        assertEquals(Arrays.asList("cpu", "gpu"), profile.compatibleAccelerators);
        assertEquals(4096, profile.maxContextTokens);
        assertEquals(1024, profile.defaultMaxTokens);
        assertEquals(64, profile.defaultTopK);
        assertEquals(0.95d, profile.defaultTopP, 0.001d);
        assertEquals(1.0d, profile.defaultTemperature, 0.001d);
        assertEquals(Integer.valueOf(8), profile.minDeviceMemoryInGb);
        assertEquals(TaiModelProfile.SOURCE_EDGE_GALLERY_1_0_15, profile.source);
    }

    @Test
    public void gemma3nE4B_matchesGalleryCpuFirstDefaults() {
        TaiModelProfile profile = TaiModelProfile.forModel(
            importedSpec("gemma-3n-E4B-it", "/models/gemma-3n-E4B-it/gemma-3n-E4B-it-int4.litertlm"));

        assertEquals(Arrays.asList("cpu", "gpu"), profile.compatibleAccelerators);
        assertEquals(4096, profile.maxContextTokens);
        assertEquals(1024, profile.defaultMaxTokens);
        assertEquals(Integer.valueOf(12), profile.minDeviceMemoryInGb);
    }

    @Test
    public void gemma31B_matchesGalleryGpuFirstFixedWindow() {
        TaiModelProfile profile = TaiModelProfile.forModel(
            importedSpec("gemma3-1b-it", "/models/gemma3-1b-it/gemma3-1b-it-int4.litertlm"));

        assertEquals(Arrays.asList("gpu", "cpu"), profile.compatibleAccelerators);
        assertEquals(1024, profile.maxContextTokens);
        assertEquals(1024, profile.defaultMaxTokens);
        assertEquals(Integer.valueOf(6), profile.minDeviceMemoryInGb);
    }

    @Test
    public void gemma4Profiles_keepTheCatalogWindowAndTheOutputCap() {
        TaiModelProfile e2b = TaiModelProfile.forModel(
            new TaiModelRegistry().getModel(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT));
        TaiModelProfile e4b = TaiModelProfile.forModel(
            new TaiModelRegistry().getModel(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT));

        // Gallery's 32000 slider is not imposed: the window follows TAI's tiers up to 32768.
        assertEquals(0, e2b.maxContextTokens);
        assertEquals(4000, e2b.defaultMaxTokens);
        assertEquals(0, e4b.maxContextTokens);
        assertEquals(4000, e4b.defaultMaxTokens);
    }

    @Test
    public void deepSeekAndQwenProfiles_mapMaxTokensToContextAndTrimOutputCap() {
        TaiModelProfile deepSeek = TaiModelProfile.forModel(importedSpec("DeepSeek-R1-Distill-Qwen-1.5B",
            "/models/deepseek/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm"));
        TaiModelProfile qwen = TaiModelProfile.forModel(importedSpec("Qwen2.5-1.5B-Instruct",
            "/models/qwen/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"));

        assertEquals(4096, deepSeek.maxContextTokens);
        assertEquals(1024, deepSeek.defaultMaxTokens);
        assertEquals(4096, qwen.maxContextTokens);
        assertEquals(1024, qwen.defaultMaxTokens);
    }

    @Test
    public void unmatchedImportWithEkvToken_usesItAsDefaultContextWindow() {
        TaiModelProfile profile = TaiModelProfile.forModel(
            importedSpec("some-other-model", "/models/some-other-model/some_other_model_q8_ekv2048.litertlm"));

        assertEquals(Collections.singletonList("cpu"), profile.compatibleAccelerators);
        assertEquals(2048, profile.maxContextTokens);
        assertEquals(512, profile.defaultMaxTokens);
        assertEquals("edge-gallery-import-default", profile.source);
    }

    @Test
    public void unmatchedImportWithNoEkvToken_keepsCpuOnly1024AsToday() {
        TaiModelProfile profile = TaiModelProfile.forModel(
            importedSpec("totally-unknown", "/models/totally-unknown/weights.litertlm"));

        assertEquals(Collections.singletonList("cpu"), profile.compatibleAccelerators);
        assertEquals(0, profile.maxContextTokens);
        assertEquals(1024, profile.defaultMaxTokens);
    }

    @Test
    public void extractEkvContext_parsesTokenAndIgnoresAbsence() {
        assertEquals(Integer.valueOf(4096),
            TaiModelProfile.extractEkvContext("/models/x/qwen2.5-1.5b-instruct_multi-prefill-seq_q8_ekv4096.litertlm"));
        assertEquals(Integer.valueOf(1024),
            TaiModelProfile.extractEkvContext("/models/x/tiny_garden_q8_ekv1024.litertlm"));
        assertNull(TaiModelProfile.extractEkvContext("/models/x/no-token-here.litertlm"));
        assertNull(TaiModelProfile.extractEkvContext(null));
    }

    @Test
    public void userRuntimeProfile_stillOverridesEkvDefault() {
        TaiModelProfile userProfile = new TaiModelProfile(Arrays.asList("gpu", "cpu"), 777, 64, 0.95d, 1.0d,
            null, "user-set", TaiModelProfile.THINKING_NONE, null, null, 8192);
        TaiModelSpec spec = new TaiModelSpec(
            "custom-ekv", "custom-ekv", "chat", "imported",
            "/models/custom/custom_q8_ekv2048.litertlm", "user-provided", 1L,
            new LinkedHashSet<>(Collections.singletonList(TaiModelSpec.CAPABILITY_TEXT_CHAT)), false, userProfile);

        TaiModelProfile resolved = TaiModelProfile.forModel(spec);

        assertEquals(8192, resolved.maxContextTokens);
        assertEquals(777, resolved.defaultMaxTokens);
        assertEquals("user-set", resolved.source);
    }
}
