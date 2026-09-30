package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LiveRadiiTest {

    private static float[] merged(float... requested) {
        float[] out = new float[LiveRadii.MAX_LIVE_RADII];
        int n = LiveRadii.merge(requested, requested.length, out);
        float[] result = new float[n];
        System.arraycopy(out, 0, result, 0, n);
        return result;
    }

    @Test
    public void radiiWithinThreeDpShareTheMiddleOne() {
        assertArrayEquals(new float[] {17f}, merged(16f, 17f, 19f), 0f);
    }

    @Test
    public void zeroAndNegativeRadiiAreNotLive() {
        assertArrayEquals(new float[] {8f, 17f}, merged(0f, 8f, 16f, 17f, 19f, -1f), 0f);
        assertEquals(0, merged().length);
    }

    @Test
    public void moreThanThreeGroupsKeepTheMostUsed() {
        // 5 and 10 tie at one user each; the higher one goes first.
        assertArrayEquals(new float[] {5f, 20f, 40f}, merged(5f, 10f, 20f, 20f, 20f, 40f, 40f), 0f);
    }

    @Test
    public void matchFindsTheNearestLiveRadiusWithinThreeDp() {
        float[] live = {8f, 17f};
        assertEquals(1, LiveRadii.match(live, 2, 19f));
        assertEquals(1, LiveRadii.match(live, 2, 14f));
        assertEquals(0, LiveRadii.match(live, 2, 10f));
        assertEquals(-1, LiveRadii.match(live, 2, 12f));
        assertEquals(-1, LiveRadii.match(live, 2, 0f));
    }

    @Test
    public void divisorIsFourFromTwelveDpUp() {
        assertEquals(2, LiveRadii.divisor(11.9f));
        assertEquals(4, LiveRadii.divisor(12f));
        assertEquals(4, LiveRadii.divisor(40f));
    }

    @Test
    public void slotsRotateThroughTheRing() {
        int slot = 0;
        int[] seen = new int[4];
        for (int i = 0; i < 4; i++) {
            slot = LiveRadii.nextSlot(slot);
            seen[i] = slot;
        }
        assertArrayEquals(new int[] {1, 2, 0, 1}, seen);
    }
}
