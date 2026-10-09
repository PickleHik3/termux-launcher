package com.termux.ai;

import com.termux.ai.TaiFeatureCheck.Axis;
import com.termux.ai.TaiFeatureCheck.Outcome;
import com.termux.ai.TaiFeatureCheck.Runs;
import com.termux.ai.TaiPlatformCaps.GpuPath;
import com.termux.ai.TaiGpuVerdict.State;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The feature check's pure decisions (tai-feature-load-plan spec, step 2): which runs a feature gets,
 * how a speed reads, what counts as a sane answer, when a result is stale, what the runs say about the
 * GPU, and the stored record read back as evidence.
 */
public class TaiFeatureCheckTest {
    private static final String GPU = TaiTierPolicy.ACCEL_GPU;
    private static final String CPU = TaiTierPolicy.ACCEL_CPU;
    private static final String E2B = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;
    private static final String LITERT = TaiModelSpec.BACKEND_LITERT_LM;

    // ---------------------------------------------------------------------------------- runs

    @Test
    public void aChatFeatureGetsItsSetupThenEachAxisFlipped() {
        Runs runs = TaiFeatureCheck.runs(TaiFunction.TIDY_DICTATION, GPU, Boolean.TRUE, true, true);
        assertEquals(3, runs.variants.size());
        assertVariant(runs, 0, Axis.CURRENT, GPU, true);
        assertVariant(runs, 1, Axis.ACCELERATOR, CPU, true);
        assertVariant(runs, 2, Axis.SPECULATIVE, GPU, false);
        assertFalse(runs.speculativeUnavailable);
    }

    @Test
    public void noUsableGpuAndNoDrafterLeaveOneRun() {
        // A phone with no usable GPU: the plan's GPU is not run, and the CPU is the setup.
        Runs noGpu = TaiFeatureCheck.runs(TaiFunction.ASSISTANT, GPU, Boolean.TRUE, true, false);
        assertEquals(2, noGpu.variants.size());
        assertVariant(noGpu, 0, Axis.CURRENT, CPU, true);
        assertVariant(noGpu, 1, Axis.SPECULATIVE, CPU, false);
        // A file without speculative decoding: no speculative run, and the row says it is not available.
        Runs plain = TaiFeatureCheck.runs(TaiFunction.APP_CATEGORIES, CPU, Boolean.TRUE, false, true);
        assertEquals(2, plain.variants.size());
        assertVariant(plain, 0, Axis.CURRENT, CPU, false);
        assertVariant(plain, 1, Axis.ACCELERATOR, GPU, false);
        assertTrue(plain.speculativeUnavailable);
        Runs one = TaiFeatureCheck.runs(TaiFunction.ASSISTANT, CPU, null, false, false);
        assertEquals(1, one.variants.size());
    }

    @Test
    public void aCpuOnlyFeatureRunsOnce() {
        for (TaiFunction feature : new TaiFunction[] {TaiFunction.EMBEDDINGS, TaiFunction.READ_ALOUD}) {
            Runs runs = TaiFeatureCheck.runs(feature, CPU, null, true, true);
            assertEquals(feature.name(), 1, runs.variants.size());
            assertVariant(runs, 0, Axis.CURRENT, CPU, false);
            assertFalse(runs.speculativeUnavailable);
        }
    }

    @Test
    public void theGpuIsSkippedWithoutOneOrAfterItAnsweredWrongly() {
        assertTrue(TaiFeatureCheck.gpuUsable(GpuPath.YES, State.UNKNOWN));
        assertTrue(TaiFeatureCheck.gpuUsable(GpuPath.UNKNOWN, State.VERIFIED));
        // The family rule says CPU first: the check is how that gets measured.
        assertTrue(TaiFeatureCheck.gpuUsable(GpuPath.CPU_FIRST, State.UNKNOWN));
        assertFalse(TaiFeatureCheck.gpuUsable(GpuPath.NO, State.VERIFIED));
        assertFalse(TaiFeatureCheck.gpuUsable(GpuPath.YES, State.FAILED));
    }

    private static void assertVariant(Runs runs, int index, Axis axis, String accelerator, boolean speculative) {
        TaiFeatureCheck.Variant variant = runs.variants.get(index);
        assertEquals(axis, variant.axis);
        assertEquals(accelerator, variant.accelerator);
        assertEquals(speculative, variant.speculative);
    }

    // ------------------------------------------------------------------------------- figures

    @Test
    public void speedsReadAsEachFeaturesPlainFigure() {
        // 150 words is a minute of speech; tidied in 6 s.
        double cleanup = TaiFeatureCheck.cleanupSpeed(150, 6_000L);
        assertEquals(10.0, cleanup, 1e-9);
        assertEquals(6.0, TaiFeatureCheck.figure(TaiFunction.TIDY_DICTATION, cleanup), 1e-9);
        // 10 apps in 9 s: 0.9 s per app.
        assertEquals(0.9, TaiFeatureCheck.figure(TaiFunction.APP_CATEGORIES, TaiFeatureCheck.perSecond(10, 9_000L)), 1e-9);
        // The first sound after 300 ms.
        assertEquals(0.3, TaiFeatureCheck.figure(TaiFunction.READ_ALOUD, TaiFeatureCheck.firstSoundSpeed(300L)), 1e-9);
        assertEquals(64.0, TaiFeatureCheck.figure(TaiFunction.EMBEDDINGS, TaiFeatureCheck.perSecond(64, 1_000L)), 1e-9);
        assertEquals(14.0, TaiFeatureCheck.figure(TaiFunction.ASSISTANT, 14.0), 1e-9);
        assertEquals(0.0, TaiFeatureCheck.figure(TaiFunction.TIDY_DICTATION, 0.0), 1e-9);
        assertEquals(0.0, TaiFeatureCheck.cleanupSpeed(150, 0L), 1e-9);
    }

    @Test
    public void verdictsFollowEachFeaturesLines() {
        // Cleanup, from pong: E2B with speculative decoding 3.7 s a minute, E4B without it 14.7 s.
        assertEquals(TaiBenchStats.VERDICT_SMOOTH, TaiFeatureCheck.verdict(TaiFunction.TIDY_DICTATION, 60.0 / 3.7));
        assertEquals(TaiBenchStats.VERDICT_SMOOTH, TaiFeatureCheck.verdict(TaiFunction.TIDY_DICTATION, 60.0 / 8.0));
        assertEquals(TaiBenchStats.VERDICT_USABLE, TaiFeatureCheck.verdict(TaiFunction.TIDY_DICTATION, 60.0 / 14.7));
        assertEquals(TaiBenchStats.VERDICT_SLOW, TaiFeatureCheck.verdict(TaiFunction.TIDY_DICTATION, 60.0 / 25.0));
        // App sorting: E2B 0.7 s per app, E4B 1.4 s.
        assertEquals(TaiBenchStats.VERDICT_SMOOTH, TaiFeatureCheck.verdict(TaiFunction.APP_CATEGORIES, 1.0 / 0.7));
        assertEquals(TaiBenchStats.VERDICT_USABLE, TaiFeatureCheck.verdict(TaiFunction.APP_CATEGORIES, 1.0 / 1.4));
        assertEquals(TaiBenchStats.VERDICT_SLOW, TaiFeatureCheck.verdict(TaiFunction.APP_CATEGORIES, 1.0 / 4.0));
        // The chat features use the bench's decode lines.
        assertEquals(TaiBenchStats.VERDICT_SMOOTH, TaiFeatureCheck.verdict(TaiFunction.DAWN_CHAT, TaiBenchStats.SMOOTH_DECODE_TPS));
        assertEquals(TaiBenchStats.VERDICT_USABLE, TaiFeatureCheck.verdict(TaiFunction.ASSISTANT, TaiBenchStats.USABLE_DECODE_TPS));
        assertEquals(TaiBenchStats.VERDICT_SLOW, TaiFeatureCheck.verdict(TaiFunction.ASSISTANT, 2.0));
        assertEquals(TaiBenchStats.VERDICT_SMOOTH, TaiFeatureCheck.verdict(TaiFunction.READ_ALOUD, TaiFeatureCheck.firstSoundSpeed(300L)));
        assertEquals(TaiBenchStats.VERDICT_SLOW, TaiFeatureCheck.verdict(TaiFunction.READ_ALOUD, TaiFeatureCheck.firstSoundSpeed(2_000L)));
        assertEquals(TaiBenchStats.VERDICT_USABLE, TaiFeatureCheck.verdict(TaiFunction.EMBEDDINGS, 8.0));
        assertNull(TaiFeatureCheck.verdict(TaiFunction.EMBEDDINGS, 0.0));
    }

    // -------------------------------------------------------------------------------- sanity

    @Test
    public void aSortAnswerIsOneOfTheCategoryIds() {
        List<String> slugs = Arrays.asList("social", "photo_video", "other");
        assertTrue(TaiFeatureCheck.sortAnswerOk("social", slugs));
        assertTrue(TaiFeatureCheck.sortAnswerOk("  Photo_Video.\n", slugs));
        assertTrue(TaiFeatureCheck.sortAnswerOk("`other`", slugs));
        assertFalse(TaiFeatureCheck.sortAnswerOk("The category is social.", slugs));
        assertFalse(TaiFeatureCheck.sortAnswerOk("ssssssss", slugs));
        assertFalse(TaiFeatureCheck.sortAnswerOk("", slugs));
        assertFalse(TaiFeatureCheck.sortAnswerOk(null, slugs));
    }

    @Test
    public void aCleanupAnswerIsTextOfASaneLengthWithNoThinking() {
        String dictation = "so um the build is green again and uh the release goes out on monday";
        assertTrue(TaiFeatureCheck.cleanupOutputOk(dictation, "The build is green again, and the release goes out on Monday."));
        assertFalse(TaiFeatureCheck.cleanupOutputOk(dictation, " "));
        assertFalse(TaiFeatureCheck.cleanupOutputOk(dictation, null));
        assertFalse(TaiFeatureCheck.cleanupOutputOk(dictation, "<think>tidy it</think>The build is green."));
        // A tenth of the text is a dropped dictation; four times it is an answer, not a tidy.
        assertFalse(TaiFeatureCheck.cleanupOutputOk(dictation, "Green."));
        StringBuilder long_ = new StringBuilder();
        for (int i = 0; i < 4; i++) long_.append(dictation).append(' ');
        assertFalse(TaiFeatureCheck.cleanupOutputOk(dictation, long_.toString()));
    }

    // ----------------------------------------------------------------------------- staleness

    @Test
    public void aResultIsTiedToItsFileAndRuntime() {
        assertEquals("sha256:abc", TaiFeatureCheck.fileKey("ABC", 10L, 20L));
        assertEquals("size:10:mtime:20", TaiFeatureCheck.fileKey(null, 10L, 20L));
        assertEquals("size:10:mtime:20", TaiFeatureCheck.fileKey(" ", 10L, 20L));
        String key = TaiFeatureCheck.stalenessKey(TaiFeatureCheck.fileKey(null, 10L, 20L), "litert-lm 0.18.0");
        assertFalse(TaiFeatureCheck.isStale(key, key));
        // A new runtime, a new file, or either not known: stale.
        assertTrue(TaiFeatureCheck.isStale(key, TaiFeatureCheck.stalenessKey(TaiFeatureCheck.fileKey(null, 10L, 20L), "litert-lm 0.19.0")));
        assertTrue(TaiFeatureCheck.isStale(key, TaiFeatureCheck.stalenessKey(TaiFeatureCheck.fileKey(null, 10L, 21L), "litert-lm 0.18.0")));
        assertTrue(TaiFeatureCheck.isStale(key, null));
        assertTrue(TaiFeatureCheck.isStale("", key));
    }

    // ---------------------------------------------------------------------------- GPU verdict

    @Test
    public void theGpuVerdictFollowsTheGpuRuns() {
        // Right answers on the GPU: verified.
        assertEquals(State.VERIFIED, TaiFeatureCheck.gpuOutcome(Arrays.asList(
            new Outcome(GPU, false, true), new Outcome(CPU, false, true))));
        // A crash on the GPU: failed, whatever else ran.
        assertEquals(State.FAILED, TaiFeatureCheck.gpuOutcome(Arrays.asList(
            new Outcome(GPU, false, true), new Outcome(GPU, true, false))));
        // Wrong on the GPU and right on the CPU: the GPU is wrong.
        assertEquals(State.FAILED, TaiFeatureCheck.gpuOutcome(Arrays.asList(
            new Outcome(GPU, false, false), new Outcome(CPU, false, true))));
        // Wrong everywhere: the model, not the GPU; and no GPU run says nothing.
        assertEquals(State.UNKNOWN, TaiFeatureCheck.gpuOutcome(Arrays.asList(
            new Outcome(GPU, false, false), new Outcome(CPU, false, false))));
        assertEquals(State.UNKNOWN, TaiFeatureCheck.gpuOutcome(Collections.singletonList(new Outcome("CPU", false, true))));
        // Speculative on: CPU+spec and GPU+spec wrong, CPU-spec right. Nothing like-for-like proves the GPU.
        assertEquals(State.UNKNOWN, TaiFeatureCheck.gpuOutcome(Arrays.asList(
            new Outcome(CPU, false, false, true), new Outcome(GPU, false, false, true), new Outcome(CPU, false, true, false))));
        // The same setting on both processors still convicts the GPU.
        assertEquals(State.FAILED, TaiFeatureCheck.gpuOutcome(Arrays.asList(
            new Outcome(CPU, false, true, true), new Outcome(GPU, false, false, true))));
        // A GPU load the self-test moved to the CPU ran on the CPU.
        assertEquals(State.UNKNOWN, TaiFeatureCheck.gpuOutcome(Collections.singletonList(new Outcome(CPU, false, false))));
    }

    @Test
    public void theLatestGpuResultWins() {
        assertEquals(State.FAILED, TaiFeatureCheck.nextVerdict(State.VERIFIED, State.FAILED));
        assertEquals(State.VERIFIED, TaiFeatureCheck.nextVerdict(State.FAILED, State.VERIFIED));
        assertEquals(State.VERIFIED, TaiFeatureCheck.nextVerdict(State.VERIFIED, State.UNKNOWN));
        assertEquals(State.UNKNOWN, TaiFeatureCheck.nextVerdict(State.UNKNOWN, State.UNKNOWN));
    }

    @Test
    public void aLoadResultNamesTheAcceleratorItCameUpOn() {
        assertEquals(GPU, TaiFeatureCheck.acceleratorOf("GPU", CPU));
        assertEquals(GPU, TaiFeatureCheck.acceleratorOf("opencl", CPU));
        assertEquals(CPU, TaiFeatureCheck.acceleratorOf("CPU", GPU));
        assertEquals(GPU, TaiFeatureCheck.acceleratorOf("", "GPU"));
        assertEquals(CPU, TaiFeatureCheck.acceleratorOf(null, CPU));
    }

    // ------------------------------------------------------------------------------- records

    static TaiFeatureCheck.Measurement measurement(String accelerator, boolean speculative, double speed) {
        TaiFeatureCheck.Measurement m = new TaiFeatureCheck.Measurement();
        m.feature = TaiFunction.TIDY_DICTATION;
        m.modelId = E2B;
        m.backend = LITERT;
        m.accelerator = accelerator;
        m.ranOn = accelerator;
        m.speculative = speculative;
        m.speculativeRan = speculative ? Boolean.TRUE : null;
        m.speed = speed;
        m.decodeTps = speed * 2.0;
        m.loadMs = 4_000L;
        m.fits = true;
        m.passed = true;
        m.staleKey = "size:1:mtime:2|litert-lm 0.18.0";
        m.timestamp = 1_700_000_000_000L;
        return m;
    }

    @Test
    public void aRecordReadsBackAsEvidence() throws Exception {
        TaiFeatureCheck.Measurement m = measurement(GPU, true, 10.0);
        JSONObject record = TaiFeatureCheck.record(m);
        assertEquals(6.0, record.getDouble("figure"), 1e-9);
        assertEquals(TaiBenchStats.VERDICT_SMOOTH, record.getString("verdict"));
        assertEquals(TaiFeatureCheck.keyOf(TaiFunction.TIDY_DICTATION, E2B, LITERT, GPU, true), TaiFeatureCheck.keyOf(record));

        TaiEvidence.FeatureResult fresh = TaiFeatureCheck.resultOf(record, m.staleKey);
        assertNotNull(fresh);
        assertEquals(TaiFunction.TIDY_DICTATION, fresh.feature);
        assertEquals(GPU, fresh.accelerator);
        assertTrue(fresh.speculative);
        assertEquals(Boolean.TRUE, fresh.speculativeRan);
        assertEquals(10.0, fresh.speed, 1e-9);
        assertEquals(20.0, fresh.decodeTps, 1e-9);
        assertTrue(fresh.passed);
        assertFalse(fresh.stale);
        assertTrue(TaiFeatureCheck.resultOf(record, "sha256:other|litert-lm 0.18.0").stale);
    }

    @Test
    public void aRunCountsOnlyWhenItCompletedFittedAndAnsweredRight() throws Exception {
        TaiFeatureCheck.Measurement wrong = measurement(GPU, false, 10.0);
        wrong.passed = false;
        JSONObject wrongRecord = TaiFeatureCheck.record(wrong);
        assertTrue(wrongRecord.isNull("verdict"));
        assertFalse(TaiFeatureCheck.resultOf(wrongRecord, wrong.staleKey).passed);

        TaiFeatureCheck.Measurement noRoom = measurement(GPU, false, 0.0);
        noRoom.fits = false;
        noRoom.status = "skipped:insufficient_memory";
        assertFalse(TaiFeatureCheck.resultOf(TaiFeatureCheck.record(noRoom), noRoom.staleKey).passed);

        // A GPU load that came up on the CPU measured the CPU.
        TaiFeatureCheck.Measurement fellBack = measurement(GPU, false, 10.0);
        fellBack.ranOn = CPU;
        assertEquals(CPU, TaiFeatureCheck.resultOf(TaiFeatureCheck.record(fellBack), fellBack.staleKey).accelerator);

        // Asked for speculative decoding and it never ran: on record, so the plan stops asking.
        TaiFeatureCheck.Measurement neverRan = measurement(CPU, true, 10.0);
        neverRan.speculativeRan = Boolean.FALSE;
        assertEquals(Boolean.FALSE, TaiFeatureCheck.resultOf(TaiFeatureCheck.record(neverRan), neverRan.staleKey).speculativeRan);
        // Not asked: nothing to say.
        assertNull(TaiFeatureCheck.resultOf(TaiFeatureCheck.record(measurement(CPU, false, 10.0)), "x").speculativeRan);
    }

    @Test
    public void aRecordOfAnotherVersionOrFeatureIsNotEvidence() throws Exception {
        JSONObject record = TaiFeatureCheck.record(measurement(GPU, false, 10.0));
        assertNull(TaiFeatureCheck.resultOf(new JSONObject(record.toString()).put("version", 99), "x"));
        assertNull(TaiFeatureCheck.resultOf(new JSONObject(record.toString()).put("feature", "wallpaper"), "x"));
    }
}
