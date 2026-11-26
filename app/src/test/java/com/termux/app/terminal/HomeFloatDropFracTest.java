package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Where a Linux window dropped on Home lands as a Float (#388). */
@RunWith(RobolectricTestRunner.class)
public final class HomeFloatDropFracTest {

    private static final RectF SIZE = new RectF(0.2f, 0.1f, 0.8f, 0.5f);

    @Test
    public void theFloatIsCentredAcrossTheFingerWithItsTitlebarUnderIt() {
        // Room 1000 x 2000, finger in the middle: width .6, height .4.
        RectF r = HomeFloatPolicy.dropFrac(SIZE, 500f, 1000f, 1000f, 2000f, 100f, 100f, 40f);
        assertEquals(0.2f, r.left, 1e-4f);
        assertEquals(0.8f, r.right, 1e-4f);
        assertEquals((1000f - 20f) / 2000f, r.top, 1e-4f);
        assertEquals(0.4f, r.height(), 1e-4f);
    }

    @Test
    public void aDropAtAnEdgeIsHeldInsideTheRoomAtTheSameSize() {
        RectF r = HomeFloatPolicy.dropFrac(SIZE, 0f, 2000f, 1000f, 2000f, 100f, 100f, 40f);
        assertEquals(0f, r.left, 1e-4f);
        assertEquals(0.6f, r.width(), 1e-4f);
        assertEquals(1f, r.bottom, 1e-4f);
        assertEquals(0.4f, r.height(), 1e-4f);
    }

    @Test
    public void noRoomKeepsTheSize() {
        RectF r = HomeFloatPolicy.dropFrac(SIZE, 5f, 5f, 0f, 0f, 100f, 100f, 40f);
        assertEquals(SIZE, r);
    }
}
