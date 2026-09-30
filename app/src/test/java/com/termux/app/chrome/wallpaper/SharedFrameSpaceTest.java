package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SharedFrameSpaceTest {

    @Test
    public void withoutParallaxOnlyTheFrameOriginIsRemoved() {
        assertEquals(90f, SharedFrameSpace.x(100f, 10, 0f), 0f);
        assertEquals(35f, SharedFrameSpace.y(50f, 15), 0f);
    }

    @Test
    public void parallaxOffsetShiftsXIntoTheWideFrame() {
        assertEquals(390f, SharedFrameSpace.x(100f, 10, 300f), 0f);
    }

    @Test
    public void yIgnoresParallax() {
        assertEquals(50f, SharedFrameSpace.y(50f, 0), 0f);
    }

    @Test
    public void rectMovesAsOne() {
        float[] out = new float[4];
        SharedFrameSpace.rect(100f, 200f, 300f, 400f, 0, 20, 150f, out);
        assertArrayEquals(new float[] {250f, 180f, 450f, 380f}, out, 0f);
    }
}
