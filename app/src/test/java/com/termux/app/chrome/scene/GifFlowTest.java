package com.termux.app.chrome.scene;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GifFlowTest {

    @Test
    public void takesOnlyAfterTheBufferArrives() {
        GifFlow f = new GifFlow();
        f.markDue();
        assertTrue(f.waiting());
        assertTrue(f.shouldDraw());
        f.posted();
        assertFalse(f.shouldDraw());
        assertFalse(f.shouldTake());
        assertFalse(f.waiting());
        f.onFrameAvailable();
        assertTrue(f.waiting());
        assertTrue(f.shouldTake());
        f.taken();
        assertFalse(f.waiting());
    }

    @Test
    public void aRepaintAskedWhileWaitingIsKept() {
        GifFlow f = new GifFlow();
        f.markDue();
        f.posted();
        f.markDue();
        assertFalse(f.shouldDraw());
        f.onFrameAvailable();
        f.taken();
        assertTrue(f.shouldDraw());
    }

    @Test
    public void aFailedDrawWaitsForNothing() {
        GifFlow f = new GifFlow();
        f.markDue();
        f.drawFailed();
        assertFalse(f.waiting());
        assertFalse(f.shouldTake());
    }
}
