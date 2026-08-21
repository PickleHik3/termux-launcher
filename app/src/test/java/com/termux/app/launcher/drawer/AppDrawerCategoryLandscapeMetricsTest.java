package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.RectF;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Landscape category geometry, and the 2x2 block's footprint inside its slot.
 *
 * <p>Both were reported from the device: landscape kept the portrait three-column rule, so tiles came
 * out as tall as a whole viewport with their icons scattered and their bottom rows clipped; and the
 * small block filled its entire slot, so its outer icons sat closer to the tile border than the large
 * icons beside them.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class AppDrawerCategoryLandscapeMetricsTest {

    private static final float DENSITY = 2.625f;
    private static final float HEADING_PX = 40f;
    private static final float LABEL_PX = 34f;
    private static final float RADIUS_PX = 48f;
    private static final int CACHE_BYTES = 8 * 1024 * 1024;

    /** 1080x2412 at 2.625, the device the reports came from. */
    private static AppDrawerCategoryGridMetrics portrait() {
        return AppDrawerCategoryGridMetrics.resolve(1080f, 1900f, DENSITY, HEADING_PX, LABEL_PX,
            RADIUS_PX, CACHE_BYTES);
    }

    /** The same device rotated: a body far wider than it is tall. */
    private static AppDrawerCategoryGridMetrics landscape() {
        return AppDrawerCategoryGridMetrics.resolve(2412f, 780f, DENSITY, HEADING_PX, LABEL_PX,
            RADIUS_PX, CACHE_BYTES);
    }

    @Test
    public void portraitKeepsTheMockGrid() {
        assertEquals(2, portrait().columns);
    }

    @Test
    public void landscapeUsesMoreColumnsThanPortrait() {
        AppDrawerCategoryGridMetrics metrics = landscape();
        assertTrue("columns=" + metrics.columns, metrics.columns > portrait().columns);
        assertTrue("columns=" + metrics.columns,
            metrics.columns <= AppDrawerCategoryGridMetrics.HEIGHT_FIT_MAX_COLUMNS);
    }

    /** A body that is wide but not short keeps the mock's three columns. */
    @Test
    public void aTallWideBodyIsLeftOnTheMockGrid() {
        assertEquals(3, AppDrawerCategoryGridMetrics.resolve(1200f, 600f, 1f, 20f, 16f, 24f,
            CACHE_BYTES).columns);
    }

    /** The clipped-bottom-row complaint: a tile has to leave the next row reachable. */
    @Test
    public void landscapeTileFitsTheViewport() {
        AppDrawerCategoryGridMetrics metrics = landscape();
        float viewport = 780f;
        assertTrue("tileSide=" + metrics.tileSidePx,
            metrics.tileSidePx
                <= viewport * AppDrawerCategoryGridMetrics.MAX_TILE_VIEWPORT_FRACTION);
        assertTrue("itemHeight=" + metrics.itemHeightPx, metrics.itemHeightPx < viewport);
    }

    /** The height rule outranks a stored column preference that would not fit the viewport. */
    @Test
    public void aColumnPreferenceCannotForceTilesTallerThanTheViewport() {
        AppDrawerCategoryGridMetrics requestedTwo = AppDrawerCategoryGridMetrics.resolve(2412f, 780f,
            DENSITY, HEADING_PX, LABEL_PX, RADIUS_PX, CACHE_BYTES, 2, 0);
        assertEquals(landscape().columns, requestedTwo.columns);
        assertTrue(requestedTwo.tileSidePx
            <= 780f * AppDrawerCategoryGridMetrics.MAX_TILE_VIEWPORT_FRACTION);
    }

    @Test
    public void smallBlockIsClumpedToOneLargeIcon() {
        RectF slot = new RectF(0f, 0f, 200f, 200f);
        float blockSize = 96f;
        RectF block = AppDrawerCategoryTileView.smallBlockBounds(slot, blockSize);
        assertEquals(blockSize, block.width(), 0.01f);
        assertEquals(blockSize, block.height(), 0.01f);
        // Centred, so the inner padding around the block matches on every side.
        assertEquals(slot.centerX(), block.centerX(), 0.01f);
        assertEquals(slot.centerY(), block.centerY(), 0.01f);
        assertEquals(block.left - slot.left, slot.right - block.right, 0.01f);
        assertEquals(block.top - slot.top, slot.bottom - block.bottom, 0.01f);
    }

    /** A block never grows past the slot it sits in, whatever it is asked for. */
    @Test
    public void smallBlockNeverExceedsItsSlot() {
        RectF slot = new RectF(10f, 10f, 90f, 60f);
        RectF block = AppDrawerCategoryTileView.smallBlockBounds(slot, 400f);
        assertTrue(block.width() <= slot.width() + 0.01f);
        assertTrue(block.height() <= slot.height() + 0.01f);
    }
}
