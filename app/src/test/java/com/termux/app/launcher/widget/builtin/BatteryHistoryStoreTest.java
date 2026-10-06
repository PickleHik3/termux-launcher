package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

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
