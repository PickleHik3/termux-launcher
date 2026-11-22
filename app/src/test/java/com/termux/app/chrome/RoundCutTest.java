package com.termux.app.chrome;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/**
 * A round cut blends from one rest's to the other's: exactly the leaving one at 0, exactly the
 * arriving one at 1, a straight blend between and clamped outside, so a landing finds the cut
 * already drawn.
 */
public class RoundCutTest {

    private static final float[] SLAB = {0f, 0f, 1080f, 900f, 40f};
    private static final float[] CARD = {32f, 95f, 1048f, 1100f, 60f};

    private static float[] blend(float share) {
        float[] out = new float[RoundCut.SIZE];
        RoundCut.blend(SLAB, CARD, share, out);
        return out;
    }

    @Test public void atZeroIsTheLeavingCut() {
        assertArrayEquals(SLAB, blend(0f), 0f);
    }

    @Test public void atOneIsTheArrivingCut() {
        assertArrayEquals(CARD, blend(1f), 0f);
    }

    @Test public void halfwayIsTheMidpoint() {
        assertArrayEquals(new float[] {16f, 47.5f, 1064f, 1000f, 50f}, blend(0.5f), 1e-4f);
    }

    @Test public void outsideTheRangeClamps() {
        assertArrayEquals(SLAB, blend(-0.3f), 0f);
        assertArrayEquals(CARD, blend(1.7f), 0f);
    }

    @Test public void writesIntoTheCallersArray() {
        float[] out = {9f, 9f, 9f, 9f, 9f};
        RoundCut.blend(SLAB, CARD, 0.25f, out);
        assertArrayEquals(new float[] {8f, 23.75f, 1072f, 950f, 45f}, out, 1e-4f);
    }
}
