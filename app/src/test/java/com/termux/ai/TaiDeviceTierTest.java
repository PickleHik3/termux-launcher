package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The tier boundaries of tai-device-tiers §1: 6 GB and under, 8 to 12 GB, 16 GB and more. */
public class TaiDeviceTierTest {
    private static final long GIB = 1024L * 1024L * 1024L;

    private static TaiDeviceTier tierOfGb(long gb) {
        return TaiDeviceTier.from(gb * GIB);
    }

    @Test
    public void ramClassBoundaries() {
        assertEquals(TaiDeviceTier.TIER_1, tierOfGb(4));
        assertEquals(TaiDeviceTier.TIER_1, tierOfGb(6));
        assertEquals(TaiDeviceTier.TIER_2, tierOfGb(8));
        assertEquals(TaiDeviceTier.TIER_2, tierOfGb(10));
        assertEquals(TaiDeviceTier.TIER_2, tierOfGb(12));
        assertEquals(TaiDeviceTier.TIER_3, tierOfGb(16));
        assertEquals(TaiDeviceTier.TIER_3, tierOfGb(24));
    }

    @Test
    public void pongsReportedMemoryIsTier2NotTier3() {
        // pong reports about 11 GiB of MemTotal; the RAM class rounds it to 12, never to advertisedMem's 16.
        long pongTotal = 11_530_736L * 1024L;
        assertEquals(TaiDeviceTier.TIER_2, TaiDeviceTier.from(TaiLoadBudget.ramClassBytes(pongTotal)));
    }

    @Test
    public void unknownRamIsTier1() {
        assertEquals(TaiDeviceTier.TIER_1, TaiDeviceTier.from(0L));
    }

    @Test
    public void eightGbHasItsOwnNotion() {
        assertTrue(TaiDeviceTier.isEightGb(8 * GIB));
        assertFalse(TaiDeviceTier.isEightGb(6 * GIB));
        assertFalse(TaiDeviceTier.isEightGb(10 * GIB));
        assertFalse(TaiDeviceTier.isEightGb(12 * GIB));
    }

    @Test
    public void overrideParsesOneTwoThreeAndNothingElse() {
        assertEquals(TaiDeviceTier.TIER_1, TaiDeviceTier.parseOverride("1"));
        assertEquals(TaiDeviceTier.TIER_2, TaiDeviceTier.parseOverride(" 2 "));
        assertEquals(TaiDeviceTier.TIER_3, TaiDeviceTier.parseOverride("3"));
        assertNull(TaiDeviceTier.parseOverride(""));
        assertNull(TaiDeviceTier.parseOverride("auto"));
        assertNull(TaiDeviceTier.parseOverride("4"));
        assertNull(TaiDeviceTier.parseOverride(null));
    }

    @Test
    public void overrideWinsOverRam() {
        assertEquals(TaiDeviceTier.TIER_3, TaiDeviceTier.effective(TaiDeviceTier.TIER_3, 4 * GIB));
        assertEquals(TaiDeviceTier.TIER_1, TaiDeviceTier.effective(null, 4 * GIB));
        assertEquals(3, TaiDeviceTier.TIER_3.number());
    }
}
