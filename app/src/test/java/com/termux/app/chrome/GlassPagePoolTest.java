package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The shared pages pool: its RAM tiers, the claim rule and the order surfaces are served in. */
public class GlassPagePoolTest {

    private static final long GIB = 1024L * 1024L * 1024L;

    @Test
    public void moreThanFourGibIsQuarterScale() {
        assertEquals(4, GlassPagePool.tier(6 * GIB, false).scale);
        assertEquals(4, GlassPagePool.tier(8 * GIB, false).scale);
        assertEquals(4, GlassPagePool.tier(12 * GIB, false).scale);
        assertEquals(GlassPagePool.QUARTER_POOL_BYTES, GlassPagePool.tier(6 * GIB, false).poolBytes);
        assertEquals(GlassPagePool.QUARTER_POOL_BYTES, GlassPagePool.tier(12 * GIB, false).poolBytes);
    }

    @Test
    public void fourGibOrLessIsEighthScale() {
        assertEquals(8, GlassPagePool.tier(3 * GIB, false).scale);
        assertEquals(8, GlassPagePool.tier(4 * GIB, false).scale);
        assertEquals(GlassPagePool.EIGHTH_POOL_BYTES, GlassPagePool.tier(3 * GIB, false).poolBytes);
        assertEquals(GlassPagePool.EIGHTH_POOL_BYTES, GlassPagePool.tier(4 * GIB, false).poolBytes);
        assertEquals(4, GlassPagePool.tier(4 * GIB + 1, false).scale);
    }

    @Test
    public void aLowRamDeviceIsEighthScaleAtAnySize() {
        assertEquals(8, GlassPagePool.tier(12 * GIB, true).scale);
        assertEquals(GlassPagePool.EIGHTH_POOL_BYTES, GlassPagePool.tier(12 * GIB, true).poolBytes);
        assertEquals(8, GlassPagePool.tier(6 * GIB, true).scale);
    }

    /** Nine slots (three surfaces, three pages each) of a 1080 x 2400 page and a 16 px gutter. */
    @Test
    public void thePoolsHoldNineReferenceSlots() {
        long quarter = 9L * BackdropLayers.slotBytes(1080, 2400, 16, 4);
        long eighth = 9L * BackdropLayers.slotBytes(1080, 2400, 16, 8);
        assertEquals(278L * 608L * 4L * 9L, quarter);
        assertEquals(139L * 304L * 4L * 9L, eighth);
        assertTrue(quarter <= GlassPagePool.QUARTER_POOL_BYTES);
        assertTrue(GlassPagePool.QUARTER_POOL_BYTES >= 6_000_000L
            && GlassPagePool.QUARTER_POOL_BYTES <= 8_000_000L);
        assertTrue(eighth <= GlassPagePool.EIGHTH_POOL_BYTES);
        assertTrue(GlassPagePool.EIGHTH_POOL_BYTES >= 1_500_000L
            && GlassPagePool.EIGHTH_POOL_BYTES <= 2_500_000L);
    }

    @Test
    public void aClaimThatFitsBesideTheHeldPictureKeepsIt() {
        assertEquals(GlassPagePool.CLAIM_BESIDE, GlassPagePool.claim(100, 200, 300, 600));
        assertEquals(GlassPagePool.CLAIM_BESIDE, GlassPagePool.claim(0, 0, 600, 600));
    }

    @Test
    public void aClaimThatFitsOnlyOnceTheHeldPictureGoesReplacesIt() {
        assertEquals(GlassPagePool.CLAIM_REPLACE, GlassPagePool.claim(200, 200, 300, 600));
        assertEquals(GlassPagePool.CLAIM_REPLACE, GlassPagePool.claim(300, 300, 300, 600));
    }

    @Test
    public void aFullPoolGivesNoSlot() {
        assertEquals(GlassPagePool.CLAIM_NONE, GlassPagePool.claim(400, 0, 300, 600));
        assertEquals(GlassPagePool.CLAIM_NONE, GlassPagePool.claim(0, 0, 601, 600));
    }

    @Test
    public void surfacesOverTheMovingPagesAreServedFirstInTheirOwnOrder() {
        boolean[] over = {false, true, false, true, true};
        int[] order = new int[5];
        GlassPagePool.order(over, 5, order);
        assertEquals(1, order[0]);
        assertEquals(3, order[1]);
        assertEquals(4, order[2]);
        assertEquals(0, order[3]);
        assertEquals(2, order[4]);
    }

    @Test
    public void withNoneOverTheWallTheOrderIsUnchanged() {
        boolean[] over = {false, false, false};
        int[] order = new int[3];
        GlassPagePool.order(over, 3, order);
        assertEquals(0, order[0]);
        assertEquals(1, order[1]);
        assertEquals(2, order[2]);
    }

    @Test
    public void pressureAndTheUiHidingDropThePages() {
        assertFalse(GlassPagePool.trimDropsPages(
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE));
        assertTrue(GlassPagePool.trimDropsPages(
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW));
        assertTrue(GlassPagePool.trimDropsPages(
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL));
        assertTrue(GlassPagePool.trimDropsPages(
            android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN));
        assertTrue(GlassPagePool.trimDropsPages(
            android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND));
    }

    @Test
    public void theWallOverlapIsARectOverlap() {
        assertTrue(GlassPagePool.overlaps(0, 1800, 1080, 2400, 0, 0, 1080, 2400));
        assertFalse(GlassPagePool.overlaps(0, 2400, 1080, 2500, 0, 0, 1080, 2400));
        assertFalse(GlassPagePool.overlaps(0, 0, 0, 0, 0, 0, 1080, 2400));
    }
}
