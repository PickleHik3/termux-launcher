package com.termux.app.chrome.wallpaper.living;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Seeded k-means: determinism, a clean two-colour split, marks inside their cluster. */
public class ColourClustersTest {

    private static int[] halves(int w, int h) {
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * w + x] = x < w / 2 ? 0xFF2040C0 : 0xFFE0D040;
            }
        }
        return px;
    }

    @Test
    public void sameSeedGivesSameLabels() {
        int w = 40, h = 30;
        int[] px = new int[w * h];
        java.util.Random r = new java.util.Random(5);
        for (int i = 0; i < px.length; i++) px[i] = 0xFF000000 | r.nextInt(0xFFFFFF);
        ColourClusters.Result a = ColourClusters.compute(px, w, h, 6, 42L);
        ColourClusters.Result b = ColourClusters.compute(px, w, h, 6, 42L);
        assertArrayEquals(a.labels, b.labels);
        assertArrayEquals(a.markX, b.markX);
    }

    @Test
    public void twoColourPictureSplitsInTwoAndMarksSitInside() {
        int w = 40, h = 20;
        ColourClusters.Result c = ColourClusters.compute(halves(w, h), w, h, 2, 7L);
        assertEquals(c.labels[0], c.labels[w / 2 - 1]);
        assertNotEquals(c.labels[0], c.labels[w - 1]);
        for (int i = 0; i < 2; i++) {
            assertEquals(i, c.labels[c.markY[i] * w + c.markX[i]]);
            assertEquals(0.5f, c.area[i], 1e-4f);
        }
    }

    @Test
    public void clustersAreNumberedLargestFirst() {
        int w = 40, h = 20;
        int[] px = halves(w, h);
        for (int y = 0; y < h; y++) for (int x = 30; x < w; x++) px[y * w + x] = 0xFF101010;
        ColourClusters.Result c = ColourClusters.compute(px, w, h, 3, 3L);
        assertTrue(c.area[0] >= c.area[1] && c.area[1] >= c.area[2]);
    }

    @Test
    public void boundaryFollowsTheColourEdge() {
        int w = 10, h = 4;
        ColourClusters.Result c = ColourClusters.compute(halves(w, h), w, h, 2, 1L);
        boolean[] b = c.boundary();
        assertTrue(b[w / 2 - 1]);
        assertTrue(!b[0]);
    }
}
