package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SceneFramePolicyTest {

    private static final long MS = 1_000_000L;

    @Test
    public void activeReactionNeverSleeps() {
        assertEquals(0L, SceneFramePolicy.nextCallbackDelayMs(true, 33 * MS, 100 * MS, 100 * MS));
    }

    @Test
    public void everyVsyncAndOffNeverSleep() {
        assertEquals(0L, SceneFramePolicy.nextCallbackDelayMs(false, 0L, 0L, 0L));
        assertEquals(0L, SceneFramePolicy.nextCallbackDelayMs(false, Long.MAX_VALUE, 0L, 5 * MS));
    }

    @Test
    public void ambientSleepsUntilDueLessSlack() {
        long period = 1_000_000_000L / 30;
        // just drew: 33.33 ms period minus 4 ms slack = 29.33 ms, rounded up
        assertEquals(30L, SceneFramePolicy.nextCallbackDelayMs(false, period, 1000 * MS, 1000 * MS));
        // 20 ms later: 9.33 ms left
        assertEquals(10L, SceneFramePolicy.nextCallbackDelayMs(false, period, 1000 * MS, 1020 * MS));
    }

    @Test
    public void overdueDoesNotGoNegative() {
        assertEquals(0L, SceneFramePolicy.nextCallbackDelayMs(false, 33 * MS, 0L, 500 * MS));
    }
}
