package com.termux.app.chrome.scene;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VideoCropTest {

    private static final float EPS = 1e-4f;
    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    /** What a SurfaceTexture usually reports: a vertical flip. */
    private static final float[] FLIP_Y = {1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 1, 0, 0, 1, 0, 1};

    private static float[] map(float[] m, float u, float v) {
        return new float[] {m[0] * u + m[4] * v + m[12], m[1] * u + m[5] * v + m[13]};
    }

    @Test
    public void squareOnATallTargetCutsTheSides() {
        float[] r = VideoCrop.coverRect(320, 320, 1080, 2400);
        assertArrayEquals(new float[] {0.275f, 0f, 0.725f, 1f}, r, EPS);
    }

    @Test
    public void landscapeOnATallTargetCutsMostOfTheSides() {
        float[] r = VideoCrop.coverRect(1080, 600, 1080, 2400);
        assertArrayEquals(new float[] {0.375f, 0f, 0.625f, 1f}, r, EPS);
    }

    @Test
    public void portraitOnAWideTargetCutsTopAndBottom() {
        float[] r = VideoCrop.coverRect(600, 1080, 1080, 600);
        float keep = (600f / 1080f) / (1080f / 600f);
        assertArrayEquals(new float[] {0f, 0.5f - keep / 2f, 1f, 0.5f + keep / 2f}, r, EPS);
    }

    @Test
    public void sameAspectOrUnknownSizeIsTheWholePicture() {
        assertArrayEquals(new float[] {0, 0, 1, 1}, VideoCrop.coverRect(540, 1200, 1080, 2400), EPS);
        assertArrayEquals(new float[] {0, 0, 1, 1}, VideoCrop.coverRect(0, 0, 1080, 2400), EPS);
        assertArrayEquals(new float[] {0, 0, 1, 1}, VideoCrop.coverRect(320, 320, 0, 0), EPS);
    }

    @Test
    public void rotationTurnsTheFrameClockwise() {
        float[] p = new float[2];
        // Displayed bottom-left is the frame's bottom-right after a 90 degree clockwise turn.
        VideoCrop.rawFromDisplayed(90, 0f, 0f, p);
        assertArrayEquals(new float[] {1f, 0f}, p, EPS);
        VideoCrop.rawFromDisplayed(180, 0f, 0f, p);
        assertArrayEquals(new float[] {1f, 1f}, p, EPS);
        VideoCrop.rawFromDisplayed(270, 0f, 0f, p);
        assertArrayEquals(new float[] {0f, 1f}, p, EPS);
        VideoCrop.rawFromDisplayed(0, 0.3f, 0.7f, p);
        assertArrayEquals(new float[] {0.3f, 0.7f}, p, EPS);
        VideoCrop.rawFromDisplayed(-90, 0f, 0f, p);   // same as 270
        assertArrayEquals(new float[] {0f, 1f}, p, EPS);
    }

    @Test
    public void unrotatedSquareFillsTheTargetHeightAndCentres() {
        float[] m = new float[16];
        VideoCrop.fill(m, IDENTITY, 320, 320, 0, 1080, 2400);
        assertArrayEquals(new float[] {0.275f, 0f}, map(m, 0f, 0f), EPS);
        assertArrayEquals(new float[] {0.725f, 1f}, map(m, 1f, 1f), EPS);
        assertArrayEquals(new float[] {0.5f, 0.5f}, map(m, 0.5f, 0.5f), EPS);
    }

    @Test
    public void surfaceFlipIsAppliedLast() {
        float[] m = new float[16];
        VideoCrop.fill(m, FLIP_Y, 320, 320, 0, 1080, 2400);
        assertArrayEquals(new float[] {0.275f, 1f}, map(m, 0f, 0f), EPS);
        assertArrayEquals(new float[] {0.725f, 0f}, map(m, 1f, 1f), EPS);
    }

    @Test
    public void rotatedLandscapeClipStandsUprightAndCovers() {
        // A 1080x600 frame with rotate=90 is shown as 600x1080, on a 1080x2400 target.
        float[] m = new float[16];
        VideoCrop.fill(m, IDENTITY, 600, 1080, 90, 1080, 2400);
        float keep = (1080f / 2400f) / (600f / 1080f);
        float u0 = 0.5f - keep / 2f, u1 = 0.5f + keep / 2f;
        // Target bottom-left shows displayed (u0, 0): the frame's (1, u0).
        assertArrayEquals(new float[] {1f, u0}, map(m, 0f, 0f), EPS);
        // Target top-right shows displayed (u1, 1): the frame's (0, u1).
        assertArrayEquals(new float[] {0f, u1}, map(m, 1f, 1f), EPS);
        // Never outside the frame.
        for (float u = 0; u <= 1f; u += 0.25f) {
            for (float v = 0; v <= 1f; v += 0.25f) {
                float[] t = map(m, u, v);
                assertEquals(true, t[0] >= -EPS && t[0] <= 1f + EPS && t[1] >= -EPS && t[1] <= 1f + EPS);
            }
        }
    }
}
