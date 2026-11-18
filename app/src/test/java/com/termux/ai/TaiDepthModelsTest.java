package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaiDepthModelsTest {
    private static final long GIB = 1024L * 1024L * 1024L;

    @Test
    public void smallerModelOnTierOneAndTheEightGbClass() {
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID, TaiDepthModels.choose(0L));
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID, TaiDepthModels.choose(6 * GIB));
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID, TaiDepthModels.choose(8 * GIB));
    }

    @Test
    public void sharperModelOnTenGbAndUp() {
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, TaiDepthModels.choose(10 * GIB));
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, TaiDepthModels.choose(12 * GIB));
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, TaiDepthModels.choose(16 * GIB));
    }

    @Test
    public void onlyDepthAnything3ReportsDistance() {
        assertTrue(TaiDepthModels.isDistance(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, null));
        assertTrue(TaiDepthModels.isDistance("imported", "depth-anything-3"));
        assertFalse(TaiDepthModels.isDistance(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID, "depth-anything-v2"));
    }
}
