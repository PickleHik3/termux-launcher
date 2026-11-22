package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The copied image's size: each half's long side at most 1024 px, two halves only with depth. */
public class SideBySideImageTest {

    @Test
    public void aLargeLandscapePhotoIsCappedOnItsLongSide() {
        assertArrayEquals(new int[] {1024, 768}, SideBySideImage.halfSize(4000, 3000));
        assertArrayEquals(new int[] {2048, 768}, SideBySideImage.sheetSize(4000, 3000, true));
    }

    @Test
    public void aLargePortraitPhotoIsCappedOnItsHeight() {
        assertArrayEquals(new int[] {768, 1024}, SideBySideImage.halfSize(3000, 4000));
        assertArrayEquals(new int[] {1536, 1024}, SideBySideImage.sheetSize(3000, 4000, true));
    }

    @Test
    public void neverWiderThanTwoHalves() {
        int[] sheet = SideBySideImage.sheetSize(12000, 9000, true);
        assertEquals(2 * SideBySideImage.MAX_HALF_PX, sheet[0]);
    }

    @Test
    public void aSmallPhotoIsNotEnlarged() {
        assertArrayEquals(new int[] {800, 600}, SideBySideImage.halfSize(800, 600));
        assertArrayEquals(new int[] {1600, 600}, SideBySideImage.sheetSize(800, 600, true));
    }

    @Test
    public void withoutDepthItIsThePhotoAlone() {
        assertArrayEquals(new int[] {1024, 768}, SideBySideImage.sheetSize(4000, 3000, false));
        assertArrayEquals(new int[] {800, 600}, SideBySideImage.sheetSize(800, 600, false));
    }

    @Test
    public void sampleSizeDecodesSmallButNeverBelowTheHalf() {
        // 12 MP (4000x3000) to 1024x768: 1/2 gives 2000x1500, 1/4 gives 1000x750 (too small).
        assertEquals(2, SideBySideImage.sampleSize(4000, 3000, 1024, 768));
        assertEquals(4, SideBySideImage.sampleSize(8000, 6000, 1024, 768));
        assertEquals(1, SideBySideImage.sampleSize(800, 600, 800, 600));
    }
}
