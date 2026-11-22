package com.termux.app.setup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeviceTierTest {

    private static final long GIB = DeviceTier.GIB;

    @Test
    public void smallRamIsLow() {
        assertEquals(DeviceTier.Tier.LOW, DeviceTier.decide(4 * GIB, "SM8450", "", "Adreno 730"));
    }

    @Test
    public void sixGigIsMid_eightIsHigh() {
        assertEquals(DeviceTier.Tier.MID, DeviceTier.decide(6 * GIB, "SM8450", "", "Adreno 730"));
        assertEquals(DeviceTier.Tier.HIGH, DeviceTier.decide(8 * GIB, "SM8450", "", "Adreno 730"));
    }

    @Test
    public void freezeGpuAndPlatformAreLowWhateverTheRam() {
        assertEquals(DeviceTier.Tier.LOW, DeviceTier.decide(8 * GIB, "", "", "Mali-G52 MC2"));
        assertEquals(DeviceTier.Tier.LOW, DeviceTier.decide(8 * GIB, "", "mt6768", null));
    }

    @Test
    public void lowSocList() {
        assertEquals(DeviceTier.Tier.LOW, DeviceTier.decide(6 * GIB, "MT6765", "", null));
        assertEquals(DeviceTier.Tier.LOW, DeviceTier.decide(6 * GIB, "SM4350", "", null));
        assertEquals(DeviceTier.Tier.LOW, DeviceTier.decide(6 * GIB, "Exynos 850", "", null));
        assertEquals(DeviceTier.Tier.LOW, DeviceTier.decide(6 * GIB, "exynos1280", "", null));
        assertEquals(DeviceTier.Tier.MID, DeviceTier.decide(6 * GIB, "SM4450", "", null));
        assertEquals(DeviceTier.Tier.MID, DeviceTier.decide(6 * GIB, "Exynos 990", "", null));
        assertEquals(DeviceTier.Tier.MID, DeviceTier.decide(6 * GIB, "Exynos 2200", "", null));
    }

    @Test
    public void unknownRamAndNamesIsMid() {
        assertEquals(DeviceTier.Tier.MID, DeviceTier.decide(0, null, null, null));
    }

    @Test
    public void tierDefaults() {
        DeviceTier.Defaults low = DeviceTier.defaults(DeviceTier.Tier.LOW);
        assertFalse(low.ai);
        assertFalse(low.fancierGlass);
        assertTrue(low.lazy);
        assertTrue(low.lowMemory);
        assertEquals("slower", low.animationSpeed);
        assertTrue(low.autotiling);
        DeviceTier.Defaults mid = DeviceTier.defaults(DeviceTier.Tier.MID);
        assertFalse(mid.motionLight);
        assertTrue(mid.fancierGlass);
        DeviceTier.Defaults high = DeviceTier.defaults(DeviceTier.Tier.HIGH);
        assertTrue(high.motionLight);
        assertFalse(high.lowMemory);
    }
}
