package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The smoothed transfer rate: steady input reads as a steady number, and a burst does not swing it. */
public class TaiDownloadRateTest {
    private static final long MB = 1024L * 1024L;

    @Test
    public void steadyTransfer_convergesOnTheTrueRate() {
        TaiDownloadRate rate = new TaiDownloadRate();
        long bytes = 0L;
        long now = 0L;
        for (int i = 0; i < 20; i++) {
            now += 500L;
            bytes += 4L * MB; // 8 MB/s
            rate.update(bytes, now);
        }
        assertEquals(8.0 * MB, rate.bytesPerSecond(), 0.01 * MB);
        assertEquals(10L, rate.etaSeconds(80L * MB));
    }

    @Test
    public void oneBurst_movesTheAverageOnlyPartWay() {
        TaiDownloadRate rate = new TaiDownloadRate();
        long bytes = 0L;
        long now = 0L;
        for (int i = 0; i < 20; i++) {
            now += 500L;
            bytes += 4L * MB;
            rate.update(bytes, now);
        }
        // One half-second sample at 40 MB/s, five times the steady rate.
        now += 500L;
        bytes += 20L * MB;
        double after = rate.update(bytes, now);
        assertTrue("a burst must not become the displayed rate", after < 20.0 * MB);
        assertTrue("but it must register", after > 8.0 * MB);
    }

    @Test
    public void samplesTooCloseTogether_areFoldedIntoTheNextOne() {
        TaiDownloadRate rate = new TaiDownloadRate();
        rate.update(0L, 0L);
        // 100 ms apart: not a rate. Folded into the 1 s sample below, which then reads 1 MB/s.
        assertEquals(0.0, rate.update(900L * 1024L, 100L), 0.0);
        assertEquals(1.0 * MB, rate.update(1L * MB, 1_000L), 1.0);
    }

    @Test
    public void noRateYet_meansNoEta_andResetForgetsTheHistory() {
        TaiDownloadRate rate = new TaiDownloadRate();
        assertEquals(-1L, rate.etaSeconds(10L * MB));
        rate.update(0L, 0L);
        rate.update(MB, 1_000L);
        assertEquals(10L, rate.etaSeconds(10L * MB));
        assertEquals(0L, rate.etaSeconds(0L));

        rate.reset();
        assertEquals(0.0, rate.bytesPerSecond(), 0.0);
        assertEquals(-1L, rate.etaSeconds(10L * MB));
        // After a reset the first sample primes again rather than averaging against the old one.
        rate.update(5L * MB, 5_000L);
        assertEquals(2.0 * MB, rate.update(7L * MB, 6_000L), 1.0);
    }

    @Test
    public void aCounterThatGoesBackwards_startsOver() {
        TaiDownloadRate rate = new TaiDownloadRate();
        rate.update(0L, 0L);
        rate.update(8L * MB, 1_000L);
        assertEquals(8.0 * MB, rate.bytesPerSecond(), 1.0);
        // A restart from a smaller offset: no negative burst, a fresh average.
        assertEquals(0.0, rate.update(2L * MB, 2_000L), 0.0);
        assertEquals(1.0 * MB, rate.update(3L * MB, 3_000L), 1.0);
    }
}
