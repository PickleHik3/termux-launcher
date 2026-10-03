package com.termux.app.chrome.wallpaper.living;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The guided filter on a synthetic edge: a soft, shifted map is pulled onto the guide's edge. */
public class GuidedFilterTest {

    @Test
    public void softMapSnapsToTheGuideEdge() {
        int w = 64, h = 16;
        float[] guide = new float[w * h];
        float[] src = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                guide[y * w + x] = x < 32 ? 0.1f : 0.9f;
                // the model's edge is soft and four pixels late
                src[y * w + x] = Planes.smoothstep(32f, 44f, x);
            }
        }
        float[] out = GuidedFilter.filter(guide, src, w, h, 8, 1e-3f);
        int row = 8 * w;
        assertTrue("left of the edge stays low", out[row + 28] < 0.25f);
        assertTrue("right of the edge is high", out[row + 36] > 0.35f);
        assertTrue("sharper than the source just past the edge", out[row + 34] > src[row + 34]);
    }

    @Test
    public void flatGuideGivesBoxMean() {
        int w = 8, h = 8;
        float[] guide = new float[w * h];
        float[] src = new float[w * h];
        for (int i = 0; i < src.length; i++) src[i] = i % w < 4 ? 0f : 1f;
        float[] out = GuidedFilter.filter(guide, src, w, h, 1, 1e-3f);
        assertEquals(0.5f, out[3 * w + 3], 0.35f);
        assertEquals(0f, out[3 * w + 0], 0.2f);
    }

    @Test
    public void boxMeanOfConstantIsConstantAtBorders() {
        float[] v = new float[6 * 5];
        java.util.Arrays.fill(v, 0.7f);
        float[] m = Planes.boxMean(v, 6, 5, 3);
        for (float f : m) assertEquals(0.7f, f, 1e-5f);
    }
}
