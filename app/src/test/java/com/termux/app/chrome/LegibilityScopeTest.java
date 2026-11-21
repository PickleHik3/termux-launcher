package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class LegibilityScopeTest {

    private final Rect item = new Rect(10, 20, 110, 220);
    private final Rect whole = new Rect(0, 0, 1080, 2400);

    @Test
    public void adaptiveMeasuresTheItem() {
        assertSame(item, LegibilityScope.measuredRect(true, item, whole));
    }

    @Test
    public void sharedMeasuresTheWholePicture() {
        assertSame(whole, LegibilityScope.measuredRect(false, item, whole));
    }

    @Test
    public void sharedWithNothingToReadStaysEmpty() {
        Rect empty = new Rect();
        assertTrue(LegibilityScope.measuredRect(false, item, empty).isEmpty());
    }

    @Test
    public void frameRegionScalesIntoTheBitmap() {
        Rect out = new Rect();
        LegibilityScope.frameRegion(new Rect(100, 200, 300, 600), new Rect(0, 0, 1000, 2000),
            500, 1000, out);
        assertEquals(new Rect(50, 100, 150, 300), out);
    }

    @Test
    public void frameRegionIsRelativeToAnOffsetFrame() {
        Rect out = new Rect();
        // A panning wallpaper: the frame starts left of the screen and is wider than it.
        LegibilityScope.frameRegion(new Rect(0, 0, 100, 100), new Rect(-200, 0, 800, 500),
            500, 250, out);
        assertEquals(new Rect(100, 0, 150, 50), out);
    }

    @Test
    public void theWholePictureReadsOnTheWiderGrid() {
        assertEquals(LegibilityScope.WHOLE_PICTURE_GRID,
            LegibilityScope.gridFor(new Rect(whole), whole));
        assertEquals(GlassBackdropCache.SAMPLE_GRID, LegibilityScope.gridFor(item, whole));
    }
}
