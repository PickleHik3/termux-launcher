package com.termux.ai;

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

    private static final long GIB = 1024L * 1024L * 1024L;
    private static final long PONG_TOTAL = 11_530_736L * 1024L;     // MemTotal on the phone
    private static final long E4B = 3_659_530_240L;
    private static final long E2B = 2_588_147_712L;
    private static final List<String> GPU_THEN_CPU = Arrays.asList("gpu", "cpu");
    /** MemoryInfo.threshold on pong (dumpsys activity oom, 2026-09-24) and the floor it gives. */
    private static final long PONG_THRESHOLD = 315_000_000L;
    private static final long PONG_FLOOR = 2L * PONG_THRESHOLD;
    private static final List<TaiResidency.Entry> NOTHING = Collections.emptyList();

    /** The pre-threshold request: old reserve, ratio estimate, automatic window, nothing to evict. */
    private static TaiLoadBudget.Plan plan(long file, long total, long available, int cap) {
        return TaiLoadBudget.plan(new TaiLoadBudget.Request(TaiModelSpec.BACKEND_LITERT_LM, file, false,
            total, available, GPU_THEN_CPU, cap, null, 0));
    }

    /** The freeze: auto chose 32k on this phone. With its usual free memory it gets the GPU at 4k. */
    @Test
    public void aTwelveGigabytePhoneWithItsUsualFreeMemoryRunsE4bOnTheGpuAt4k() {
        TaiLoadBudget.Plan plan = plan(E4B, PONG_TOTAL, 5_800_000_000L, 32_768);
        assertTrue(plan.fits);
        assertEquals("gpu", plan.accelerator);
        assertEquals(4096, plan.contextWindow);
    }

    /** The developer's condition: a 12 GB phone always runs E4B, if not on the GPU then on the CPU. */
    @Test
    public void aTwelveGigabytePhoneShortOfMemoryStillRunsE4bOnTheCpu() {
        TaiLoadBudget.Plan plan = plan(E4B, PONG_TOTAL, 4_000_000_000L, 32_768);
        assertTrue(plan.fits);
        assertEquals("cpu", plan.accelerator);
        assertEquals(4096, plan.contextWindow);
    }

    /** The CPU fallback never takes a larger window: 8k on the CPU took the phone's network down. */
    @Test
    public void theFallbackAcceleratorIsHeldToTheFloor() {
        TaiLoadBudget.Plan plan = plan(E4B, PONG_TOTAL, 5_300_000_000L, 32_768);
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
    public void anEightGigabytePhoneRunsE2bOnTheCpu() {
        TaiLoadBudget.Plan plan = plan(E2B, (long) (7.3 * GIB), 3_000_000_000L, 32_768);
        assertTrue(plan.fits);
        assertEquals("cpu", plan.accelerator);
        assertEquals(4096, plan.contextWindow);
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
        // GPU 4k does not fit here (needs ~5.05 GB with the ratio margin); the CPU takes it at the floor.
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 5_000_000_000L, GPU_THEN_CPU,
            16_384, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
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

    @Test
    public void aLoadKilledAtTheFloorMovesToTheNextAccelerator() {
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(new TaiLoadBudget.Request(TaiModelSpec.BACKEND_LITERT_LM,
            E4B, false, PONG_TOTAL, 5_800_000_000L, GPU_THEN_CPU, 32_768, "gpu", 4096));
        assertEquals("cpu", plan.accelerator);
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

    // --- Estimates: measured worst case over ratio, and what a ratio counts as new pressure ---

    @Test
    public void aMeasuredWorstCaseReplacesTheRatioEstimateWithTenPercentHeadroom() {
        long worst = 3_200_000_000L;
        TaiLoadBudget.History history = (accelerator, context) -> "gpu".equals(accelerator) && context == 4096 ? worst : 0L;
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 9_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, history, NOTHING));
        assertEquals(TaiLoadBudget.SOURCE_MEASURED, plan.estimateSource);
        assertEquals(worst + worst / 10L, plan.estimatedBytes);
        assertEquals(0L, plan.marginBytes);
        assertEquals(0L, plan.reclaimableBytes);
        // pong: 3.2 GB measured -> 3.52 GB + the 630 MB floor = 4.15 GB free needed.
        assertEquals(worst + worst / 10L + PONG_FLOOR, plan.neededFreeBytes());
    }

    @Test
    public void withoutHistoryTheRatioEstimateIsUsedAndSaysSo() {
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 9_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertEquals(TaiLoadBudget.SOURCE_RATIO, plan.estimateSource);
        assertEquals(TaiLoadBudget.estimateBytes(TaiModelSpec.BACKEND_LITERT_LM, "gpu", E4B, false, 4096), plan.estimatedBytes);
    }

    /** LiteRT CPU weights are mapped file pages the kernel reclaims; only the KV cache and a 5% anonymous part are new pressure. */
    @Test
    public void aLiteRtCpuLoadCountsOnlyItsKvCacheAndAnonymousPartAsNonReclaimable() {
        long perToken = E4B / TaiLoadBudget.FILE_BYTES_PER_TOKEN_DIVISOR;
        assertEquals(E4B / 100L * TaiLoadBudget.LITERT_CPU_ANON_PERCENT + perToken * 4096L,
            TaiLoadBudget.estimateBytes(TaiModelSpec.BACKEND_LITERT_LM, "cpu", E4B, false, 4096));
        assertEquals(E4B, TaiLoadBudget.reclaimableBytes(TaiModelSpec.BACKEND_LITERT_LM, "cpu", E4B));
        // Measured on pong: the CPU 16k drop was 0.45 GB, against 4.25 GB estimated before the split.
        assertTrue(TaiLoadBudget.estimateBytes(TaiModelSpec.BACKEND_LITERT_LM, "cpu", E4B, false, 16_384) < 3_400_000_000L);
        // GPU buffers and MNN weights are all new pressure.
        assertEquals(E4B * 3L / 4L + perToken * 4096L, TaiLoadBudget.estimateBytes(TaiModelSpec.BACKEND_LITERT_LM, "gpu", E4B, false, 4096));
        assertEquals(0L, TaiLoadBudget.reclaimableBytes(TaiModelSpec.BACKEND_LITERT_LM, "gpu", E4B));
        assertEquals(0L, TaiLoadBudget.reclaimableBytes(TaiModelSpec.BACKEND_MNN_LLM, "cpu", E4B));
        TaiLoadBudget.Estimate cpu = TaiLoadBudget.estimate(TaiModelSpec.BACKEND_LITERT_LM, "cpu", E4B, false, 4096, TaiLoadBudget.NO_HISTORY);
        assertEquals(E4B, cpu.reclaimableBytes);
        assertEquals(TaiLoadBudget.SOURCE_RATIO, cpu.source);
    }

    // --- Floor and margin ---

    /** pong: threshold 315 MB -> floor 630 MB, where the old reserve was 1.8 GB. */
    @Test
    public void theFloorIsTwiceAndroidsThresholdButNeverBelowHalfAGigabyte() {
        assertEquals(PONG_FLOOR, TaiLoadBudget.floorBytes(PONG_THRESHOLD, PONG_TOTAL));
        assertEquals(TaiLoadBudget.MIN_FLOOR_BYTES, TaiLoadBudget.floorBytes(100_000_000L, PONG_TOTAL));
        // Without a threshold the old reserve stays in force.
        assertEquals(TaiLoadBudget.reserveBytes(PONG_TOTAL), TaiLoadBudget.floorBytes(0L, PONG_TOTAL));
        assertTrue(TaiLoadBudget.reserveBytes(PONG_TOTAL) > 1_700_000_000L);
    }

    @Test
    public void aRatioEstimateCarriesAQuarterMarginOverTheFloorAndAMeasuredOneDoesNot() {
        TaiLoadBudget.Plan ratio = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 9_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertEquals(PONG_FLOOR, ratio.reserveBytes);
        assertEquals(ratio.estimatedBytes * TaiLoadBudget.RATIO_MARGIN_PERCENT / 100L, ratio.marginBytes);
        assertEquals(ratio.estimatedBytes + ratio.marginBytes + PONG_FLOOR, ratio.neededFreeBytes());

        TaiLoadBudget.Plan measured = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 9_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, (accelerator, context) -> 3_000_000_000L, NOTHING));
        assertEquals(PONG_FLOOR, measured.reserveBytes);
        assertEquals(0L, measured.marginBytes);

        // The legacy reserve carries no margin either: the margin is what makes the smaller floor safe.
        TaiLoadBudget.Plan legacy = plan(E4B, PONG_TOTAL, 9_000_000_000L, 4096);
        assertEquals(0L, legacy.marginBytes);
    }

    /** The daily-driver state from the plan: 5 GB free. E4B goes to the CPU; E2B gets the GPU. */
    @Test
    public void atFiveGigabytesFreeOnPongE2bGetsTheGpuAndE4bTheCpu() {
        TaiLoadBudget.Plan e4b = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 5_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertEquals("cpu", e4b.accelerator);
        assertTrue(e4b.fits);

        TaiLoadBudget.Plan e2b = TaiLoadBudget.plan(request(E2B, PONG_TOTAL, 5_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, NOTHING));
        assertEquals("gpu", e2b.accelerator);
        assertTrue(e2b.fits);
        assertTrue(e2b.neededFreeBytes() < 4_000_000_000L);
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

    /** When eviction cannot cover it either, the ladder still runs: the CPU floor, then a refusal. */
    @Test
    public void whenEvictionIsNotEnoughTheLadderStillRunsAndThenRefuses() {
        TaiResidency.Entry embedding = resident("emb", TaiResidency.Kind.EMBEDDING, 100_000_000L, false);
        TaiLoadBudget.Plan cpu = TaiLoadBudget.plan(request(E4B, PONG_TOTAL, 4_000_000_000L, GPU_THEN_CPU,
            4096, false, PONG_THRESHOLD, TaiLoadBudget.NO_HISTORY, Collections.singletonList(embedding)));
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
        TaiLoadBudget.Plan refused = TaiLoadBudget.planFixed(estimate, "cpu", PONG_TOTAL, PONG_FLOOR + margin + 100_000_000L,
            PONG_THRESHOLD, Collections.<TaiResidency.Entry>emptyList());
        assertFalse(refused.fits);
        assertEquals(TaiLoadBudget.SOURCE_RATIO, refused.estimateSource);

        TaiLoadBudget.Plan evicting = TaiLoadBudget.planFixed(estimate, "cpu", PONG_TOTAL, PONG_FLOOR + margin + 100_000_000L,
            PONG_THRESHOLD, Collections.singletonList(chat));
        assertTrue(evicting.fits);
        assertEquals(Collections.singletonList(chat), evicting.evicted);

        TaiLoadBudget.Plan measured = TaiLoadBudget.planFixed(TaiLoadBudget.Estimate.measured(200_000_000L), "cpu", PONG_TOTAL,
            PONG_FLOOR + 220_000_000L, PONG_THRESHOLD, Collections.<TaiResidency.Entry>emptyList());
        assertTrue(measured.fits);
        assertEquals(220_000_000L, measured.estimatedBytes);
        assertEquals(0L, measured.marginBytes);
    }

    /** An embedding interpreter has no window to shrink: it fits whole or it is refused. */
    @Test
    public void aFixedLoadFitsWhenItLeavesTheReserveFree() {
        long reserve = TaiLoadBudget.reserveBytes(PONG_TOTAL);
        TaiLoadBudget.Plan fits = TaiLoadBudget.planFixed(240_000_000L, "cpu", PONG_TOTAL, reserve + 240_000_000L);
        assertTrue(fits.fits);
        assertTrue(fits.measured);
        assertEquals("cpu", fits.accelerator);
        assertEquals(0, fits.contextWindow);
        assertEquals(240_000_000L, fits.estimatedBytes);

        TaiLoadBudget.Plan refused = TaiLoadBudget.planFixed(240_000_000L, "cpu", PONG_TOTAL, reserve + 239_999_999L);
        assertFalse(refused.fits);
        assertTrue(refused.neededFreeBytes() > refused.availableBytes);
    }

    @Test
    public void aFixedLoadWithUnknownFreeMemoryGoesAheadUnmeasured() {
        TaiLoadBudget.Plan plan = TaiLoadBudget.planFixed(240_000_000L, "cpu", PONG_TOTAL, 0L);
        assertTrue(plan.fits);
        assertFalse(plan.measured);
    }

    /** The pre-threshold reserve, still what a plan without a threshold falls back to. */
    @Test
    public void theReserveIsTheLargerOfOneAndAHalfGigabytesAndFifteenPercent() {
        assertEquals(1536L * 1024L * 1024L, TaiLoadBudget.reserveBytes(6L * GIB));
        assertEquals(16L * GIB / 100L * 15L, TaiLoadBudget.reserveBytes(16L * GIB));
    }

    @Test
    public void ramClassRoundsTheKernelsFigureUpToTheSizeThePhoneIsSoldAs() {
        assertEquals(12L * GIB, TaiLoadBudget.ramClassBytes(PONG_TOTAL));
        assertEquals(8L * GIB, TaiLoadBudget.ramClassBytes((long) (7.3 * GIB)));
        assertEquals(6L * GIB, TaiLoadBudget.ramClassBytes((long) (5.5 * GIB)));
        assertEquals(0L, TaiLoadBudget.ramClassBytes(0L));
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
