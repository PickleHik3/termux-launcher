package com.termux.app.chrome;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The shared frame is sampled by where a surface is, at whatever size the frame is held: the
 * arithmetic every glass surface now shares.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SharedFrameDrawableTest {

    private static final Rect FRAME_RECT = new Rect(0, 0, 1620, 2412);

    /** Maps a point in the surface's own coordinates to the frame pixel it samples. */
    private static float[] sample(Rect frameRect, int frameWidth, int frameHeight, int originX,
                                  int originY, float offsetPx, float localX, float localY) {
        Matrix matrix = new Matrix();
        SharedFrameDrawable.aim(matrix, frameRect, frameWidth, frameHeight, originX, originY, offsetPx);
        Matrix inverse = new Matrix();
        assertTrue(matrix.invert(inverse));
        float[] point = {localX, localY};
        inverse.mapPoints(point);
        return point;
    }

    @Test
    public void aFullSizeFrameIsSampledAtTheSurfacesScreenPosition() {
        float[] pixel = sample(FRAME_RECT, 1620, 2412, 100, 300, 0f, 10f, 20f);
        assertEquals(110f, pixel[0], 0.001f);
        assertEquals(320f, pixel[1], 0.001f);
    }

    @Test
    public void theParallaxOffsetMovesTheSampleFurtherIntoTheFrame() {
        float[] pixel = sample(FRAME_RECT, 1620, 2412, 100, 300, 270f, 0f, 0f);
        assertEquals("the wallpaper went 270px left, so the surface sees 270px further right",
            370f, pixel[0], 0.001f);
        assertEquals(300f, pixel[1], 0.001f);
    }

    @Test
    public void aFrameHeldAtBlurResolutionIsScaledUpToItsRect() {
        // The same rect held at a quarter of the size: the surface still sees screen pixel 370,
        // which is frame pixel 92.5 in the small bitmap.
        float[] pixel = sample(FRAME_RECT, 405, 603, 100, 300, 270f, 0f, 0f);
        assertEquals(92.5f, pixel[0], 0.001f);
        assertEquals(75f, pixel[1], 0.001f);
    }

    @Test
    public void aFrameRectAwayFromTheScreensOriginIsHonoured() {
        Rect frameRect = new Rect(40, 60, 1660, 2472);
        float[] pixel = sample(frameRect, 1620, 2412, 40, 60, 0f, 0f, 0f);
        assertEquals("a surface at the rect's corner samples the frame's first pixel",
            0f, pixel[0], 0.001f);
        assertEquals(0f, pixel[1], 0.001f);
    }

    @Test
    public void theDrawableReportsWhatItShowsAndFindsItselfInsideALayer() {
        Bitmap first = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        Bitmap second = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        SharedFrameDrawable drawable = new SharedFrameDrawable(first, FRAME_RECT, null,
            out -> { out[0] = 0; out[1] = 0; });
        assertSame(first, drawable.frame());
        assertTrue(drawable.shows(first));
        assertFalse(drawable.shows(second));

        // A wallpaper change's replacement keeps the frame it displaced on screen while it fades.
        drawable.setFrame(second, FRAME_RECT, true);
        assertSame(second, drawable.frame());
        assertTrue(drawable.shows(second));
        assertTrue("the retired frame is still drawn under the fade", drawable.shows(first));

        // A new capture for another rect lands outright and lets the old frame go.
        Bitmap third = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        drawable.setFrame(third, new Rect(0, 0, 1080, 2412), true);
        assertFalse(drawable.shows(second));
        assertFalse(drawable.shows(first));

        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
            new android.graphics.drawable.Drawable[] {
                new android.graphics.drawable.ColorDrawable(0xFF000000), drawable});
        assertSame(drawable, SharedFrameDrawable.of(layers));
        assertEquals(null, SharedFrameDrawable.of(new android.graphics.drawable.ColorDrawable(0)));
    }
}
