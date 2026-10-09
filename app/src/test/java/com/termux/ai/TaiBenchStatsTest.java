package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The formulas a record is built from, including the runs where nothing arrived. */
public class TaiBenchStatsTest {

    private static final double EXACT = 1e-9;

    @Test
    public void prefillIsPromptTokensOverTimeToFirstToken() {
        // 512 prompt tokens, first token after 200 ms: pong's measured MNN CPU figure.
        assertEquals(2560.0, TaiBenchStats.prefillTps(512, 200L), EXACT);
        assertEquals(0.0, TaiBenchStats.prefillTps(0, 200L), EXACT);
        assertEquals(0.0, TaiBenchStats.prefillTps(512, 0L), EXACT);
        assertEquals(0.0, TaiBenchStats.prefillTps(512, -5L), EXACT);
    }

    @Test
    public void decodeLeavesTheFirstTokenOutOfTheCount() {
        // 128 tokens, first at t=0, last at t=6050 ms: 127 intervals, 21 tok/s.
        assertEquals(127 * 1000.0 / 6050.0, TaiBenchStats.decodeTps(128, 1000L, 7050L), EXACT);
    }

    @Test
    public void zeroOrOneTokenHasNoDecodeRate() {
        assertEquals(0.0, TaiBenchStats.decodeTps(0, 0L, 0L), EXACT);
        assertEquals(0.0, TaiBenchStats.decodeTps(1, 1000L, 1000L), EXACT);
        assertEquals(0.0, TaiBenchStats.decodeTps(1, 1000L, 5000L), EXACT);
        // Two tokens stamped in the same millisecond: no interval to divide by.
        assertEquals(0.0, TaiBenchStats.decodeTps(2, 1000L, 1000L), EXACT);
    }

    @Test
    public void medianOfOddAndEvenRuns() {
        TaiBenchStats.Series odd = new TaiBenchStats.Series();
        odd.add(21.0);
        odd.add(19.0);
        odd.add(23.0);
        assertEquals(21.0, odd.median(), EXACT);
        assertEquals(19.0, odd.min(), EXACT);
        assertEquals(23.0, odd.max(), EXACT);

        TaiBenchStats.Series even = new TaiBenchStats.Series();
        even.add(10.0);
        even.add(20.0);
        even.add(30.0);
        even.add(40.0);
        assertEquals(25.0, even.median(), EXACT);
    }

    @Test
    public void singleRunIsItsOwnMedianMinAndMax() throws Exception {
        TaiBenchStats.Series one = new TaiBenchStats.Series();
        one.add(7.5);
        assertEquals(7.5, one.median(), EXACT);
        assertEquals(7.5, one.min(), EXACT);
        assertEquals(7.5, one.max(), EXACT);
        assertNotNull(one.toJson());
        assertEquals(1, one.toJson().getInt("runs"));
    }

    @Test
    public void emptySeriesIsZeroAndSerialisesToNothing() throws Exception {
        TaiBenchStats.Series none = new TaiBenchStats.Series();
        assertEquals(0.0, none.median(), EXACT);
        assertEquals(0.0, none.min(), EXACT);
        assertEquals(0.0, none.max(), EXACT);
        assertNull(none.toJson());
    }

    @Test
    public void standardMedianOfTwoRunsIsTheirMean() {
        TaiBenchStats.Series two = new TaiBenchStats.Series();
        two.add(12.0);
        two.add(18.0);
        assertEquals(15.0, two.median(), EXACT);
        assertEquals(12.0, two.min(), EXACT);
        assertEquals(18.0, two.max(), EXACT);
    }

    @Test
    public void peakKeepsTheLargestSampleAndIgnoresFailedReads() {
        TaiBenchStats.Peak peak = new TaiBenchStats.Peak();
        assertEquals(-1L, peak.value());
        peak.add(1_500_000_000L);
        peak.add(2_100_000_000L);
        peak.add(1_900_000_000L);
        peak.add(-1L);
        peak.add(0L);
        assertEquals(2_100_000_000L, peak.value());
    }

    // ---- the verdict ----

    private static String verdict(double decode, long ttft, long read) {
        return TaiBenchStats.verdict(decode, ttft, read, true);
    }

    @Test
    public void smoothNeedsAllThreeFigures() {
        assertEquals("smooth", verdict(14.0, 600L, 4_000L));
        // Each bound is inclusive.
        assertEquals("smooth", verdict(TaiBenchStats.SMOOTH_DECODE_TPS, TaiBenchStats.SMOOTH_TTFT_MS, TaiBenchStats.SMOOTH_READ_MS));
        // One step past any one bound is Usable, however good the other two are.
        assertEquals("usable", verdict(11.99, 600L, 4_000L));
        assertEquals("usable", verdict(30.0, 1_501L, 4_000L));
        assertEquals("usable", verdict(30.0, 600L, 8_001L));
    }

    @Test
    public void usableBoundsAreInclusiveAndAnythingPastThemIsSlow() {
        assertEquals("usable", verdict(TaiBenchStats.USABLE_DECODE_TPS, TaiBenchStats.USABLE_TTFT_MS, TaiBenchStats.USABLE_READ_MS));
        assertEquals("slow", verdict(5.99, 1_000L, 5_000L));
        assertEquals("slow", verdict(30.0, 3_001L, 5_000L));
        assertEquals("slow", verdict(30.0, 1_000L, 20_001L));
    }

    @Test
    public void aFigureThatWasNotMeasuredClearsNoLine() {
        assertEquals("slow", verdict(0.0, 600L, 4_000L));
        assertEquals("slow", verdict(30.0, 0L, 4_000L));
        assertEquals("slow", verdict(30.0, 600L, 0L));
    }

    @Test
    public void aFailedSanityCheckIsBrokenWhateverTheSpeed() {
        assertEquals("broken", TaiBenchStats.verdict(40.0, 100L, 1_000L, false));
    }

    @Test
    public void verdictOrderPutsSmoothFirstAndBrokenLast() {
        assertTrue(TaiBenchStats.verdictOrder("smooth") > TaiBenchStats.verdictOrder("usable"));
        assertTrue(TaiBenchStats.verdictOrder("usable") > TaiBenchStats.verdictOrder("slow"));
        assertTrue(TaiBenchStats.verdictOrder("slow") > TaiBenchStats.verdictOrder("broken"));
        assertEquals(TaiBenchStats.verdictOrder("broken"), TaiBenchStats.verdictOrder(null));
    }
}
