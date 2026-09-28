package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
    public void verdictThresholds() {
        assertEquals("smooth", TaiBenchStats.verdict(15.0, true));
        assertEquals("smooth", TaiBenchStats.verdict(21.3, true));
        assertEquals("usable", TaiBenchStats.verdict(14.99, true));
        assertEquals("usable", TaiBenchStats.verdict(7.0, true));
        assertEquals("slow", TaiBenchStats.verdict(6.99, true));
        assertEquals("slow", TaiBenchStats.verdict(0.0, true));
        assertEquals("broken", TaiBenchStats.verdict(40.0, false));
    }
}
