package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SttBenchStatsTest {

    @Test
    public void medianOddEvenAndEmpty() {
        assertEquals(3.0, SttBenchStats.median(new double[] {5, 1, 3}), 0.0);
        assertEquals(2.5, SttBenchStats.median(new double[] {4, 1, 2, 3}), 0.0);
        assertTrue(Double.isNaN(SttBenchStats.median(new double[0])));
    }

    @Test
    public void medianDoesNotReorderItsInput() {
        double[] in = {3, 1, 2};
        SttBenchStats.median(in);
        assertEquals(3.0, in[0], 0.0);
    }

    @Test
    public void minAndMeanOfFinite() {
        assertEquals(1.0, SttBenchStats.min(new double[] {2, 1, 3}), 0.0);
        assertEquals(2.0, SttBenchStats.meanOfFinite(new double[] {1, Double.NaN, 3}), 0.0);
        assertTrue(Double.isNaN(SttBenchStats.meanOfFinite(new double[] {Double.NaN})));
    }

    @Test
    public void parsesCpuTicksPastACommWithSpacesAndParens() {
        // pid (comm) state ppid pgrp session tty tpgid flags minflt cminflt majflt cmajflt utime stime ...
        String stat = "1234 (app (proc) x) S 1 2 3 4 5 6 7 8 9 10 250 30 0 0 20 0 5";
        assertEquals(280L, SttBenchStats.parseCpuTicks(stat));
    }

    @Test
    public void rejectsShortOrBrokenStat() {
        assertEquals(-1L, SttBenchStats.parseCpuTicks(null));
        assertEquals(-1L, SttBenchStats.parseCpuTicks("1 (a) S 1 2"));
        assertEquals(-1L, SttBenchStats.parseCpuTicks("no parens at all"));
        assertEquals(-1L, SttBenchStats.parseCpuTicks("1 (a) S 1 2 3 4 5 6 7 8 9 10 x y 0"));
    }

    @Test
    public void ticksToSeconds() {
        assertEquals(2.8, SttBenchStats.ticksToSeconds(280L, 100L), 1e-9);
        assertTrue(Double.isNaN(SttBenchStats.ticksToSeconds(-1L, 100L)));
        assertTrue(Double.isNaN(SttBenchStats.ticksToSeconds(5L, 0L)));
    }

    @Test
    public void numbersUseADotAndNaIsNa() {
        assertEquals("1.50", SttBenchStats.num(1.5, 2));
        assertEquals("NA", SttBenchStats.num(Double.NaN, 1));
        assertEquals("-312.5", SttBenchStats.milliamps(-312500.0));
    }

    @Test
    public void lineQuotesOnlyWhatNeedsIt() {
        assertEquals("T a=1 b=\"x y\" c=\"\" d=\"say \\\"hi\\\"\\n\"",
            SttBenchStats.line("T", "a", "1", "b", "x y", "c", "", "d", "say \"hi\"\n"));
    }
}
