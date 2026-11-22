package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

/**
 * The Device ring after deep sleep: the reading read at once falls back from the sticky intent
 * to the capacity, a known reading is never replaced by nothing, and the ring's number is never
 * empty, so the ring is never a blank disc.
 */
public class BatteryReadingFallbackTest {
    @NonNull private static BatterySource.State state(int level, boolean charging) {
        return new BatterySource.State(level, charging, false, charging, 312, 4100, 1.5d, -1);
    }

    @NonNull private static BatterySource.State unknown() {
        return new BatterySource.State(-1, false, false, false, Integer.MIN_VALUE, -1,
            Double.NaN, -1);
    }

    @Test public void theStickyIntentWinsWhenItHasALevel() {
        BatterySource.State sticky = state(57, false);
        assertSame(sticky, BatterySource.settle(sticky, 90, state(12, true)));
    }

    @Test public void theCapacityStandsInForANullStickyIntent() {
        BatterySource.State out = BatterySource.settle(null, 63, null);
        assertTrue(out.known());
        assertEquals(63, out.level);
        assertFalse(out.charging);
        assertEquals(Integer.MIN_VALUE, out.temperatureTenths);
    }

    @Test public void theCapacityKeepsTheIntentsOtherValues() {
        BatterySource.State levelless = new BatterySource.State(-1, true, false, true, 300, 4000,
            2d, 60_000L);
        BatterySource.State out = BatterySource.settle(levelless, 41, null);
        assertEquals(41, out.level);
        assertTrue(out.charging);
        assertEquals(300, out.temperatureTenths);
    }

    @Test public void theCapacityKeepsTheLastReadingsOtherValues() {
        BatterySource.State out = BatterySource.settle(null, 70, state(80, true));
        assertEquals(70, out.level);
        assertTrue(out.charging);
    }

    @Test public void anOutOfRangeCapacityIsNoReading() {
        assertNull(BatterySource.settle(null, Integer.MIN_VALUE, null));
        assertNull(BatterySource.settle(null, 101, null));
        assertNull(BatterySource.settle(null, -1, null));
    }

    @Test public void aKnownReadingIsNeverReplacedByNothing() {
        assertNull(BatterySource.settle(unknown(), -1, state(50, false)));
        assertNull(BatterySource.settle(null, -1, state(50, false)));
    }

    @Test public void withNothingKnownTheUnknownIntentStands() {
        BatterySource.State levelless = unknown();
        assertSame(levelless, BatterySource.settle(levelless, -1, null));
        assertSame(levelless, BatterySource.settle(levelless, -1, unknown()));
    }

    @Test public void theRingsNumberIsNeverEmpty() {
        assertEquals(BatteryRingView.PLACEHOLDER, DeviceWidgetView.ringLabel(unknown()));
        assertEquals("84", DeviceWidgetView.ringLabel(state(84, false)));
        assertEquals("0", DeviceWidgetView.ringLabel(state(0, false)));
        assertEquals(BatteryRingView.PLACEHOLDER, BatteryRingView.shown(""));
        assertEquals("7", BatteryRingView.shown("7"));
        assertFalse(BatteryRingView.PLACEHOLDER.isEmpty());
    }
}
