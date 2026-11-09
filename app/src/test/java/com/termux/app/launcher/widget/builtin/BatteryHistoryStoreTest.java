package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class BatteryHistoryStoreTest {
    private static int[] ramp() {
        int[] levels = new int[BatteryHistoryStore.SLOTS];
        for (int i = 0; i < levels.length; i++) levels[i] = 50 + i;
        return levels;
    }

    @Test public void theSameHourKeepsEverySlot() {
        assertArrayEquals(ramp(), BatteryHistoryStore.shifted(ramp(), 100, 100));
    }

    @Test public void anHourLaterTheRingSlidesAndTheNewHourIsEmpty() {
        int[] out = BatteryHistoryStore.shifted(ramp(), 100, 101);
        assertEquals(51, out[0]);
        assertEquals(73, out[22]);
        assertEquals(BatteryHistoryStore.EMPTY, out[23]);
    }

    @Test public void aDayOrMoreAwayEmptiesTheRing() {
        assertArrayEquals(BatteryHistoryStore.emptySlots(), BatteryHistoryStore.shifted(ramp(), 100, 124));
        assertArrayEquals(BatteryHistoryStore.emptySlots(), BatteryHistoryStore.shifted(ramp(), 100, 76));
    }

    @Test public void aClockThatWentBackKeepsWhatStillFits() {
        int[] out = BatteryHistoryStore.shifted(ramp(), 100, 99);
        assertEquals(BatteryHistoryStore.EMPTY, out[0]);
        assertEquals(50, out[1]);
        assertEquals(72, out[23]);
    }

    @Test public void theDrainRateIsTheNewestFallingRunAnHour() {
        int[] levels = BatteryHistoryStore.emptySlots();
        // Charged to 90 by hour 15, then down 4 an hour.
        levels[15] = 70; levels[16] = 90; levels[17] = 86; levels[18] = 82; levels[19] = 78;
        assertEquals(4, BatteryHistoryStore.drainPerHour(Arrays.copyOf(levels, 20)));
        // Read over at most six hours: 100 down 3 an hour from hour 0 reads 3 over the last six.
        int[] long_ = new int[BatteryHistoryStore.SLOTS];
        for (int i = 0; i < long_.length; i++) long_[i] = 100 - 3 * i / 2;
        assertEquals(Math.round((long_[17] - long_[23]) / 6f),
            BatteryHistoryStore.drainPerHour(long_));
    }

    @Test public void theDrainRateIsUnknownWithoutAnHourOfFalling() {
        int[] levels = BatteryHistoryStore.emptySlots();
        assertEquals(-1, BatteryHistoryStore.drainPerHour(levels));
        levels[23] = 80;
        assertEquals("one reading", -1, BatteryHistoryStore.drainPerHour(levels));
        levels[22] = 80;
        assertEquals("nothing used", -1, BatteryHistoryStore.drainPerHour(levels));
        levels[22] = 70;
        assertEquals("charging", -1, BatteryHistoryStore.drainPerHour(levels));
        levels[22] = 83;
        levels[23] = BatteryHistoryStore.EMPTY;
        assertEquals("nothing heard this hour", -1, BatteryHistoryStore.drainPerHour(levels));
    }

    /** A drain under half a percent an hour is no rate, not 1%/h: the Device line falls back. */
    @Test public void aSlowDrainIsNoRateRatherThanOnePercentAnHour() {
        int[] levels = BatteryHistoryStore.emptySlots();
        // 1% over six hours: 0.17%/h.
        levels[17] = 80; levels[18] = 80; levels[19] = 80; levels[20] = 80; levels[21] = 80;
        levels[22] = 80; levels[23] = 79;
        assertEquals(-1, BatteryHistoryStore.drainPerHour(levels));
        // 3% over six hours: 0.5%/h rounds to 1.
        levels[23] = 77;
        assertEquals(1, BatteryHistoryStore.drainPerHour(levels));
    }

    @Test public void encodingRoundTrips() {
        int[] levels = ramp();
        levels[3] = BatteryHistoryStore.EMPTY;
        assertArrayEquals(levels, BatteryHistoryStore.decode(BatteryHistoryStore.encode(levels)));
    }

    @Test public void malformedStorageReadsAsEmptySlots() {
        assertArrayEquals(BatteryHistoryStore.emptySlots(), BatteryHistoryStore.decode(null));
        assertArrayEquals(BatteryHistoryStore.emptySlots(), BatteryHistoryStore.decode("x,y"));
        int[] out = BatteryHistoryStore.decode("40,150,-7,60");
        assertEquals(40, out[0]);
        assertEquals(BatteryHistoryStore.EMPTY, out[1]);
        assertEquals(BatteryHistoryStore.EMPTY, out[2]);
        assertEquals(60, out[3]);
        assertEquals(BatteryHistoryStore.EMPTY, out[4]);
    }
}
