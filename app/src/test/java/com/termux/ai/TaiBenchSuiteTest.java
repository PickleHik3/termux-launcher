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

/** The graders, the preset expansion, the long-input fitting, and the bundled log's size. */
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
        assertNull(TaiBenchSuite.Preset.fromId("fast"));
        // Thorough and its sustained run went with bench v1.
        assertNull(TaiBenchSuite.Preset.fromId("thorough"));
        assertEquals(2, TaiBenchSuite.Preset.values().length);

        assertEquals(1, TaiBenchSuite.Preset.QUICK.runs);
        assertEquals(2, TaiBenchSuite.Preset.STANDARD.runs);
    }

    @Test
    public void gpuOnlyModelSkipsTheCpuUnlessAskedFor() {
        TaiBenchSuite.ModelInput gpuOnly =
            new TaiBenchSuite.ModelInput("gemma-4-e2b-it-gpu", TaiModelSpec.BACKEND_LITERT_LM, "cpu", false, true, false);
        List<TaiBenchSuite.EntryPlan> compared = TaiBenchSuite.expand(Collections.singletonList(gpuOnly), true, null, false);
        assertEquals(1, compared.size());
        assertEquals("gpu", compared.get(0).accelerator);
        List<TaiBenchSuite.EntryPlan> single = TaiBenchSuite.expand(Collections.singletonList(gpuOnly), false, null, false);
        assertEquals("gpu", single.get(0).accelerator);
        List<TaiBenchSuite.EntryPlan> forced = TaiBenchSuite.expand(
            Collections.singletonList(gpuOnly), true, Collections.singletonList("cpu"), false);
        assertEquals("cpu", forced.get(0).accelerator);
    }

    @Test
    public void byDefaultOnlyTheProcessorAnAutomaticLoadWouldPick() {
        List<TaiBenchSuite.EntryPlan> entries = TaiBenchSuite.expand(
            Arrays.asList(LITERT_GPU_BEST, MNN_CPU_ONLY), false, null, false);
        assertEquals(2, entries.size());
        assertEquals("gemma-4-e2b", entries.get(0).modelId);
        assertEquals("gpu", entries.get(0).accelerator);
        assertEquals("qwen3-vl-2b-instruct-mnn", entries.get(1).modelId);
        assertEquals("cpu", entries.get(1).accelerator);
    }

    @Test
    public void compareTakesCpuAndGpuWhereSupported() {
        List<TaiBenchSuite.EntryPlan> entries = TaiBenchSuite.expand(
            Arrays.asList(LITERT_GPU_BEST, MNN_CPU_ONLY), true, null, false);
        assertEquals(3, entries.size());
        assertEquals("cpu", entries.get(0).accelerator);
        assertEquals("gpu", entries.get(1).accelerator);
        assertEquals("qwen3-vl-2b-instruct-mnn", entries.get(2).modelId);
        assertEquals("cpu", entries.get(2).accelerator);
    }

    /** An asked-for processor is planned even where unsupported: the load's refusal is the answer. */
    @Test
    public void anExplicitProcessorListOverridesTheSwitch() {
        List<TaiBenchSuite.EntryPlan> gpuOnly = TaiBenchSuite.expand(
            Arrays.asList(LITERT_GPU_BEST, MNN_CPU_ONLY), true, Collections.singletonList("GPU"), false);
        assertEquals(2, gpuOnly.size());
        assertEquals("gemma-4-e2b", gpuOnly.get(0).modelId);
        assertEquals("gpu", gpuOnly.get(0).accelerator);
        assertEquals("qwen3-vl-2b-instruct-mnn", gpuOnly.get(1).modelId);
        assertEquals("gpu", gpuOnly.get(1).accelerator);

        List<TaiBenchSuite.EntryPlan> both = TaiBenchSuite.expand(
            Collections.singletonList(LITERT_GPU_BEST), false, Arrays.asList("cpu", "gpu", "cpu"), false);
        assertEquals(2, both.size());
        assertEquals("cpu", both.get(0).accelerator);
        assertEquals("gpu", both.get(1).accelerator);

        List<TaiBenchSuite.EntryPlan> unknownProcessor = TaiBenchSuite.expand(
            Collections.singletonList(LITERT_GPU_BEST), false, Collections.singletonList("npu"), false);
        assertTrue(unknownProcessor.isEmpty());
    }

    @Test
    public void eagleOnlyReachesModelsThatShipADraft() {
        List<TaiBenchSuite.EntryPlan> entries = TaiBenchSuite.expand(
            Arrays.asList(MNN_EAGLE_GPU, MNN_CPU_ONLY, LITERT_GPU_BEST), false, null, true);
        assertEquals(3, entries.size());
        assertTrue(entries.get(0).speculative);
        assertFalse(entries.get(1).speculative);
        assertFalse(entries.get(2).speculative);
        // Off by default: the same run without the flag has no speculative entry.
        for (TaiBenchSuite.EntryPlan entry : TaiBenchSuite.expand(
            Arrays.asList(MNN_EAGLE_GPU, MNN_CPU_ONLY, LITERT_GPU_BEST), false, null, false)) {
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
        assertEquals(3L * TaiBenchSuite.EXPECTED_CHAT_SECONDS * 1000L, TaiBenchSuite.timeLimitMs(TaiBenchSuite.PHASE_CHAT));
        assertEquals(3L * TaiBenchSuite.EXPECTED_LONG_INPUT_SECONDS * 1000L, TaiBenchSuite.timeLimitMs(TaiBenchSuite.PHASE_LONG_INPUT));
        assertEquals(3L * TaiBenchSuite.EXPECTED_LOAD_SECONDS * 1000L, TaiBenchSuite.timeLimitMs(TaiBenchSuite.PHASE_LOAD));
        assertEquals(3L * TaiBenchSuite.EXPECTED_CHECK_SECONDS * 1000L, TaiBenchSuite.timeLimitMs(TaiBenchSuite.PHASE_CHECK));
    }

    @Test
    public void estimateIsPerProcessor() {
        assertEquals(90_000L, TaiBenchSuite.estimateMs(TaiBenchSuite.Preset.QUICK, 1));
        assertEquals(180_000L, TaiBenchSuite.estimateMs(TaiBenchSuite.Preset.QUICK, 2));
        assertEquals(3 * 60_000L, TaiBenchSuite.estimateMs(TaiBenchSuite.Preset.STANDARD, 1));
        assertEquals(6 * 60_000L, TaiBenchSuite.estimateMs(TaiBenchSuite.Preset.STANDARD, 2));
        // A count under one is one processor, never a zero estimate.
        assertEquals(3 * 60_000L, TaiBenchSuite.estimateMs(TaiBenchSuite.Preset.STANDARD, 0));
    }

    // ---- the long input ----

    private static String bundledLog() throws Exception {
        // Read straight off disk, the way the help tests read strings.xml: no resource table needed.
        File asset = new File("src/main/assets/" + TaiBenchSuite.LONG_INPUT_ASSET);
        if (!asset.isFile()) asset = new File("app/src/main/assets/" + TaiBenchSuite.LONG_INPUT_ASSET);
        assertTrue("log asset missing: " + asset.getAbsolutePath(), asset.isFile());
        return new String(Files.readAllBytes(asset.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void bundledLogIsAboutEightKilobytesEndingInTheError() throws Exception {
        String log = bundledLog();
        // About 8 KB is about 2000-2700 tokens of log text; a rewrite that drifts far changes what
        // "reads a long page" measures and belongs to a new bench version.
        assertTrue("log is " + log.length() + " chars", log.length() >= 7_000 && log.length() <= 9_500);
        assertTrue(log.contains("BUILD FAILED"));
    }

    @Test
    public void aWindowThatHoldsTheLogLeavesItWhole() throws Exception {
        String log = bundledLog();
        TaiBenchSuite.LongInput input = TaiBenchSuite.longInput(log, 4096);
        assertFalse(input.truncated);
        assertEquals(log.trim().length(), input.keptChars);
        assertEquals(input.keptChars, input.totalChars);
        assertTrue(input.prompt.startsWith(log.trim()));
        assertTrue(input.prompt.endsWith("\n\n" + TaiBenchSuite.LONG_INPUT_QUESTION));
        // An unknown window is never a reason to cut.
        assertFalse(TaiBenchSuite.longInput(log, 0).truncated);
    }

    @Test
    public void aSmallWindowCutsTheLogFromTheTopAtALineAndKeepsTheEnd() throws Exception {
        String log = bundledLog();
        int window = 2048;
        TaiBenchSuite.LongInput input = TaiBenchSuite.longInput(log, window);
        assertTrue(input.truncated);
        assertEquals(log.trim().length(), input.totalChars);
        assertTrue(input.keptChars < input.totalChars);
        // What is kept, plus the reply cap and the overhead, fits the window at the estimate.
        int budgetChars = (window - TaiBenchSuite.LONG_INPUT_MAX_TOKENS - TaiBenchSuite.PROMPT_OVERHEAD_TOKENS)
            * TaiBenchSuite.CHARS_PER_TOKEN_ESTIMATE;
        assertTrue(input.keptChars <= budgetChars);
        // The tail survives whole and the cut is on a line boundary.
        String kept = input.prompt.substring(0, input.prompt.length() - ("\n\n" + TaiBenchSuite.LONG_INPUT_QUESTION).length());
        assertTrue(log.trim().endsWith(kept));
        assertTrue(kept.contains("BUILD FAILED"));
        int cutAt = log.trim().length() - kept.length();
        assertEquals('\n', log.trim().charAt(cutAt - 1));
    }

    @Test
    public void aWindowTooSmallForAnythingStillKeepsTheTail() {
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 400; i++) log.append("line ").append(i).append(" of a long build log\n");
        TaiBenchSuite.LongInput input = TaiBenchSuite.longInput(log.toString(), 200);
        assertTrue(input.truncated);
        assertTrue(input.keptChars >= TaiBenchSuite.MIN_LOG_CHARS - 40);
        assertTrue(input.prompt.contains("line 399 of a long build log"));
        assertFalse(input.prompt.contains("line 0 of a long build log"));
    }
}
