package com.termux.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * A smooth scroll's cached grid is drawn again only when nothing it was recorded from has moved,
 * and is never used where the live render draws something a recording would not. Each input has
 * its own case: a missed one leaves a stale grid on screen for the rest of the scroll.
 */
public class ScrollRenderCacheTest {

    private static final int TOP_ROW = -40;
    private static final int EXTRA_ROWS = 1;
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1900;
    private static final float OFFSET = 6.5f;
    private static final int OVERLAY = 0x80000000;
    private static final long GENERATION = 12L;

    private static final int API_Q = 29;

    private ScrollRenderCache mCache;

    @Before
    public void setUp() {
        mCache = new ScrollRenderCache();
        mCache.remember(TOP_ROW, EXTRA_ROWS, WIDTH, HEIGHT, OFFSET, true, OVERLAY, GENERATION);
    }

    @Test
    public void sameKeyReuses() {
        assertTrue(mCache.canReuse(TOP_ROW, EXTRA_ROWS, WIDTH, HEIGHT, OFFSET, true, OVERLAY,
            GENERATION));
    }

    @Test
    public void nothingRecordedYetNeverReuses() {
        ScrollRenderCache fresh = new ScrollRenderCache();
        assertFalse(fresh.canReuse(0, 0, 0, 0, 0f, false, 0, 0L));
    }

    @Test
    public void forgottenRecordingReRecords() {
        mCache.forget();
        assertFalse(mCache.canReuse(TOP_ROW, EXTRA_ROWS, WIDTH, HEIGHT, OFFSET, true, OVERLAY,
            GENERATION));
    }

    @Test
    public void rowCrossingReRecords() {
        assertFalse(mCache.canReuse(TOP_ROW + 1, EXTRA_ROWS, WIDTH, HEIGHT, OFFSET, true, OVERLAY,
            GENERATION));
        assertFalse(mCache.canReuse(TOP_ROW - 1, EXTRA_ROWS, WIDTH, HEIGHT, OFFSET, true, OVERLAY,
            GENERATION));
    }

    @Test
    public void extraRowChangeReRecords() {
        assertFalse(mCache.canReuse(TOP_ROW, 0, WIDTH, HEIGHT, OFFSET, true, OVERLAY,
            GENERATION));
    }

    @Test
    public void contentGenerationReRecords() {
        assertFalse(mCache.canReuse(TOP_ROW, EXTRA_ROWS, WIDTH, HEIGHT, OFFSET, true, OVERLAY,
            GENERATION + 1));
    }

    @Test
    public void sizeChangeReRecords() {
        assertFalse(mCache.canReuse(TOP_ROW, EXTRA_ROWS, WIDTH - 1, HEIGHT, OFFSET, true, OVERLAY,
            GENERATION));
        assertFalse(mCache.canReuse(TOP_ROW, EXTRA_ROWS, WIDTH, HEIGHT + 1, OFFSET, true, OVERLAY,
            GENERATION));
    }

    @Test
    public void horizontalOffsetChangeReRecords() {
        assertFalse(mCache.canReuse(TOP_ROW, EXTRA_ROWS, WIDTH, HEIGHT, OFFSET + 0.5f, true,
            OVERLAY, GENERATION));
    }

    @Test
    public void transparencyChangeReRecords() {
        assertFalse(mCache.canReuse(TOP_ROW, EXTRA_ROWS, WIDTH, HEIGHT, OFFSET, false, OVERLAY,
            GENERATION));
        assertFalse(mCache.canReuse(TOP_ROW, EXTRA_ROWS, WIDTH, HEIGHT, OFFSET, true,
            0x40000000, GENERATION));
    }

    @Test
    public void appliesToAPlainHardwareSmoothScroll() {
        assertTrue(ScrollRenderCache.applies(true, API_Q, true, false, 0, false, false));
    }

    @Test
    public void neverAppliesAtRest() {
        assertFalse(ScrollRenderCache.applies(true, API_Q, false, false, 0, false, false));
    }

    @Test
    public void neverAppliesWhileTravelling() {
        assertFalse(ScrollRenderCache.applies(true, API_Q, true, true, 0, false, false));
    }

    @Test
    public void neverAppliesWithFillRows() {
        assertFalse(ScrollRenderCache.applies(true, API_Q, true, false, 2, false, false));
    }

    @Test
    public void neverAppliesWhileSelecting() {
        assertFalse(ScrollRenderCache.applies(true, API_Q, true, false, 0, true, false));
    }

    @Test
    public void neverAppliesBelowApi29() {
        assertFalse(ScrollRenderCache.applies(true, API_Q - 1, true, false, 0, false, false));
    }

    @Test
    public void neverAppliesOnASoftwareCanvas() {
        assertFalse(ScrollRenderCache.applies(false, API_Q, true, false, 0, false, false));
    }

    @Test
    public void neverAppliesToAFrozenCopy() {
        assertFalse(ScrollRenderCache.applies(true, API_Q, true, false, 0, false, true));
    }
}
