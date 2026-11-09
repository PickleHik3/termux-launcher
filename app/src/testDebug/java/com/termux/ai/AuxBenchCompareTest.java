package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AuxBenchCompareTest {

    @Test
    public void maxAbsDiffOverTheCommonPrefix() {
        float[] a = {0f, 1f, -2f, 100f};
        float[] b = {0.5f, 1f, 1f, -100f};
        assertEquals(3.0, AuxBenchCompare.maxAbsDiff(a, b, 3), 1e-9);
        assertEquals(0.0, AuxBenchCompare.maxAbsDiff(a, b, 0), 0.0);
    }

    @Test
    public void snrOfKnownNoise() {
        // signal power 4 per sample, error power 0.04: 10 log10(100) = 20 dB
        float[] ref = {2f, -2f, 2f, -2f};
        float[] test = {2.2f, -2.2f, 2.2f, -2.2f};
        assertEquals(20.0, AuxBenchCompare.snrDb(ref, test, 4), 1e-3);
    }

    @Test
    public void snrOfIdenticalAndSilentInputs() {
        float[] ref = {1f, 2f};
        assertEquals(Double.POSITIVE_INFINITY, AuxBenchCompare.snrDb(ref, ref.clone(), 2), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, AuxBenchCompare.snrDb(new float[2], ref, 2), 0.0);
        assertTrue(Double.isNaN(AuxBenchCompare.snrDb(new float[2], new float[2], 2)));
    }

    @Test
    public void cosineHonoursOffsets() {
        float[] a = {9f, 1f, 0f, 9f};
        float[] b = {0f, 0f, 3f, 0f, 5f};
        // a[1..3) = (1, 0), b[2..4) = (3, 0): parallel
        assertEquals(1.0, AuxBenchCompare.cosine(a, 1, b, 2, 2), 1e-12);
        // (1, 0) against (0, 5): orthogonal
        assertEquals(0.0, AuxBenchCompare.cosine(a, 1, b, 3, 2), 1e-12);
        // opposite direction
        assertEquals(-1.0, AuxBenchCompare.cosine(new float[] {1f, 1f}, 0, new float[] {-2f, -2f}, 0, 2), 1e-12);
        assertTrue(Double.isNaN(AuxBenchCompare.cosine(new float[2], 0, a, 1, 2)));
    }

    @Test
    public void decisionFlipsCountsOnlyCrossingsOfTheThreshold() {
        float[] a = {0.1f, 0.6f, 0.5f, 0.9f, 0.2f};
        float[] b = {0.2f, 0.4f, 0.6f, 0.95f, 0.7f};
        // index 1 (0.6 -> 0.4), 2 (0.5 is not above 0.5 -> 0.6), 4 (0.2 -> 0.7) flip; 0 and 3 do not
        assertEquals(3, AuxBenchCompare.decisionFlips(a, b, 5, 0.5f));
        assertEquals(0, AuxBenchCompare.decisionFlips(a, a, 5, 0.5f));
        assertEquals(1, AuxBenchCompare.decisionFlips(a, b, 2, 0.5f));
    }
}
