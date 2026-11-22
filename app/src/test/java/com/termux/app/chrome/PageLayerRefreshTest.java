package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** When a frozen pages layer is taken again at rest: rate limited, trailing, never idle. */
public class PageLayerRefreshTest {

    private static final long T = PageLayerRefresh.REFRESH_INTERVAL_MS;

    @Test
    public void nothingIsTakenWithoutARequest() {
        PageLayerRefresh refresh = new PageLayerRefresh();
        assertFalse(refresh.due(0));
        assertFalse(refresh.due(10 * T));
        assertEquals(-1L, refresh.waitMs(10 * T));
        refresh.taken(1000);
        assertFalse(refresh.due(1000 + 10 * T));
        assertEquals(-1L, refresh.waitMs(1000 + 10 * T));
    }

    @Test
    public void theFirstRequestIsTakenAtOnce() {
        PageLayerRefresh refresh = new PageLayerRefresh();
        refresh.request();
        assertTrue(refresh.due(5));
        assertEquals(0L, refresh.waitMs(5));
    }

    @Test
    public void atMostOneCapturePerInterval() {
        PageLayerRefresh refresh = new PageLayerRefresh();
        refresh.request();
        refresh.taken(1000);
        refresh.request();
        assertFalse(refresh.due(1000));
        assertFalse(refresh.due(1000 + T - 1));
        assertEquals(T - 1, refresh.waitMs(1001));
        assertTrue(refresh.due(1000 + T));
    }

    @Test
    public void aRequestInsideTheIntervalIsTakenWhenItEnds() {
        PageLayerRefresh refresh = new PageLayerRefresh();
        refresh.request();
        refresh.taken(1000);
        // Many changes inside the interval are one trailing capture.
        for (long at = 1010; at < 1000 + T; at += 40) refresh.request();
        assertEquals(T - 100, refresh.waitMs(1100));
        assertTrue(refresh.due(1000 + T));
        refresh.taken(1000 + T);
        assertFalse(refresh.requested());
        assertEquals(-1L, refresh.waitMs(1000 + 2 * T));
    }

    @Test
    public void aRequestLongAfterTheLastCaptureIsDueAtOnce() {
        PageLayerRefresh refresh = new PageLayerRefresh();
        refresh.request();
        refresh.taken(1000);
        refresh.request();
        assertTrue(refresh.due(1000 + 20 * T));
        assertEquals(0L, refresh.waitMs(1000 + 20 * T));
    }

    @Test
    public void resetStartsOver() {
        PageLayerRefresh refresh = new PageLayerRefresh();
        refresh.request();
        refresh.taken(1000);
        refresh.reset();
        refresh.request();
        assertTrue(refresh.due(1001));
    }

    @Test
    public void onlyAnInvalidationTheGlassDidNotCauseIsAContentChange() {
        assertTrue(PageLayerRefresh.isContentChange(false));
        assertFalse(PageLayerRefresh.isContentChange(true));
    }
}
