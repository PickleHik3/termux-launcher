package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TaiImportFitTest {
    private static final long GIB = 1024L * 1024L * 1024L;
    private static final long GEMMA_E2B = 2_588_147_712L;
    private static final long GEMMA_E4B = 3_659_530_240L;
    private static final long DEEPSEEK_1_5B = 1_833_451_520L;
    private static final long FUNCTION_GEMMA = 288_964_608L;
    private static final long EMBEDDING_GEMMA = 183_329_528L;

    @Test
    public void tiersMatchTheCatalog() {
        assertEquals(8, TaiImportFit.recommendedRamGb(GEMMA_E2B, false));
        assertEquals(12, TaiImportFit.recommendedRamGb(GEMMA_E4B, false));
        assertEquals(6, TaiImportFit.recommendedRamGb(DEEPSEEK_1_5B, false));
        assertEquals(6, TaiImportFit.recommendedRamGb(FUNCTION_GEMMA, false));
        assertEquals(4, TaiImportFit.recommendedRamGb(EMBEDDING_GEMMA, true));
        assertEquals(0, TaiImportFit.recommendedRamGb(-1L, false));
        assertEquals(32, TaiImportFit.recommendedRamGb(10L * GIB, false));
    }

    @Test
    public void yesWhenThePhoneMeetsTheTier() {
        assertEquals(TaiImportFit.Verdict.YES, TaiImportFit.check(GEMMA_E2B, 8L * GIB, false).verdict);
        assertEquals(TaiImportFit.Verdict.YES, TaiImportFit.check(GEMMA_E4B, 12L * GIB, false).verdict);
        assertEquals(TaiImportFit.Verdict.YES, TaiImportFit.check(FUNCTION_GEMMA, 6L * GIB, false).verdict);
    }

    @Test
    public void slowOneClassUnderAndTooBigTwoUnder() {
        TaiImportFit slow = TaiImportFit.check(GEMMA_E2B, 6L * GIB, false);
        assertEquals(TaiImportFit.Verdict.SLOW, slow.verdict);
        assertEquals(8, slow.neededGb);
        assertEquals(6, slow.deviceGb);
        assertEquals(TaiImportFit.Verdict.TOO_BIG, TaiImportFit.check(GEMMA_E2B, 4L * GIB, false).verdict);
        // The review's case: a 3.7 GB model on a 6 GB phone is refused before the copy, with the tier named.
        TaiImportFit tooBig = TaiImportFit.check(GEMMA_E4B, 6L * GIB, false);
        assertEquals(TaiImportFit.Verdict.TOO_BIG, tooBig.verdict);
        assertEquals(12, tooBig.neededGb);
        assertEquals(TaiImportFit.Verdict.SLOW, TaiImportFit.check(GEMMA_E4B, 10L * GIB, false).verdict);
    }

    @Test
    public void unknownWhenEitherSideIsUnknown() {
        assertEquals(TaiImportFit.Verdict.UNKNOWN, TaiImportFit.check(-1L, 8L * GIB, false).verdict);
        assertEquals(TaiImportFit.Verdict.UNKNOWN, TaiImportFit.check(GEMMA_E2B, 0L, false).verdict);
    }

    @Test
    public void classBelowWalksTheRamLadder() {
        assertEquals(4, TaiImportFit.classBelow(6));
        assertEquals(6, TaiImportFit.classBelow(8));
        assertEquals(10, TaiImportFit.classBelow(12));
        assertEquals(2, TaiImportFit.classBelow(2));
    }
}
