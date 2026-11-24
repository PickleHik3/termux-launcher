package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The Hidden column stands beside the frame at its top and height, at any pose; plain JVM. */
public class HiddenColumnGeometryTest {

    private static final int W = 1080;
    private static final int GAP = 16;
    private static final int MIN = 96;

    /** The frame of a window W wide, scaled about its top centre: left, top, right, bottom. */
    private static int[] frame(float scale, int top, int unscaledHeight) {
        int width = Math.round(W * scale);
        int left = (W - width) / 2;
        return new int[] {left, top, left + width, top + Math.round(unscaledHeight * scale)};
    }

    @Test public void topAndHeightAreTheFrames_atEveryCardHeight() {
        float[] scales = {0.84f, 0.5f, 0.28f, 0.2f};
        for (float scale : scales) {
            int[] frame = frame(scale, 120, 2200);
            int[] column = HiddenColumnGeometry.rect(frame, W, GAP, MIN);
            assertEquals("top at " + scale, frame[1], column[1]);
            assertEquals("bottom at " + scale, frame[3], column[3]);
        }
    }

    @Test public void standsRightOfTheFrameWhereThereIsRoom() {
        int[] frame = frame(0.28f, 120, 2200);
        int[] column = HiddenColumnGeometry.rect(frame, W, GAP, MIN);
        assertEquals(frame[2] + GAP, column[0]);
        assertTrue(column[2] <= W);
        assertTrue(column[2] - column[0] >= MIN);
    }

    @Test public void staysOnTheWindowWhenTheFrameFillsIt() {
        int[] frame = frame(0.84f, 120, 2200);
        int[] column = HiddenColumnGeometry.rect(frame, W, GAP, MIN);
        assertEquals(W, column[2]);
        assertEquals(MIN, column[2] - column[0]);
    }
}
