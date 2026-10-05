package com.termux.ai;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Fixtures are measurements from a Nothing Phone 2 (12 GB class, 11 GiB usable) loading
 * gemma-4-e4b, and the smaller phones the launcher has to keep working on.
 */
public class TaiLoadBudgetTest {

    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;
    private static final long PONG_TOTAL = 11_530_736L * 1024L;     // MemTotal on the phone
    private static final long E4B = 3_659_530_240L;                 // over 3 GB: never a CPU fallback
    private static final long E2B = 2_588_147_712L;
    private static final List<String> GPU_THEN_CPU = Arrays.asList("gpu", "cpu");
    /** MemoryInfo.threshold on pong (dumpsys activity oom, 2026-09-24); only whether the seed margin applies now. */
    private static final long PONG_THRESHOLD = 315_000_000L;
    /** The 12 GB class's hold and peak floors, which pong's plans are made against. */
    private static final long HOLD = TaiLoadBudget.holdFloorBytes(TaiLoadBudget.ramClassBytes(PONG_TOTAL));
    private static final long PEAK = TaiLoadBudget.peakFloorBytes(TaiLoadBudget.ramClassBytes(PONG_TOTAL));
    private static final List<TaiResidency.Entry> NOTHING = Collections.emptyList();

    /** The legacy request: hold floor, seed estimate without margin, automatic window, nothing to evict. */
    private static TaiLoadBudget.Plan plan(long file, long total, long available, int cap) {
        return TaiLoadBudget.plan(new TaiLoadBudget.Request(TaiModelSpec.BACKEND_LITERT_LM, file, false,
            total, available, GPU_THEN_CPU, cap, null, 0));
    }

    /** A history that has measured the CPU, whatever the window, and nothing else. */
    private static TaiLoadBudget.History cpuMeasured(long bytes) {
        return (accelerator, context) -> "cpu".equals(accelerator) ? bytes : 0L;
    }

    // --- Floors: policy by RAM class, with penalties ---

    @Test
    public void floorsFollowTheRamClass() {
        // peak: 0.75 GiB up to 12 GB, 1 GiB from 16 GB; hold: 1.0, 1.25, 1.25, 1.5, 2.0 GiB.
        assertEquals(768L * MIB, TaiLoadBudget.peakFloorBytes(4L * GIB));
        assertEquals(768L * MIB, TaiLoadBudget.peakFloorBytes(6L * GIB));
        assertEquals(768L * MIB, TaiLoadBudget.peakFloorBytes(8L * GIB));
        assertEquals(768L * MIB, TaiLoadBudget.peakFloorBytes(10L * GIB));
        assertEquals(768L * MIB, TaiLoadBudget.peakFloorBytes(12L * GIB));
        assertEquals(GIB, TaiLoadBudget.peakFloorBytes(16L * GIB));
        assertEquals(GIB, TaiLoadBudget.peakFloorBytes(24L * GIB));

        assertEquals(GIB, TaiLoadBudget.holdFloorBytes(3L * GIB));
        assertEquals(GIB, TaiLoadBudget.holdFloorBytes(4L * GIB));
        assertEquals(1280L * MIB, TaiLoadBudget.holdFloorBytes(6L * GIB));
        assertEquals(1280L * MIB, TaiLoadBudget.holdFloorBytes(8L * GIB));
        assertEquals(1536L * MIB, TaiLoadBudget.holdFloorBytes(10L * GIB));
        assertEquals(1536L * MIB, TaiLoadBudget.holdFloorBytes(12L * GIB));
        assertEquals(2L * GIB, TaiLoadBudget.holdFloorBytes(16L * GIB));
        assertEquals(2L * GIB, TaiLoadBudget.holdFloorBytes(32L * GIB));
        // Unknown RAM is the smallest class, not zero.
        assertEquals(GIB, TaiLoadBudget.holdFloorBytes(0L));
        assertEquals(768L * MIB, TaiLoadBudget.peakFloorBytes(0L));
    }

    @Test
    public void thePenaltiesAddToTheFloorsAsTheTableSays() {
        long twelve = 12L * GIB;
        TaiLoadBudget.Conditions swapLow = new TaiLoadBudget.Conditions(true, true);
        TaiLoadBudget.Conditions behind = new TaiLoadBudget.Conditions(false, false);
        TaiLoadBudget.Conditions both = new TaiLoadBudget.Conditions(true, false);

        assertEquals(1536L * MIB, TaiLoadBudget.floorBytes(twelve, false, TaiLoadBudget.Conditions.NORMAL));
        assertEquals(768L * MIB, TaiLoadBudget.floorBytes(twelve, true, TaiLoadBudget.Conditions.NORMAL));
        // Swap nearly full: +0.5 GiB on either floor.
        assertEquals(1536L * MIB + 512L * MIB, TaiLoadBudget.floorBytes(twelve, false, swapLow));
        assertEquals(768L * MIB + 512L * MIB, TaiLoadBudget.floorBytes(twelve, true, swapLow));
        // The launcher out of front: +0.25 GiB on the hold floor only.
        assertEquals(1536L * MIB + 256L * MIB, TaiLoadBudget.floorBytes(twelve, false, behind));
        assertEquals(768L * MIB, TaiLoadBudget.floorBytes(twelve, true, behind));
        assertEquals(1536L * MIB + 512L * MIB + 256L * MIB, TaiLoadBudget.floorBytes(twelve, false, both));
        assertEquals(768L * MIB + 512L * MIB, TaiLoadBudget.floorBytes(twelve, true, both));
    }

    @Test
    public void aPlanCarriesItsFloorWithTheConditionsTheCallerRead() {
        TaiLoadBudget.Request request = new TaiLoadBudget.Request(TaiModelSpec.BACKEND_LITERT_LM, E2B, false,
            PONG_TOTAL, 9_000_000_000L, GPU_THEN_CPU, 4096, null, 0);
        assertEquals(HOLD, TaiLoadBudget.plan(request).reserveBytes);
        assertEquals(HOLD + 512L * MIB + 256L * MIB,
            TaiLoadBudget.plan(request.withConditions(new TaiLoadBudget.Conditions(true, false))).reserveBytes);
    }

    // --- The ladder ---

    /** The freeze: auto chose 32k on this phone. With its usual free memory it gets the GPU at 4k. */
    @Test
    public void aTwelveGigabytePhoneWithItsUsualFreeMemoryRunsE4bOnTheGpuAt4k() {
        TaiLoadBudget.Plan plan = plan(E4B, PONG_TOTAL, 5_800_000_000L, 32_768);
        assertTrue(plan.fits);
        assertEquals("gpu", plan.accelerator);
        assertEquals(4096, plan.contextWindow);
    }

    /**
     * For gemma-4 the CPU saves little or nothing in memory and costs 3-4 times the time, and an
     * unmeasured guess is what kept choosing it: short of memory, E4B is refused so the caller
     * walks its fallback model.
     */
    @Test
    public void aTwelveGigabytePhoneShortOfMemoryRefusesE4bInsteadOfFallingToTheCpu() {
        TaiLoadBudget.Plan plan = plan(E4B, PONG_TOTAL, 4_000_000_000L, 32_768);
        assertFalse(plan.fits);
        assertEquals("gpu", plan.accelerator);
        assertEquals(4096, plan.contextWindow);
    }

    /** Step 3 is taken only when the other accelerator has a measured cost below the refused one's. */
    @Test
    public void theOtherAcceleratorIsTakenOnlyWithAMeasuredCostBelowTheRefusedOne() {
        // E2B on the GPU needs ~2.5 GB; free here is 4.0 GB less the 1.61 GB hold floor.
        TaiLoadBudget.Plan unmeasured = plan(E2B, PONG_TOTAL, 4_000_000_000L, 32_768);
        assertFalse(unmeasured.fits);

        TaiLoadBudget.Plan cheaper = TaiLoadBudget.plan(request(E2B, PONG_TOTAL, 4_000_000_000L, GPU_THEN_CPU,
            32_768, false, 0L, cpuMeasured(1_000_000_000L), NOTHING));
        assertTrue(cheaper.fits);
        assertEquals("cpu", cheaper.accelerator);
        assertEquals(TaiLoadBudget.SOURCE_MEASURED, cheaper.estimateSource);

        // A measured CPU cost above what the GPU was refused for is no reason to move.
        TaiLoadBudget.Plan dearer = TaiLoadBudget.plan(request(E2B, PONG_TOTAL, 4_000_000_000L, GPU_THEN_CPU,
            32_768, false, 0L, cpuMeasured(3_000_000_000L), NOTHING));
        assertFalse(dearer.fits);
        assertEquals("gpu", dearer.accelerator);
    }

    @Test
    public void aDeviceWithoutAGpuMayTryTheCpuUnmeasured() {
        TaiLoadBudget.Request request = request(E2B, PONG_TOTAL, 4_000_000_000L, GPU_THEN_CPU, 32_768, false, 0L,
            TaiLoadBudget.NO_HISTORY, NOTHING);
        assertFalse(TaiLoadBudget.plan(request).fits);
        TaiLoadBudget.Plan gpuless = TaiLoadBudget.plan(request.withGpuless(true));
        assertTrue(gpuless.fits);
        assertEquals("cpu", gpuless.accelerator);
        assertEquals(4096, gpuless.contextWindow);
    }

    /** E4B (3.66 GB) is over the 3 GB line: no CPU step, measured or not, GPU-less or not. */
    @Test
    public void aLiteRtFileOverThreeGigabytesIsNeverTakenToTheCpuAsAFallback() {
        TaiLoadBudget.Request request = request(E4B, PONG_TOTAL, 4_000_000_000L, GPU_THEN_CPU, 32_768, false, 0L,
            cpuMeasured(500_000_000L), NOTHING);
        TaiLoadBudget.Plan measured = TaiLoadBudget.plan(request);
        assertFalse(measured.fits);
        assertEquals("gpu", measured.accelerator);
        assertFalse(TaiLoadBudget.plan(request.withGpuless(true)).fits);

        // The same history on a file under the line does take it.
        assertTrue(E2B < TaiLoadBudget.LITERT_CPU_FALLBACK_MAX_FILE_BYTES);
        assertTrue(TaiLoadBudget.plan(request(E2B, PONG_TOTAL, 4_000_000_000L, GPU_THEN_CPU, 32_768, false, 0L,
            cpuMeasured(500_000_000L), NOTHING)).fits);

        // The rule is about the fallback step: a caller that asks for the CPU alone gets it planned.
        TaiLoadBudget.Plan only = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 9_000_000_000L,
            Collections.singletonList("cpu"), 4096, false, 0L, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertTrue(only.fits);
        assertEquals("cpu", only.accelerator);
    }

    /** The CPU fallback never takes a larger window: 8k on the CPU took the phone's network down. */
    @Test
    public void theFallbackAcceleratorIsHeldToTheFloor() {
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(request(E2B, PONG_TOTAL, 4_000_000_000L, GPU_THEN_CPU,
            32_768, true, 0L, cpuMeasured(1_000_000_000L), NOTHING));
        assertEquals("cpu", plan.accelerator);
        assertEquals(4096, plan.contextWindow);
    }

    @Test
    public void measuredLoadsThatFroze_areNeverPlanned() {
        for (long free = 4_000_000_000L; free <= 7_000_000_000L; free += 250_000_000L) {
            TaiLoadBudget.Plan plan = plan(E4B, PONG_TOTAL, free, 32_768);
            assertTrue("free=" + free, plan.contextWindow < 16_384);
        }
    }

    @Test
    public void anEightGigabytePhoneRunsE2bOnTheGpuWhenItFits() {
        // 8 GB class: hold floor 1.25 GiB; E2B on the GPU at 4k is ~2.5 GB.
        TaiLoadBudget.Plan plan = plan(E2B, (long) (7.3 * GIB), 4_000_000_000L, 32_768);
        assertTrue(plan.fits);
        assertEquals("gpu", plan.accelerator);
        assertEquals(4096, plan.contextWindow);
        assertEquals(1280L * MIB, plan.reserveBytes);
    }

    @Test
    public void aSixGigabytePhoneWithLittleFreeIsRefusedRatherThanFrozen() {
        TaiLoadBudget.Plan plan = plan(E2B, (long) (5.5 * GIB), 2_000_000_000L, 32_768);
        assertFalse(plan.fits);
        assertTrue(plan.neededFreeBytes() > plan.availableBytes);
    }

    /** An automatic GPU load stops at 4k however much is free; only an explicit window goes above. */
    @Test
    public void plentyOfFreeMemoryEarnsALargerWindowOnlyWhenTheWindowIsExplicit() {
        TaiLoadBudget.Plan auto = plan(E2B, 16L * GIB, 12_000_000_000L, 32_768);
        assertEquals("gpu", auto.accelerator);
        assertEquals(TaiLoadBudget.GPU_AUTO_CONTEXT, auto.contextWindow);

        TaiLoadBudget.Plan explicit = TaiLoadBudget.plan(request(E2B, 16L * GIB, 12_000_000_000L, GPU_THEN_CPU,
            32_768, true, 0L, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertEquals("gpu", explicit.accelerator);
        assertEquals(32_768, explicit.contextWindow);
    }

    /** pong after LiteRT-LM 0.17.1: 8k cost ~0.8 GB more than 4k and decoded slower, so auto picks 4k on the GPU. */
    @Test
    public void anAutomaticGpuLoadIsCappedAt4kAndAnExplicit8kIsHonoured() {
        TaiLoadBudget.Plan auto = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 9_000_000_000L, GPU_THEN_CPU,
            16_384, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertEquals("gpu", auto.accelerator);
        assertEquals(4096, auto.contextWindow);

        TaiLoadBudget.Plan explicit = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 9_000_000_000L, GPU_THEN_CPU,
            8192, true, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertEquals("gpu", explicit.accelerator);
        assertEquals(8192, explicit.contextWindow);
    }

    /** The CPU is a fallback, not a place for the GPU cap to send larger windows: it still runs at the floor. */
    @Test
    public void theGpuCapDoesNotHandTheCpuALargerWindow() {
        // GPU 4k does not fit here (~3.1 GB with the seed margin); a measured CPU takes it at the floor.
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(request(E2B, PONG_TOTAL, 4_500_000_000L, GPU_THEN_CPU,
            16_384, false, PONG_THRESHOLD, cpuMeasured(800_000_000L), NOTHING));
        assertTrue(plan.fits);
        assertEquals("cpu", plan.accelerator);
        assertEquals(4096, plan.contextWindow);
    }

    @Test
    public void aSmallRequestIsNotGrown() {
        assertEquals(2048, plan(E4B, PONG_TOTAL, 9_000_000_000L, 2048).contextWindow);
    }

    @Test
    public void aLoadKilledMidwayIsNotRepeatedAsItWas() {
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(new TaiLoadBudget.Request(TaiModelSpec.BACKEND_LITERT_LM,
            E4B, false, PONG_TOTAL, 9_000_000_000L, GPU_THEN_CPU, 32_768, "gpu", 16_384));
        assertEquals("gpu", plan.accelerator);
        assertTrue(plan.contextWindow <= 8192);
    }

    /** A GPU that died at the floor cannot be tried again, so the CPU is the only way left, unmeasured or not. */
    @Test
    public void aLoadKilledAtTheFloorMovesToTheNextAcceleratorWithoutAMeasurement() {
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(new TaiLoadBudget.Request(TaiModelSpec.BACKEND_LITERT_LM,
            E2B, false, PONG_TOTAL, 5_800_000_000L, GPU_THEN_CPU, 32_768, "gpu", 4096));
        assertEquals("cpu", plan.accelerator);
        // Not for a file over 3 GB: the caller walks its fallback model.
        assertFalse(TaiLoadBudget.plan(new TaiLoadBudget.Request(TaiModelSpec.BACKEND_LITERT_LM,
            E4B, false, PONG_TOTAL, 5_800_000_000L, GPU_THEN_CPU, 32_768, "gpu", 4096)).fits);
    }

    @Test
    public void anExplicitAcceleratorIsTheOnlyOneTried() {
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(new TaiLoadBudget.Request(TaiModelSpec.BACKEND_LITERT_LM,
            E4B, false, PONG_TOTAL, 4_000_000_000L, Collections.singletonList("gpu"), 32_768, null, 0));
        assertFalse(plan.fits);
        assertEquals("gpu", plan.accelerator);
    }

    @Test
    public void unknownFreeMemoryFallsBackToTheFloorRatherThanTheTier() {
        TaiLoadBudget.Plan plan = plan(E4B, PONG_TOTAL, 0L, 32_768);
        assertTrue(plan.fits);
        assertFalse(plan.measured);
        assertEquals(4096, plan.contextWindow);
    }

    // --- Estimates: measured worst case over seed, and what a seed counts as new pressure ---

    @Test
    public void aMeasuredWorstCaseReplacesTheSeedEstimateWithTenPercentHeadroom() {
        long worst = 3_200_000_000L;
        TaiLoadBudget.History history = (accelerator, context) -> "gpu".equals(accelerator) && context == 4096 ? worst : 0L;
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 9_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, history, NOTHING));
        assertEquals(TaiLoadBudget.SOURCE_MEASURED, plan.estimateSource);
        assertEquals(worst + worst / 10L, plan.estimatedBytes);
        assertEquals(0L, plan.marginBytes);
        assertEquals(0L, plan.reclaimableBytes);
        assertEquals(worst + worst / 10L + HOLD, plan.neededFreeBytes());
    }

    @Test
    public void withoutHistoryTheSeedEstimateIsUsedAndSaysSo() {
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 9_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertEquals(TaiLoadBudget.SOURCE_RATIO, plan.estimateSource);
        assertEquals(TaiLoadBudget.estimateBytes(TaiModelSpec.BACKEND_LITERT_LM, "gpu", E4B, false, 4096), plan.estimatedBytes);
    }

    /** Seeds: LiteRT CPU 0.6 of the file (the card's resident figure), GPU three quarters, MNN the whole file. */
    @Test
    public void theSeedsPerAccelerator() {
        long perToken = E4B / TaiLoadBudget.FILE_BYTES_PER_TOKEN_DIVISOR;
        assertEquals(E4B / 100L * 60L + perToken * 4096L,
            TaiLoadBudget.estimateBytes(TaiModelSpec.BACKEND_LITERT_LM, "cpu", E4B, false, 4096));
        assertEquals(E4B * 3L / 4L + perToken * 4096L,
            TaiLoadBudget.estimateBytes(TaiModelSpec.BACKEND_LITERT_LM, "gpu", E4B, false, 4096));
        assertEquals(E4B + perToken * 4096L,
            TaiLoadBudget.estimateBytes(TaiModelSpec.BACKEND_MNN_LLM, "cpu", E4B, false, 4096));
        // The encoders' share is a tenth of the file on either.
        assertEquals(E4B * 3L / 4L + E4B / 10L + perToken * 4096L,
            TaiLoadBudget.estimateBytes(TaiModelSpec.BACKEND_LITERT_LM, "gpu", E4B, true, 4096));
        // The mapped weights stay informational: reclaimable on a LiteRT CPU load only.
        assertEquals(E4B, TaiLoadBudget.reclaimableBytes(TaiModelSpec.BACKEND_LITERT_LM, "cpu", E4B));
        assertEquals(0L, TaiLoadBudget.reclaimableBytes(TaiModelSpec.BACKEND_LITERT_LM, "gpu", E4B));
        assertEquals(0L, TaiLoadBudget.reclaimableBytes(TaiModelSpec.BACKEND_MNN_LLM, "cpu", E4B));
        TaiLoadBudget.Estimate cpu = TaiLoadBudget.estimate(TaiModelSpec.BACKEND_LITERT_LM, "cpu", E4B, false, 4096, TaiLoadBudget.NO_HISTORY);
        assertEquals(E4B, cpu.reclaimableBytes);
        assertEquals(TaiLoadBudget.SOURCE_RATIO, cpu.source);
    }

    /** MNN's slope is the architecture's own KV: Qwen3 0.6B is 28 layers x 8 kv heads x 128 x 2 x 2 bytes = 112 KiB. */
    @Test
    public void anMnnSlopeComesFromTheArchitectureWhenConfigSaysSo() throws Exception {
        JSONObject qwen = new JSONObject("{\"num_hidden_layers\":28,\"num_attention_heads\":16,"
            + "\"num_key_value_heads\":8,\"head_dim\":128,\"hidden_size\":1024}");
        assertEquals(114_688L, TaiLoadBudget.kvBytesPerToken(qwen));
        // 8-bit KV (attention_mode 10 or 2) halves it; the default mode 8 does not.
        assertEquals(57_344L, TaiLoadBudget.kvBytesPerToken(new JSONObject(qwen.toString()).put("attention_mode", 10)));
        assertEquals(57_344L, TaiLoadBudget.kvBytesPerToken(new JSONObject(qwen.toString()).put("attention_mode", 2)));
        assertEquals(114_688L, TaiLoadBudget.kvBytesPerToken(new JSONObject(qwen.toString()).put("attention_mode", 8)));
        // Head size from the hidden size, kv heads from the attention heads, when not stated.
        assertEquals(24L * 16L * 64L * 4L, TaiLoadBudget.kvBytesPerToken(new JSONObject(
            "{\"num_hidden_layers\":24,\"num_attention_heads\":16,\"hidden_size\":1024}")));
        // Not enough said: unknown, which keeps file/19000.
        assertEquals(0L, TaiLoadBudget.kvBytesPerToken(new JSONObject("{\"hidden_size\":1024}")));
        assertEquals(0L, TaiLoadBudget.kvBytesPerToken(null));
        assertEquals(0L, TaiLoadBudget.mnnKvBytesPerToken(null));
        assertEquals(0L, TaiLoadBudget.mnnKvBytesPerToken("/nonexistent/model/config.json"));

        long file = 451_000_000L;
        assertEquals(114_688L, TaiLoadBudget.seedSlopeBytes(TaiModelSpec.BACKEND_MNN_LLM, file, 114_688L));
        // file/19000 is ~5x too small for MNN; it stays the fallback and the LiteRT figure.
        assertEquals(file / 19_000L, TaiLoadBudget.seedSlopeBytes(TaiModelSpec.BACKEND_MNN_LLM, file, 0L));
        assertEquals(file / 19_000L, TaiLoadBudget.seedSlopeBytes(TaiModelSpec.BACKEND_LITERT_LM, file, 114_688L));
        assertEquals(file + 114_688L * 4096L,
            TaiLoadBudget.estimateBytes(TaiModelSpec.BACKEND_MNN_LLM, "cpu", file, false, 4096, 114_688L));
    }

    @Test
    public void anMnnPlanUsesTheArchitectureSlopeWhenTheRequestCarriesIt() {
        TaiLoadBudget.Request request = new TaiLoadBudget.Request(TaiModelSpec.BACKEND_MNN_LLM, 451_000_000L, false,
            PONG_TOTAL, 9_000_000_000L, Collections.singletonList("cpu"), 8192, null, 0);
        long file = 451_000_000L;
        assertEquals(file + (file / 19_000L) * 8192L, TaiLoadBudget.plan(request).estimatedBytes);
        assertEquals(file + 114_688L * 8192L, TaiLoadBudget.plan(request.withKvBytesPerToken(114_688L)).estimatedBytes);
    }

    @Test
    public void aMeasuredSampleCountsForLiteRtCpuToo() {
        TaiLoadBudget.History measured = (accelerator, context) -> 400_000_000L;
        TaiLoadBudget.Estimate cpu = TaiLoadBudget.estimate(TaiModelSpec.BACKEND_LITERT_LM, "cpu", E2B, false, 4096, measured);
        assertEquals(TaiLoadBudget.SOURCE_MEASURED, cpu.source);
        assertEquals(440_000_000L, cpu.nonReclaimableBytes);
        assertEquals(TaiLoadBudget.SOURCE_MEASURED,
            TaiLoadBudget.estimate(TaiModelSpec.BACKEND_LITERT_LM, "gpu", E2B, false, 4096, measured).source);
        assertEquals(TaiLoadBudget.SOURCE_MEASURED,
            TaiLoadBudget.estimate(TaiModelSpec.BACKEND_MNN_LLM, "cpu", E2B, false, 4096, measured).source);
    }

    // --- Margin ---

    @Test
    public void aSeedEstimateCarriesAQuarterMarginOverTheFloorAndAMeasuredOneDoesNot() {
        TaiLoadBudget.Plan seed = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 9_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertEquals(HOLD, seed.reserveBytes);
        assertEquals(seed.estimatedBytes * TaiLoadBudget.RATIO_MARGIN_PERCENT / 100L, seed.marginBytes);
        assertEquals(seed.estimatedBytes + seed.marginBytes + HOLD, seed.neededFreeBytes());

        TaiLoadBudget.Plan measured = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 9_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, (accelerator, context) -> 3_000_000_000L, NOTHING));
        assertEquals(HOLD, measured.reserveBytes);
        assertEquals(0L, measured.marginBytes);

        // An unknown threshold applies no margin, as before.
        TaiLoadBudget.Plan legacy = plan(E4B, PONG_TOTAL, 9_000_000_000L, 4096);
        assertEquals(0L, legacy.marginBytes);
    }

    /** The daily-driver state: 5 GB free on pong. E2B gets the GPU; E4B is refused, so its caller walks to E2B. */
    @Test
    public void atFiveGigabytesFreeOnPongE2bGetsTheGpuAndE4bIsRefused() {
        TaiLoadBudget.Plan e4b = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 5_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertFalse(e4b.fits);

        TaiLoadBudget.Plan e2b = TaiLoadBudget.plan(request(E2B, PONG_TOTAL, 5_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertEquals("gpu", e2b.accelerator);
        assertTrue(e2b.fits);
        assertTrue(e2b.neededFreeBytes() < 5_000_000_000L);
    }

    // --- Eviction ---

    /** Short of memory, the plan closes idle residents in the order given before it shrinks the window. */
    @Test
    public void aLoadThatDoesNotFitEvictsIdleResidentsBeforeShrinkingTheWindow() {
        TaiResidency.Entry embedding = resident("emb", TaiResidency.Kind.EMBEDDING, 300_000_000L, false);
        TaiResidency.Entry stt = resident("whisper", TaiResidency.Kind.STT, 400_000_000L, false);
        TaiLoadBudget.Plan without = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 9_000_000_000L, Collections.singletonList("gpu"),
            8192, true, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        long available = without.neededFreeBytes() - 200_000_000L;

        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, available, Collections.singletonList("gpu"),
            8192, true, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, Arrays.asList(embedding, stt)));
        assertTrue(plan.fits);
        assertEquals(8192, plan.contextWindow);
        assertEquals(1, plan.evicted.size());
        assertSame(embedding, plan.evicted.get(0));
        assertEquals(300_000_000L, plan.evictedBytes());

        // Nothing to evict: the window shrinks instead.
        TaiLoadBudget.Plan shrunk = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, available, Collections.singletonList("gpu"),
            8192, true, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertTrue(shrunk.fits);
        assertEquals(4096, shrunk.contextWindow);
        assertTrue(shrunk.evicted.isEmpty());
    }

    @Test
    public void evictionTakesTheShortestPrefixThatCoversTheShortfall() {
        TaiResidency.Entry small = resident("emb", TaiResidency.Kind.EMBEDDING, 100_000_000L, false);
        TaiResidency.Entry stt = resident("whisper", TaiResidency.Kind.STT, 400_000_000L, false);
        TaiResidency.Entry chat = resident("qwen", TaiResidency.Kind.CHAT, 1_000_000_000L, false);
        List<TaiResidency.Entry> evicted = TaiLoadBudget.evictions(450_000_000L, Arrays.asList(small, stt, chat));
        assertEquals(Arrays.asList(small, stt), evicted);
        assertNull(TaiLoadBudget.evictions(2_000_000_000L, Arrays.asList(small, stt, chat)));
        assertTrue(TaiLoadBudget.evictions(0L, Arrays.asList(small, stt, chat)).isEmpty());
    }

    @Test
    public void aBusyResidentIsNeverEvictedEvenWhenListed() {
        TaiResidency.Entry busy = resident("emb", TaiResidency.Kind.EMBEDDING, 300_000_000L, true);
        TaiResidency.Entry idle = resident("whisper", TaiResidency.Kind.STT, 300_000_000L, false);
        List<TaiResidency.Entry> evicted = TaiLoadBudget.evictions(250_000_000L, Arrays.asList(busy, idle));
        assertEquals(Collections.singletonList(idle), evicted);
        assertNull(TaiLoadBudget.evictions(250_000_000L, Collections.singletonList(busy)));
    }

    /** When eviction cannot cover it either, the ladder still runs and then refuses. */
    @Test
    public void whenEvictionIsNotEnoughTheLadderStillRunsAndThenRefuses() {
        TaiResidency.Entry embedding = resident("emb", TaiResidency.Kind.EMBEDDING, 100_000_000L, false);
        TaiLoadBudget.Plan cpu = TaiLoadBudget.plan(request(E2B, PONG_TOTAL, 4_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, cpuMeasured(800_000_000L), Collections.singletonList(embedding)));
        assertTrue(cpu.fits);
        assertEquals("cpu", cpu.accelerator);
        assertTrue(cpu.evicted.isEmpty());

        TaiLoadBudget.Plan refused = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 1_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, Collections.singletonList(embedding)));
        assertFalse(refused.fits);
        assertTrue(refused.evicted.isEmpty());
    }

    @Test
    public void aFixedLoadEvictsIdleResidentsToo() {
        TaiResidency.Entry chat = resident("qwen", TaiResidency.Kind.CHAT, 1_000_000_000L, false);
        TaiLoadBudget.Estimate estimate = TaiLoadBudget.Estimate.ratio(240_000_000L, 0L);
        long margin = 240_000_000L * TaiLoadBudget.RATIO_MARGIN_PERCENT / 100L;
        TaiLoadBudget.Plan refused = TaiLoadBudget.planFixed(estimate, "cpu", PONG_TOTAL, HOLD + margin + 100_000_000L,
            PONG_THRESHOLD, Collections.<TaiResidency.Entry>emptyList());
        assertFalse(refused.fits);
        assertEquals(TaiLoadBudget.SOURCE_RATIO, refused.estimateSource);
        assertEquals(HOLD, refused.reserveBytes);

        TaiLoadBudget.Plan evicting = TaiLoadBudget.planFixed(estimate, "cpu", PONG_TOTAL, HOLD + margin + 100_000_000L,
            PONG_THRESHOLD, Collections.singletonList(chat));
        assertTrue(evicting.fits);
        assertEquals(Collections.singletonList(chat), evicting.evicted);

        TaiLoadBudget.Plan measured = TaiLoadBudget.planFixed(TaiLoadBudget.Estimate.measured(200_000_000L), "cpu", PONG_TOTAL,
            HOLD + 220_000_000L, PONG_THRESHOLD, Collections.<TaiResidency.Entry>emptyList());
        assertTrue(measured.fits);
        assertEquals(220_000_000L, measured.estimatedBytes);
        assertEquals(0L, measured.marginBytes);
    }

    /** An embedding interpreter has no window to shrink: it fits whole or it is refused, against the hold floor. */
    @Test
    public void aFixedLoadFitsWhenItLeavesTheHoldFloorFree() {
        long reserve = HOLD;
        TaiLoadBudget.Plan fits = TaiLoadBudget.planFixed(240_000_000L, "cpu", PONG_TOTAL, reserve + 240_000_000L);
        assertTrue(fits.fits);
        assertTrue(fits.measured);
        assertEquals("cpu", fits.accelerator);
        assertEquals(0, fits.contextWindow);
        assertEquals(240_000_000L, fits.estimatedBytes);

        TaiLoadBudget.Plan refused = TaiLoadBudget.planFixed(240_000_000L, "cpu", PONG_TOTAL, reserve + 239_999_999L);
        assertFalse(refused.fits);
        assertTrue(refused.neededFreeBytes() > refused.availableBytes);

        // With low swap and the launcher out of front the same load needs 0.75 GiB more.
        TaiLoadBudget.Plan penalised = TaiLoadBudget.planFixed(TaiLoadBudget.Estimate.ratio(240_000_000L, 0L), "cpu",
            PONG_TOTAL, reserve + 240_000_000L, 0L, Collections.<TaiResidency.Entry>emptyList(),
            new TaiLoadBudget.Conditions(true, false));
        assertFalse(penalised.fits);
        assertEquals(reserve + 512L * MIB + 256L * MIB, penalised.reserveBytes);
    }

    @Test
    public void aFixedLoadWithUnknownFreeMemoryGoesAheadUnmeasured() {
        TaiLoadBudget.Plan plan = TaiLoadBudget.planFixed(240_000_000L, "cpu", PONG_TOTAL, 0L);
        assertTrue(plan.fits);
        assertFalse(plan.measured);
    }

    @Test
    public void ramClassRoundsTheKernelsFigureUpToTheSizeThePhoneIsSoldAs() {
        assertEquals(12L * GIB, TaiLoadBudget.ramClassBytes(PONG_TOTAL));
        assertEquals(8L * GIB, TaiLoadBudget.ramClassBytes((long) (7.3 * GIB)));
        assertEquals(6L * GIB, TaiLoadBudget.ramClassBytes((long) (5.5 * GIB)));
        assertEquals(0L, TaiLoadBudget.ramClassBytes(0L));
    }

    // --- LiteRT CPU: KV cache and buffers arrive at the first prefill, after the budget has looked ---

    private static final List<String> CPU_ONLY = Collections.singletonList("cpu");

    @Test
    public void anImplicitWindowOnLiteRtCpuIsHeldToTheFloor() {
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(request(E2B, PONG_TOTAL, 9_000_000_000L, CPU_ONLY,
            32_768, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertTrue(plan.fits);
        assertEquals("cpu", plan.accelerator);
        assertEquals(TaiLoadBudget.FLOOR_CONTEXT, plan.contextWindow);
    }

    @Test
    public void anExplicitWindowOnLiteRtCpuIsHonoured() {
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(request(E2B, PONG_TOTAL, 10_000_000_000L, CPU_ONLY,
            32_768, true, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertEquals(32_768, plan.contextWindow);
    }

    @Test
    public void aModelLimitBelowTheFloorStillWinsOnLiteRtCpu() {
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(request(E2B, PONG_TOTAL, 9_000_000_000L, CPU_ONLY,
            2048, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertEquals(2048, plan.contextWindow);
    }

    @Test
    public void theCpuCapDoesNotApplyToMnn() {
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(new TaiLoadBudget.Request(TaiModelSpec.BACKEND_MNN_LLM, 500_000_000L, false,
            PONG_TOTAL, 9_000_000_000L, CPU_ONLY, 16_384, null, 0, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertEquals(16_384, plan.contextWindow);
    }

    // --- Momentary loads keep the lower peak floor ---

    private static TaiLoadBudget.Plan momentaryPlan(long available, boolean momentary) {
        return TaiLoadBudget.plan(new TaiLoadBudget.Request(TaiModelSpec.BACKEND_LITERT_LM, E4B, false,
            PONG_TOTAL, available, GPU_THEN_CPU, 4096, null, 0, false, 0L, TaiLoadBudget.NO_HISTORY, NOTHING,
            momentary));
    }

    /** The director on pong: E4B on the GPU is ~3.5 GB; the hold floor asks for more free memory than the peak floor. */
    @Test
    public void aMomentaryLoadFitsTheGpuWhereTheHoldFloorRefuses() {
        TaiLoadBudget.Plan normal = momentaryPlan(4_500_000_000L, false);
        assertFalse(normal.fits);
        assertEquals(HOLD, normal.reserveBytes);

        TaiLoadBudget.Plan momentary = momentaryPlan(4_500_000_000L, true);
        assertTrue(momentary.fits);
        assertEquals("gpu", momentary.accelerator);
        assertEquals(PEAK, momentary.reserveBytes);
        assertEquals(768L * MIB, momentary.reserveBytes);
    }

    @Test
    public void aMomentaryLoadCarriesTheSwapPenaltyButNotTheBackgroundOne() {
        TaiLoadBudget.Request request = new TaiLoadBudget.Request(TaiModelSpec.BACKEND_LITERT_LM, E4B, false,
            PONG_TOTAL, 9_000_000_000L, GPU_THEN_CPU, 4096, null, 0, false, 0L, TaiLoadBudget.NO_HISTORY, NOTHING, true);
        assertEquals(PEAK + 512L * MIB,
            TaiLoadBudget.plan(request.withConditions(new TaiLoadBudget.Conditions(true, false))).reserveBytes);
        assertEquals(PEAK, TaiLoadBudget.plan(request.withConditions(new TaiLoadBudget.Conditions(false, false))).reserveBytes);
    }

    @Test
    public void anOrdinaryLoadKeepsItsHoldFloor() {
        assertEquals(HOLD, momentaryPlan(5_300_000_000L, false).reserveBytes);
    }

    @Test
    public void theMomentaryDeadlineIsThreeMinutes() {
        assertEquals(180_000L, TaiLoadBudget.MOMENTARY_DEADLINE_MS);
    }

    private static TaiLoadBudget.Request request(long file, long total, long available, List<String> accelerators, int cap,
                                                 boolean explicitContext, long threshold, TaiLoadBudget.History history,
                                                 List<TaiResidency.Entry> evictable) {
        return new TaiLoadBudget.Request(TaiModelSpec.BACKEND_LITERT_LM, file, false, total, available, accelerators, cap,
            null, 0, explicitContext, threshold, history, evictable);
    }

    private static TaiResidency.Entry resident(String id, TaiResidency.Kind kind, long bytes, boolean busy) {
        return new TaiResidency.Entry(id, kind, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0, bytes, null, 1L, busy);
    }
}
