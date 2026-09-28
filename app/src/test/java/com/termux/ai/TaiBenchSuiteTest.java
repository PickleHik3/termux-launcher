package com.termux.ai;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The graders, the preset expansion, and the bundled passage's size. */
public class TaiBenchSuiteTest {

    private static final TaiBenchSuite.ModelInput MNN_CPU_ONLY =
        new TaiBenchSuite.ModelInput("qwen3-vl-2b-instruct-mnn", TaiModelSpec.BACKEND_MNN_LLM, "cpu", false, false);
    private static final TaiBenchSuite.ModelInput MNN_EAGLE_GPU =
        new TaiBenchSuite.ModelInput("qwen3-vl-2b-instruct-eagle3-mnn", TaiModelSpec.BACKEND_MNN_LLM, "cpu", true, true);
    private static final TaiBenchSuite.ModelInput LITERT_GPU_BEST =
        new TaiBenchSuite.ModelInput("gemma-4-e2b", TaiModelSpec.BACKEND_LITERT_LM, "gpu", true, false);

    // ---- graders ----

    @Test
    public void arithmeticAcceptsTheNumberInAnyDress() {
        TaiBenchSuite.Check check = TaiBenchSuite.CHECKS.get(0);
        assertEquals("arithmetic", check.name);
        assertTrue(check.grade("42"));
        assertTrue(check.grade("  42.  "));
        assertTrue(check.grade("The answer is 42."));
        assertFalse(check.grade("41"));
        assertFalse(check.grade(""));
        assertFalse(check.grade(null));
    }

    @Test
    public void jsonCheckParsesAndComparesTheColour() {
        TaiBenchSuite.Check check = TaiBenchSuite.CHECKS.get(1);
        assertEquals("json", check.name);
        assertTrue(check.grade("{\"color\":\"blue\"}"));
        assertTrue(check.grade(" {\"COLOR\": \"Blue\"} . "));
        assertTrue(check.grade("```json\n{\"color\":\"blue\"}\n```"));
        assertFalse(check.grade("{\"color\":\"red\"}"));
        assertFalse(check.grade("{\"colour\":\"blue\"}"));
        assertFalse(check.grade("blue"));
        assertFalse(check.grade("{not json"));
    }

    @Test
    public void repeatCheckWantsTheWord() {
        TaiBenchSuite.Check check = TaiBenchSuite.CHECKS.get(2);
        assertEquals("repeat", check.name);
        assertTrue(check.grade("pineapple"));
        assertTrue(check.grade("Pineapple."));
        assertTrue(check.grade("\"pineapple\""));
        assertFalse(check.grade("apple"));
    }

    @Test
    public void thereAreThreeChecks() {
        assertEquals(3, TaiBenchSuite.CHECKS.size());
    }

    // ---- presets ----

    @Test
    public void presetIdsAndRunCounts() {
        assertSame(TaiBenchSuite.Preset.STANDARD, TaiBenchSuite.Preset.fromId(null));
        assertSame(TaiBenchSuite.Preset.STANDARD, TaiBenchSuite.Preset.fromId(""));
        assertSame(TaiBenchSuite.Preset.QUICK, TaiBenchSuite.Preset.fromId("Quick"));
        assertSame(TaiBenchSuite.Preset.THOROUGH, TaiBenchSuite.Preset.fromId(" thorough "));
        assertNull(TaiBenchSuite.Preset.fromId("fast"));

        assertEquals(1, TaiBenchSuite.Preset.QUICK.readingRuns);
        assertEquals(1, TaiBenchSuite.Preset.QUICK.firstWordRuns);
        assertEquals(1, TaiBenchSuite.Preset.QUICK.writingRuns);
        assertFalse(TaiBenchSuite.Preset.QUICK.sustained);
        assertEquals(3, TaiBenchSuite.Preset.STANDARD.readingRuns);
        assertEquals(3, TaiBenchSuite.Preset.STANDARD.firstWordRuns);
        assertEquals(3, TaiBenchSuite.Preset.STANDARD.writingRuns);
        assertFalse(TaiBenchSuite.Preset.STANDARD.sustained);
        assertEquals(3, TaiBenchSuite.Preset.THOROUGH.readingRuns);
        assertEquals(3, TaiBenchSuite.Preset.THOROUGH.firstWordRuns);
        assertEquals(5, TaiBenchSuite.Preset.THOROUGH.writingRuns);
        assertTrue(TaiBenchSuite.Preset.THOROUGH.sustained);
    }

    @Test
    public void quickTakesOnlyTheProcessorAnAutomaticLoadWould() {
        List<TaiBenchSuite.EntryPlan> entries = TaiBenchSuite.expand(
            Arrays.asList(LITERT_GPU_BEST, MNN_CPU_ONLY), TaiBenchSuite.Preset.QUICK, null, false);
        assertEquals(2, entries.size());
        assertEquals("gemma-4-e2b", entries.get(0).modelId);
        assertEquals("gpu", entries.get(0).accelerator);
        assertEquals("qwen3-vl-2b-instruct-mnn", entries.get(1).modelId);
        assertEquals("cpu", entries.get(1).accelerator);
    }

    @Test
    public void standardTakesCpuAndGpuWhereSupported() {
        List<TaiBenchSuite.EntryPlan> entries = TaiBenchSuite.expand(
            Arrays.asList(LITERT_GPU_BEST, MNN_CPU_ONLY), TaiBenchSuite.Preset.STANDARD, null, false);
        assertEquals(3, entries.size());
        assertEquals("cpu", entries.get(0).accelerator);
        assertEquals("gpu", entries.get(1).accelerator);
        assertEquals("qwen3-vl-2b-instruct-mnn", entries.get(2).modelId);
        assertEquals("cpu", entries.get(2).accelerator);
    }

    /** An asked-for processor is planned even where unsupported: the load's refusal is the answer. */
    @Test
    public void anExplicitProcessorListOverridesThePreset() {
        List<TaiBenchSuite.EntryPlan> gpuOnly = TaiBenchSuite.expand(
            Arrays.asList(LITERT_GPU_BEST, MNN_CPU_ONLY), TaiBenchSuite.Preset.STANDARD,
            Collections.singletonList("GPU"), false);
        assertEquals(2, gpuOnly.size());
        assertEquals("gemma-4-e2b", gpuOnly.get(0).modelId);
        assertEquals("gpu", gpuOnly.get(0).accelerator);
        assertEquals("qwen3-vl-2b-instruct-mnn", gpuOnly.get(1).modelId);
        assertEquals("gpu", gpuOnly.get(1).accelerator);

        List<TaiBenchSuite.EntryPlan> both = TaiBenchSuite.expand(
            Collections.singletonList(LITERT_GPU_BEST), TaiBenchSuite.Preset.QUICK,
            Arrays.asList("cpu", "gpu", "cpu"), false);
        assertEquals(2, both.size());
        assertEquals("cpu", both.get(0).accelerator);
        assertEquals("gpu", both.get(1).accelerator);

        List<TaiBenchSuite.EntryPlan> unknownProcessor = TaiBenchSuite.expand(
            Collections.singletonList(LITERT_GPU_BEST), TaiBenchSuite.Preset.QUICK,
            Collections.singletonList("npu"), false);
        assertTrue(unknownProcessor.isEmpty());
    }

    @Test
    public void eagleOnlyReachesModelsThatShipADraft() {
        List<TaiBenchSuite.EntryPlan> entries = TaiBenchSuite.expand(
            Arrays.asList(MNN_EAGLE_GPU, MNN_CPU_ONLY, LITERT_GPU_BEST), TaiBenchSuite.Preset.QUICK, null, true);
        assertEquals(3, entries.size());
        assertTrue(entries.get(0).speculative);
        assertFalse(entries.get(1).speculative);
        assertFalse(entries.get(2).speculative);
        // Off by default: the same run without the flag has no speculative entry.
        for (TaiBenchSuite.EntryPlan entry : TaiBenchSuite.expand(
            Arrays.asList(MNN_EAGLE_GPU, MNN_CPU_ONLY, LITERT_GPU_BEST), TaiBenchSuite.Preset.QUICK, null, false)) {
            assertFalse(entry.speculative);
        }
    }

    @Test
    public void entryKeySeparatesProcessorAndDraftModel() {
        TaiBenchSuite.EntryPlan cpu = new TaiBenchSuite.EntryPlan("m", TaiModelSpec.BACKEND_MNN_LLM, "cpu", false);
        TaiBenchSuite.EntryPlan gpu = new TaiBenchSuite.EntryPlan("m", TaiModelSpec.BACKEND_MNN_LLM, "gpu", false);
        TaiBenchSuite.EntryPlan eagle = new TaiBenchSuite.EntryPlan("m", TaiModelSpec.BACKEND_MNN_LLM, "cpu", true);
        assertEquals("m|mnn-llm|cpu|off", cpu.key());
        assertEquals("m|mnn-llm|gpu|off", gpu.key());
        assertEquals("m|mnn-llm|cpu|on", eagle.key());
    }

    @Test
    public void timeLimitIsThreeTimesTheExpectedTime() {
        assertEquals(3L * TaiBenchSuite.EXPECTED_WRITING_SECONDS * 1000L, TaiBenchSuite.timeLimitMs(TaiBenchSuite.PHASE_WRITING));
        assertEquals(3L * TaiBenchSuite.SUSTAINED_SECONDS * 1000L, TaiBenchSuite.timeLimitMs(TaiBenchSuite.PHASE_SUSTAINED));
        assertEquals(3L * TaiBenchSuite.EXPECTED_LOAD_SECONDS * 1000L, TaiBenchSuite.timeLimitMs(TaiBenchSuite.PHASE_LOAD));
    }

    @Test
    public void estimateIsAMinuteForQuickAndPerProcessorOtherwise() {
        assertEquals(60_000L, TaiBenchSuite.estimateMs(TaiBenchSuite.Preset.QUICK, 1));
        assertEquals(60_000L, TaiBenchSuite.estimateMs(TaiBenchSuite.Preset.QUICK, 2));
        assertEquals(2 * 60_000L, TaiBenchSuite.estimateMs(TaiBenchSuite.Preset.STANDARD, 1));
        assertEquals(4 * 60_000L, TaiBenchSuite.estimateMs(TaiBenchSuite.Preset.STANDARD, 2));
        assertEquals(4 * 60_000L, TaiBenchSuite.estimateMs(TaiBenchSuite.Preset.THOROUGH, 1));
        assertEquals(8 * 60_000L, TaiBenchSuite.estimateMs(TaiBenchSuite.Preset.THOROUGH, 2));
        // A count under one is one processor, never a zero estimate.
        assertEquals(2 * 60_000L, TaiBenchSuite.estimateMs(TaiBenchSuite.Preset.STANDARD, 0));
    }

    // ---- the passage ----

    /** Read straight off disk, the way the help tests read strings.xml: no resource table needed. */
    @Test
    public void bundledPassageIsAboutFiveHundredTokens() throws Exception {
        File asset = new File("src/main/assets/" + TaiBenchSuite.READING_ASSET);
        if (!asset.isFile()) asset = new File("app/src/main/assets/" + TaiBenchSuite.READING_ASSET);
        assertTrue("passage asset missing: " + asset.getAbsolutePath(), asset.isFile());
        String passage = new String(Files.readAllBytes(asset.toPath()), StandardCharsets.UTF_8);
        int words = passage.trim().split("\\s+").length;
        // About 380 English words is about 512 tokens; a rewrite that drifts far changes what
        // "reading" measures and belongs to a new bench version.
        assertTrue("passage is " + words + " words", words >= 340 && words <= 440);
        String prompt = TaiBenchSuite.readingPrompt(passage);
        assertTrue(prompt.endsWith(TaiBenchSuite.READING_INSTRUCTION));
        assertTrue(prompt.startsWith(passage.trim()));
    }
}
