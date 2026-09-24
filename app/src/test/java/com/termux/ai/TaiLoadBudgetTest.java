package com.termux.ai;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    @Test
    public void plentyOfFreeMemoryEarnsALargerWindow() {
        TaiLoadBudget.Plan plan = plan(E2B, 16L * GIB, 12_000_000_000L, 32_768);
        assertEquals("gpu", plan.accelerator);
        assertEquals(32_768, plan.contextWindow);
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
}
