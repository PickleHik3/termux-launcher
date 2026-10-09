package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Family defaults for the litert-community repositories, against the file names the Hugging Face
 * API listed for each on 2026-09-27 ({@code /api/models/litert-community/<repo>?blobs=true}).
 */
public class TaiImportProfilesTest {
    private static final String SHA = "0123456789012345678901234567890123456789";

    private static TaiImportProfiles.Match match(String repo, String file) {
        TaiImportProfiles.Match match = TaiImportProfiles.match("litert-community/" + repo, file);
        assertNotNull(repo + "/" + file, match);
        return match;
    }

    private static TaiRuntimeOptions thinking(Boolean enabled) {
        return new TaiRuntimeOptions(null, null, null, null, null, enabled, null, null);
    }

    @Test
    public void smolLm3PrefersTheGpuForTheQ4BuildAndCapsItsWindow() {
        TaiImportProfiles.Match q4 = match("SmolLM3-3B", "SmolLM3-3B_q4_block32_ekv4096.litertlm");
        assertEquals(TaiImportProfiles.FAMILY_SMOLLM3, q4.family);
        assertEquals(Arrays.asList("gpu", "cpu"), q4.profile.compatibleAccelerators);
        assertEquals(4096, q4.profile.maxContextTokens);
        assertEquals(1024, q4.profile.defaultMaxTokens);
        assertEquals(0.6d, q4.profile.defaultTemperature, 0.0d);
        assertEquals(0.95d, q4.profile.defaultTopP, 0.0d);
        assertEquals(TaiModelProfile.THINKING_TOGGLEABLE, q4.profile.thinkingMode);
        assertEquals(TaiModelProfile.THINKING_SWITCH_SYSTEM_FLAG, q4.profile.thinkingSwitch);
        // The bundles declare their own thought channel; the profile must not override it.
        assertNull(q4.profile.thinkingChannelStart);
        assertTrue(q4.capabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));
        assertNull(q4.minimumRuntimeVersion);

        TaiImportProfiles.Match full = match("SmolLM3-3B", "SmolLM3-3B.litertlm");
        assertEquals(Arrays.asList("cpu", "gpu"), full.profile.compatibleAccelerators);
        assertEquals(4096, full.profile.maxContextTokens);
    }

    @Test
    public void qwen35RunsOnTheCpuFirstWithThinkingOffAndNeedsRuntime015() {
        String[][] files = {
            {"Qwen3.5-0.8B", "Qwen3.5-0.8B-VL_int8.litertlm"},
            {"Qwen3.5-0.8B", "Qwen3.5-0.8B_int8.litertlm"},
            {"Qwen3.5-2B", "Qwen3.5-2B-VL_int8.litertlm"},
            {"Qwen3.5-2B", "Qwen3.5-2B_int8.litertlm"},
            {"Qwen3.5-4B", "Qwen3.5-4B_int8.litertlm"},
            {"Qwen3.5-4B", "Qwen3.5-4B_mixed_int4.litertlm"},
        };
        for (String[] file : files) {
            TaiImportProfiles.Match qwen = match(file[0], file[1]);
            assertEquals(TaiImportProfiles.FAMILY_QWEN35, qwen.family);
            assertEquals("cpu", qwen.profile.compatibleAccelerators.get(0));
            assertEquals(4096, qwen.profile.maxContextTokens);
            assertEquals(20, qwen.profile.defaultTopK);
            assertEquals(0.8d, qwen.profile.defaultTopP, 0.0d);
            assertEquals(0.7d, qwen.profile.defaultTemperature, 0.0d);
            assertEquals(TaiModelProfile.THINKING_NONE, qwen.profile.thinkingMode);
            assertEquals("0.15.0", qwen.minimumRuntimeVersion);
            assertEquals(file[1], file[1].contains("-VL"), qwen.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        }
    }

    @Test
    public void miniCpm5KeepsEachBuildsProcessorAndThinksOnlyWhenAsked() {
        TaiImportProfiles.Match int4 = match("MiniCPM5-2B", "MiniCPM5-2B_int4.litertlm");
        assertEquals(TaiImportProfiles.FAMILY_MINICPM5, int4.family);
        assertEquals(Arrays.asList("gpu", "cpu"), int4.profile.compatibleAccelerators);
        assertEquals(4096, int4.profile.maxContextTokens);
        assertEquals(2048, int4.profile.defaultMaxTokens);
        assertEquals(1.0d, int4.profile.defaultTemperature, 0.0d);
        assertEquals(TaiModelProfile.THINKING_SWITCH_TEMPLATE_BOOLEAN, int4.profile.thinkingSwitch);
        assertEquals("0.16.0", int4.minimumRuntimeVersion);
        assertEquals(Arrays.asList("gpu", "cpu"), match("MiniCPM5-2B", "MiniCPM5-2B_int8.litertlm").profile.compatibleAccelerators);
        // The card lists these two as "CPU-only models for exploration".
        assertEquals(Collections.singletonList("cpu"),
            match("MiniCPM5-2B", "minicpm_wi4c_wi8_afp32.litertlm").profile.compatibleAccelerators);
        assertEquals(Collections.singletonList("cpu"),
            match("MiniCPM5-2B", "minicpm_wi8_afp32.litertlm").profile.compatibleAccelerators);

        TaiImportProfiles.Match oneB = match("MiniCPM5-1B", "MiniCPM5-1B_dynamic_wi8_afp32.litertlm");
        assertEquals(0.9d, oneB.profile.defaultTemperature, 0.0d);
        assertEquals(0, oneB.profile.maxContextTokens);
        assertNull(oneB.minimumRuntimeVersion);
        assertEquals("cpu", match("MiniCPM5-1B", "minicpm_wi4b32_wi8_afp32.litertlm").profile.compatibleAccelerators.get(0));
        assertEquals("gpu", match("MiniCPM5-1B", "minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm").profile.compatibleAccelerators.get(0));
        // A local file with no repository still reads as the 1B build.
        TaiImportProfiles.Match local = TaiImportProfiles.match(null, "minicpm_wi4b32_wi8_afp32.litertlm");
        assertNotNull(local);
        assertEquals(0.9d, local.profile.defaultTemperature, 0.0d);
        // MiniCPM-V-4 is a different model and must not borrow MiniCPM5's defaults.
        assertNull(TaiImportProfiles.match("litert-community/MiniCPM-V-4", "MiniCPM-V-4-int8.litertlm"));
    }

    @Test
    public void medGemmaIsCappedToItsExportedCacheAndDemotesTheGpuBuild() {
        TaiImportProfiles.Match text = match("MedGemma-1.5-4B-IT", "medgemma-1.5-4b-it_q4_block32_ekv2048.litertlm");
        assertEquals(TaiImportProfiles.FAMILY_MEDGEMMA, text.family);
        assertEquals(2048, text.profile.maxContextTokens);
        assertEquals(512, text.profile.defaultMaxTokens);
        assertEquals(Arrays.asList("cpu", "gpu"), text.profile.compatibleAccelerators);
        assertFalse(text.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        assertTrue(match("MedGemma-1.5-4B-IT", "medgemma-1.5-4b-it_q4_block32_vision_ekv2048.litertlm")
            .capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        TaiImportProfiles.Match gpu = match("MedGemma-1.5-4B-IT", "medgemma-1.5-4b-it_q8_gpu_ekv2048.litertlm");
        assertEquals("gpu", gpu.profile.compatibleAccelerators.get(0));
        assertTrue(TaiImportProfiles.deprioritised("medgemma-1.5-4b-it_q8_gpu_ekv2048.litertlm"));
        assertFalse(TaiImportProfiles.deprioritised("medgemma-1.5-4b-it_q4_block32_ekv2048.litertlm"));
    }

    @Test
    public void functionGemmaLandsWithToolUse() {
        String[][] files = {
            {"functiongemma-270m-ft-mobile-actions", "mobile_actions_q8_ekv1024.litertlm"},
            {"functiongemma-270m-ft-tiny-garden", "tiny_garden_q8_ekv1024.litertlm"},
            {"functiongemma-mobile-actions_q8_ekv1024.litertlm", "mobile-actions_q8_ekv1024.litertlm"},
        };
        for (String[] file : files) {
            TaiImportProfiles.Match function = match(file[0], file[1]);
            assertEquals(TaiImportProfiles.FAMILY_FUNCTIONGEMMA, function.family);
            assertTrue(function.capabilities.contains(TaiModelSpec.CAPABILITY_TOOL_USE));
            assertTrue(function.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
            assertEquals(1024, function.profile.maxContextTokens);
            assertEquals(0.0d, function.profile.defaultTemperature, 0.0d);
            assertEquals(Collections.singletonList("cpu"), function.profile.compatibleAccelerators);
        }
        // Also a local file with no repository name.
        assertNotNull(TaiImportProfiles.match(null, "tiny_garden_q8_ekv1024.litertlm"));
    }

    @Test
    public void embeddingGemmaIsEmbeddingsOnly() {
        TaiImportProfiles.Match embedding = match("embeddinggemma-300m", "embeddinggemma-300M_seq1024_mixed-precision.tflite");
        assertEquals(TaiImportProfiles.FAMILY_EMBEDDINGGEMMA, embedding.family);
        assertEquals(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS), embedding.capabilities);
        assertEquals(1024, embedding.profile.maxContextTokens);
        assertEquals(2048, match("embeddinggemma-300m", "embeddinggemma-300M_seq2048_mixed-precision.tflite").profile.maxContextTokens);
        // The embeddings endpoint accepts the imported spec: text_embeddings survives, chat does not.
        assertEquals(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS),
            TaiModelSpec.endpointCapabilitiesFor("embeddinggemma-300m", TaiModelSpec.BACKEND_LITERT_LM,
                TaiModelSpec.FORMAT_LITERTLM, embedding.capabilities,
                "/models/embeddinggemma-300m/embeddinggemma-300M_seq1024_mixed-precision.tflite"));
    }

    @Test
    public void theFileNamesEkvCapsTheWindowBelowTheCards() {
        // A build exported with a smaller cache than the card's window keeps the smaller one.
        assertEquals(2048, match("Qwen3.5-2B", "Qwen3.5-2B_int8_ekv2048.litertlm").profile.maxContextTokens);
        assertEquals(4096, match("Qwen3.5-2B", "Qwen3.5-2B_int8_ekv8192.litertlm").profile.maxContextTokens);
        // A card with no window (MiniCPM5-1B) takes the file's own cache size.
        assertEquals(1280, TaiImportProfiles.match(null, "MiniCPM5-1B_int8_ekv1280.litertlm").profile.maxContextTokens);
    }

    @Test
    public void chipSpecificBuildsAreRecognisedAndMatchedToThePhone() {
        assertEquals("tensorg5", TaiImportProfiles.socTarget("embeddinggemma-300M_seq512_mixed-precision.google.tensor_g5.tflite"));
        assertEquals("tensorg6", TaiImportProfiles.socTarget("functiongemma-270m-ft-mobile-actions_Google_Tensor_G6.litertlm"));
        assertEquals("mt6991", TaiImportProfiles.socTarget("embeddinggemma-300M_seq256_mixed-precision.mediatek.mt6991.tflite"));
        assertEquals("sm8650", TaiImportProfiles.socTarget("embeddinggemma-300M_seq2048_mixed-precision.qualcomm.sm8650.tflite"));
        assertNull(TaiImportProfiles.socTarget("embeddinggemma-300M_seq2048_mixed-precision.tflite"));
        assertNull(TaiImportProfiles.socTarget("Qwen3.5-2B_int8.litertlm"));
        assertNull(TaiImportProfiles.socTarget("SmolLM3-3B_q4_block32_ekv4096.litertlm"));

        assertTrue(TaiImportProfiles.socMatches("sm8650", "SM8650"));
        assertTrue(TaiImportProfiles.socMatches("tensorg5", "Tensor G5"));
        assertFalse(TaiImportProfiles.socMatches("sm8650", "SM8750"));
        assertFalse(TaiImportProfiles.socMatches("mt6991", ""));
        assertTrue(TaiImportProfiles.socMatches(null, ""));
        assertTrue(TaiImportProfiles.deprioritised("embeddinggemma-300M_seq512_mixed-precision.google.tensor_g5.tflite"));
    }

    @Test
    public void unknownModelsAreLeftToTheGenericGuess() {
        assertNull(TaiImportProfiles.match("litert-community/gemma-3n-E2B-it-litert-lm", "gemma-3n-E2B-it-int4.litertlm"));
        assertNull(TaiImportProfiles.match(null, null));
        assertNull(TaiImportProfiles.match(""));
    }

    @Test
    public void candidatesCarryEachCardsRuntimeFloor() throws Exception {
        assertEquals("0.15.0", firstCandidate("litert-community/Qwen3.5-0.8B", "Qwen3.5-0.8B_int8.litertlm")
            .optString("minimumRuntimeVersion"));
        assertEquals("0.15.0", firstCandidate("litert-community/Qwen3.5-2B", "Qwen3.5-2B-VL_int8.litertlm")
            .optString("minimumRuntimeVersion"));
        assertEquals("0.16.0", firstCandidate("litert-community/MiniCPM5-2B", "MiniCPM5-2B_int4.litertlm")
            .optString("minimumRuntimeVersion"));
        assertFalse(firstCandidate("litert-community/SmolLM3-3B", "SmolLM3-3B_q4_block32_ekv4096.litertlm")
            .has("minimumRuntimeVersion"));
        // Another publisher's copy is not bound by litert-community's card.
        assertFalse(firstCandidate("someone/Qwen3.5-2B", "Qwen3.5-2B_int8.litertlm").has("minimumRuntimeVersion"));
    }

    private static JSONObject firstCandidate(String repository, String file) throws Exception {
        JSONObject metadata = new JSONObject().put("sha", SHA)
            .put("siblings", new JSONArray().put(new JSONObject().put("rfilename", file).put("size", 20)));
        JSONArray candidates = TaiHuggingFace.parse("https://huggingface.co/" + repository).candidates(metadata);
        assertEquals(1, candidates.length());
        return candidates.getJSONObject(0);
    }

    // ---- thinking off per family ----

    @Test
    public void miniCpm5SendsARealBooleanBothWays() {
        TaiModelProfile profile = match("MiniCPM5-2B", "MiniCPM5-2B_int8.litertlm").profile;
        Map<String, Object> unset = LiteRtTaiRuntime.thinkingExtraContext(thinking(null), profile);
        assertSame(Boolean.FALSE, unset.get("enable_thinking"));
        assertSame(Boolean.FALSE, LiteRtTaiRuntime.thinkingExtraContext(thinking(false), profile).get("enable_thinking"));
        assertSame(Boolean.TRUE, LiteRtTaiRuntime.thinkingExtraContext(thinking(true), profile).get("enable_thinking"));
        // The system prompt is not touched for a template switch.
        assertEquals("", LiteRtTaiRuntime.thinkingSystemPrompt("", thinking(false), profile));
    }

    @Test
    public void granite42IsToldNotToThinkWithARealBoolean() {
        TaiModelProfile guessed = new TaiModelProfile(Collections.singletonList("cpu"), 1024, 64, 0.95d, 1.0d, null,
            "import-dialog-selection");
        assertEquals(TaiModelProfile.THINKING_NONE, guessed.thinkingMode);
        TaiModelProfile profile = TaiImportProfiles.withGraniteThinkingSwitch(guessed,
            "litert-community/granite-4.2-3b granite-4.2-3b_int4.litertlm");
        assertEquals(TaiModelProfile.THINKING_TOGGLEABLE, profile.thinkingMode);
        assertEquals(TaiModelProfile.THINKING_SWITCH_TEMPLATE_BOOLEAN, profile.thinkingSwitch);
        // Everything else the importer guessed stays.
        assertEquals(guessed.compatibleAccelerators, profile.compatibleAccelerators);
        assertEquals(guessed.defaultMaxTokens, profile.defaultMaxTokens);
        assertEquals(guessed.source, profile.source);
        // Off unless asked: the key is sent as false when thinking is unset or off, true when on.
        assertSame(Boolean.FALSE, LiteRtTaiRuntime.thinkingExtraContext(thinking(null), profile).get("enable_thinking"));
        assertSame(Boolean.FALSE, LiteRtTaiRuntime.thinkingExtraContext(thinking(false), profile).get("enable_thinking"));
        assertSame(Boolean.TRUE, LiteRtTaiRuntime.thinkingExtraContext(thinking(true), profile).get("enable_thinking"));
    }

    @Test
    public void otherGraniteFilesAndProfilesWithAThinkingModeAreLeftAlone() {
        TaiModelProfile guessed = new TaiModelProfile(Collections.singletonList("cpu"), 1024, 64, 0.95d, 1.0d, null, "x");
        assertSame(guessed, TaiImportProfiles.withGraniteThinkingSwitch(guessed, "granite-4.0-h-1b_int8.litertlm"));
        assertSame(guessed, TaiImportProfiles.withGraniteThinkingSwitch(guessed, null));
        TaiModelProfile always = TaiModelProfile.qwen3Thinking2507Profile();
        assertSame(always, TaiImportProfiles.withGraniteThinkingSwitch(always, "granite-4.2-3b-int4"));
        assertTrue(TaiImportProfiles.granite42("granite-4.2-3b-int4"));
        assertTrue(TaiImportProfiles.granite42("/models/x/Granite-4.2-3B_int4.litertlm"));
        assertFalse(TaiImportProfiles.granite42("granite-4.0-h-1b"));
    }

    @Test
    public void smolLm3SwitchesThinkingOffInTheSystemPrompt() {
        TaiModelProfile profile = match("SmolLM3-3B", "SmolLM3-3B_q4_block32_ekv4096.litertlm").profile;
        assertTrue(LiteRtTaiRuntime.thinkingExtraContext(thinking(false), profile).isEmpty());
        assertTrue(LiteRtTaiRuntime.thinkingExtraContext(thinking(true), profile).isEmpty());
        assertEquals("/no_think", LiteRtTaiRuntime.thinkingSystemPrompt("", thinking(null), profile));
        assertEquals("/no_think", LiteRtTaiRuntime.thinkingSystemPrompt("  ", thinking(false), profile));
        assertEquals("Answer briefly.\n\n/no_think",
            LiteRtTaiRuntime.thinkingSystemPrompt("Answer briefly.", thinking(false), profile));
        // Thinking on is the templates' default; nothing is added.
        assertEquals("Answer briefly.", LiteRtTaiRuntime.thinkingSystemPrompt("Answer briefly.", thinking(true), profile));
        assertEquals("", LiteRtTaiRuntime.thinkingSystemPrompt("", thinking(true), profile));
        // A caller's own flag wins.
        assertEquals("/think Answer.", LiteRtTaiRuntime.thinkingSystemPrompt("/think Answer.", thinking(false), profile));
    }

    @Test
    public void qwen35AndOtherFamiliesSendNothing() {
        TaiModelProfile qwen = match("Qwen3.5-2B", "Qwen3.5-2B_int8.litertlm").profile;
        assertTrue(LiteRtTaiRuntime.thinkingExtraContext(thinking(true), qwen).isEmpty());
        assertEquals("Be kind.", LiteRtTaiRuntime.thinkingSystemPrompt("Be kind.", thinking(false), qwen));
        TaiModelProfile medGemma = match("MedGemma-1.5-4B-IT", "medgemma-1.5-4b-it_q4_block32_ekv2048.litertlm").profile;
        assertTrue(LiteRtTaiRuntime.thinkingExtraContext(thinking(false), medGemma).isEmpty());
    }

    @Test
    public void theDefaultSwitchStillOmitsTheKeyForOff() {
        TaiModelProfile gemma = new TaiModelProfile(Arrays.asList("gpu", "cpu"), 4000, 64, 0.95d, 1.0d, 8,
            TaiModelProfile.SOURCE_EDGE_GALLERY_1_0_15, TaiModelProfile.THINKING_TOGGLEABLE, null, null);
        assertEquals(TaiModelProfile.THINKING_SWITCH_TEMPLATE_KEY, gemma.thinkingSwitch);
        assertTrue(LiteRtTaiRuntime.thinkingExtraContext(thinking(false), gemma).isEmpty());
        assertEquals("true", LiteRtTaiRuntime.thinkingExtraContext(thinking(true), gemma).get("enable_thinking"));
    }

    @Test
    public void theSwitchSurvivesStorageAndRequests() throws Exception {
        TaiModelProfile profile = match("MiniCPM5-2B", "MiniCPM5-2B_int4.litertlm").profile;
        assertEquals(TaiModelProfile.THINKING_SWITCH_TEMPLATE_BOOLEAN, TaiModelProfile.fromJson(profile.toJson()).thinkingSwitch);
        JSONObject request = new JSONObject().put("runtimeProfile", profile.toJson());
        TaiModelProfile fallback = new TaiModelProfile(Collections.singletonList("cpu"), 1024, 64, 0.95d, 1.0d, null, "fallback");
        assertEquals(TaiModelProfile.THINKING_SWITCH_TEMPLATE_BOOLEAN, TaiModelProfile.fromRequest(request, fallback).thinkingSwitch);
        // Profiles stored before the field existed read as the old behaviour.
        JSONObject legacy = profile.toJson();
        legacy.remove("thinkingSwitch");
        assertEquals(TaiModelProfile.THINKING_SWITCH_TEMPLATE_KEY, TaiModelProfile.fromJson(legacy).thinkingSwitch);
    }

    @Test
    public void anImportWithoutARuntimeProfileStillGetsTheFamilyDefaults() {
        TaiModelSpec spec = new TaiModelSpec("smollm3-3b", "SmolLM3 3B", "Downloaded model", "downloaded",
            "https://huggingface.co/litert-community/SmolLM3-3B/resolve/" + SHA + "/SmolLM3-3B_q4_block32_ekv4096.litertlm",
            "apache-2.0", 0L, match("SmolLM3-3B", null).capabilities, false);
        TaiModelProfile profile = TaiModelProfile.forModel(spec);
        assertEquals(TaiModelProfile.THINKING_SWITCH_SYSTEM_FLAG, profile.thinkingSwitch);
        assertEquals(4096, profile.maxContextTokens);
    }

    @Test
    public void gemmaArtisanBundlesAreNamedAndNeverTheDefault() {
        org.junit.Assert.assertTrue(TaiImportProfiles.artisanBundle("gemma-4-E2B-it-gpu.litertlm"));
        org.junit.Assert.assertTrue(TaiImportProfiles.artisanBundle("gemma-4-E4B-it-web.litertlm"));
        org.junit.Assert.assertFalse(TaiImportProfiles.artisanBundle("gemma-4-E2B-it.litertlm"));
        org.junit.Assert.assertTrue(TaiImportProfiles.artisanBundle("/data/models/gemma-4-e2b-it-gpu/gemma-4-E2B-it-gpu.litertlm"));
        org.junit.Assert.assertFalse(TaiImportProfiles.artisanBundle("/data/models/lfm2.5/LFM2.5-1.2B-Instruct_int4_gpu.litertlm"));
        org.junit.Assert.assertTrue(TaiImportProfiles.deprioritised("gemma-4-E2B-it-web.litertlm"));
        org.junit.Assert.assertTrue(TaiImportProfiles.deprioritised("gemma-4-E2B-it-gpu.litertlm"));
        org.junit.Assert.assertFalse(TaiImportProfiles.deprioritised("gemma-4-E2B-it.litertlm"));
    }
}
