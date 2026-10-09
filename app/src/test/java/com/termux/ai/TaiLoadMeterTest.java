package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The numbers behind the meter's two phases; the sampling itself needs a phone. */
public class TaiLoadMeterTest {

    /** Measuring continues past the end of initialize() until the first token, but not for ever. */
    @Test
    public void theFirstPrefillWindowStaysOpenForSixtySecondsAfterTheLoadEnds() {
        assertEquals(60_000L, TaiLoadMeter.FIRST_PREFILL_CAP_MS);
        long ended = 5_000L;
        // While the load is still in progress the window is open.
        assertTrue(TaiLoadMeter.firstPrefillWindowOpen(999_999L, 0L, TaiLoadMeter.FIRST_PREFILL_CAP_MS));
        assertTrue(TaiLoadMeter.firstPrefillWindowOpen(ended, ended, TaiLoadMeter.FIRST_PREFILL_CAP_MS));
        assertTrue(TaiLoadMeter.firstPrefillWindowOpen(ended + 60_000L, ended, TaiLoadMeter.FIRST_PREFILL_CAP_MS));
        assertFalse(TaiLoadMeter.firstPrefillWindowOpen(ended + 60_001L, ended, TaiLoadMeter.FIRST_PREFILL_CAP_MS));
    }

    /** Sampling stays at 100 ms: a load is over in seconds and the allocation guarded is fast. */
    @Test
    public void samplingStaysAtOneHundredMilliseconds() {
        assertEquals(100L, TaiLoadMeter.SAMPLE_INTERVAL_MS);
    }

    @Test
    public void theDropIsBeforeMinusMinimumAndNeverNegative() {
        assertEquals(3_000_000_000L, TaiLoadMeter.dropBytes(5_800_000_000L, 2_800_000_000L));
        assertEquals(0L, TaiLoadMeter.dropBytes(2_800_000_000L, 2_800_000_000L));
        assertEquals(0L, TaiLoadMeter.dropBytes(2_000_000_000L, 2_800_000_000L));
    }
}
