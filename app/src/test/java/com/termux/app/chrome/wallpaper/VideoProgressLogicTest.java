package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VideoProgressLogicTest {

    private static final String COPY = "Copying…";
    private static final String APPLY = "Setting wallpaper\u2026";
    private static final String CONV = "Converting GIF to video… %1$d%%";

    @Test
    public void startsIdleAndNotBusy() {
        VideoProgressLogic l = new VideoProgressLogic();
        assertEquals(VideoProgressLogic.Phase.IDLE, l.phase());
        assertFalse(l.busy());
        assertEquals("", l.label(COPY, CONV, APPLY));
    }

    @Test
    public void copyingIsIndeterminateAndBusy() {
        VideoProgressLogic l = new VideoProgressLogic();
        l.start();
        assertTrue(l.busy());
        assertTrue(l.indeterminate());
        assertEquals(COPY, l.label(COPY, CONV, APPLY));
    }

    @Test
    public void zeroProgressStaysCopying() {
        VideoProgressLogic l = new VideoProgressLogic();
        l.start();
        assertFalse(l.onProgress(0f, 0));
        assertEquals(VideoProgressLogic.Phase.COPYING, l.phase());
    }

    @Test
    public void firstProgressSwitchesToConverting() {
        VideoProgressLogic l = new VideoProgressLogic();
        l.start();
        assertTrue(l.onProgress(0.4f, 1000));
        assertEquals(VideoProgressLogic.Phase.CONVERTING, l.phase());
        assertFalse(l.indeterminate());
        assertEquals("Converting GIF to video… 40%", l.label(COPY, CONV, APPLY));
    }

    @Test
    public void updatesAreThrottledToEveryHundredMs() {
        VideoProgressLogic l = new VideoProgressLogic();
        l.start();
        assertTrue(l.onProgress(0.1f, 1000));
        assertFalse(l.onProgress(0.2f, 1050));
        assertTrue(l.onProgress(0.3f, 1100));
        assertEquals(30, l.percent());
    }

    @Test
    public void reachingOneAlwaysDraws() {
        VideoProgressLogic l = new VideoProgressLogic();
        l.start();
        l.onProgress(0.5f, 1000);
        assertTrue(l.onProgress(1f, 1010));
        assertEquals(100, l.percent());
    }

    @Test
    public void doneAndErrorEndTheBusyState() {
        VideoProgressLogic l = new VideoProgressLogic();
        l.start();
        l.onProgress(0.5f, 0);
        l.finish(true);
        assertEquals(VideoProgressLogic.Phase.DONE, l.phase());
        assertFalse(l.busy());
        assertEquals("", l.label(COPY, CONV, APPLY));
        assertFalse(l.onProgress(0.9f, 5000));
        l.start();
        l.finish(false);
        assertEquals(VideoProgressLogic.Phase.ERROR, l.phase());
        assertFalse(l.busy());
    }

    @Test
    public void startAgainResetsTheFraction() {
        VideoProgressLogic l = new VideoProgressLogic();
        l.start();
        l.onProgress(0.8f, 0);
        l.finish(false);
        l.start();
        assertEquals(0, l.percent());
        assertTrue(l.indeterminate());
    }

    @Test
    public void applyingFollowsTheImportAndNeverGoesBackToCopying() {
        VideoProgressLogic l = new VideoProgressLogic();
        l.start();
        l.onProgress(1f, 0);
        l.finish(true);
        l.startApplying();
        assertEquals(VideoProgressLogic.Phase.APPLYING, l.phase());
        assertTrue(l.busy());
        assertTrue(l.indeterminate());
        assertEquals(APPLY, l.label(COPY, CONV, APPLY));
        assertFalse(l.onProgress(0.5f, 1000));
        assertEquals(APPLY, l.label(COPY, CONV, APPLY));
        l.finish(true);
        assertFalse(l.busy());
    }
}
