package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** How far an image channel's file is shrunk on decode. */
public class ImageChannelTest {

    @Test
    public void smallImageIsNotShrunk() {
        assertEquals(1, ImageChannel.sampleSize(800, 600, 1024));
        assertEquals(1, ImageChannel.sampleSize(1024, 10, 1024));
    }

    @Test
    public void longerSideDecidesAndStepsArePowersOfTwo() {
        assertEquals(2, ImageChannel.sampleSize(1025, 20, 1024));
        assertEquals(2, ImageChannel.sampleSize(2000, 4000, 2048));
        assertEquals(4, ImageChannel.sampleSize(4000, 3000, 1024));
        assertEquals(4, ImageChannel.sampleSize(4096, 100, 1024));
    }

    @Test
    public void photoAndDepthCapsDiffer() {
        assertEquals(2, ImageChannel.sampleSize(3000, 2000, ImageChannel.MAX_SIDE_PX));
        assertEquals(4, ImageChannel.sampleSize(3000, 2000, ImageChannel.DEPTH_MAX_SIDE_PX));
    }

    @Test
    public void noCapMeansNoShrink() {
        assertEquals(1, ImageChannel.sampleSize(9000, 9000, 0));
    }
}
