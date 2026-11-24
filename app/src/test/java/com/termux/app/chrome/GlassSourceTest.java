package com.termux.app.chrome;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Which source a glass surface reads, and when its paint or program is pointed at another. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassSourceTest {

    @Test
    public void theLivePictureWinsThenTheSceneThenTheStoredFrame() {
        assertEquals(GlassSource.LIVE, GlassSource.pick(true, true, true));
        assertEquals(GlassSource.LIVE, GlassSource.pick(true, false, false));
        assertEquals(GlassSource.SCENE, GlassSource.pick(false, true, true));
        assertEquals(GlassSource.SCENE, GlassSource.pick(false, true, false));
        assertEquals(GlassSource.STORED, GlassSource.pick(false, false, true));
        assertEquals(GlassSource.NONE, GlassSource.pick(false, false, false));
    }

    @Test
    public void aSceneFrameIsBoundOncePerPublish() {
        assertTrue(GlassSource.rebinds(GlassSource.SCENE, true, 5, false, 0));
        assertFalse(GlassSource.rebinds(GlassSource.SCENE, true, 5, true, 5));
        assertTrue(GlassSource.rebinds(GlassSource.SCENE, true, 6, true, 5));
    }

    @Test
    public void theStoredFrameGoesBackOnceTheSceneIsOff() {
        assertTrue(GlassSource.rebinds(GlassSource.STORED, false, 5, true, 5));
        assertTrue(GlassSource.rebinds(GlassSource.NONE, false, 5, true, 5));
        assertFalse(GlassSource.rebinds(GlassSource.STORED, false, 5, false, 5));
    }

    @Test
    public void aLivePictureOverASceneBindsNothing() {
        assertFalse(GlassSource.rebinds(GlassSource.LIVE, true, 5, false, 0));
        assertFalse(GlassSource.rebinds(GlassSource.LIVE, true, 6, true, 5));
        // The scene went off under the live picture: the stored frame goes back on the paint.
        assertTrue(GlassSource.rebinds(GlassSource.LIVE, false, 5, true, 5));
    }

    @Test
    public void theStoredFrameCarriesItsParallaxAndCrossfade() {
        Bitmap frame = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888);
        BitmapShader shader = new BitmapShader(frame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Rect rect = new Rect(0, 0, 100, 200);
        GlassSource source = new GlassSource();
        assertEquals(GlassSource.STORED, source.select(false, null, frame, shader, rect, 7f));
        assertSame(frame, source.bitmap());
        assertSame(shader, source.shader());
        assertSame(rect, source.rect());
        assertEquals(7f, source.parallaxPx(), 0f);
        assertTrue(source.crossfades());
        // Nothing was bound from a scene, so the stored frame needs no rebind.
        assertFalse(source.takeRebind());
    }

    @Test
    public void aRecycledStoredFrameIsNoSource() {
        Bitmap frame = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888);
        BitmapShader shader = new BitmapShader(frame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        frame.recycle();
        GlassSource source = new GlassSource();
        assertEquals(GlassSource.NONE, source.select(false, null, frame, shader, new Rect(), 0f));
        assertNull(source.bitmap());
        assertFalse(source.crossfades());
    }

    @Test
    public void theLivePictureHoldsNoFrame() {
        Bitmap frame = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888);
        BitmapShader shader = new BitmapShader(frame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        GlassSource source = new GlassSource();
        assertEquals(GlassSource.LIVE, source.select(true, null, frame, shader, new Rect(), 3f));
        assertNull(source.bitmap());
        assertNull(source.shader());
        assertEquals(0f, source.parallaxPx(), 0f);
        assertFalse(source.crossfades());
    }

    @Test
    public void theAimIsTheSharedFramesAim() {
        Bitmap frame = Bitmap.createBitmap(405, 603, Bitmap.Config.ARGB_8888);
        BitmapShader shader = new BitmapShader(frame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Rect rect = new Rect(0, 0, 1620, 2412);
        GlassSource source = new GlassSource();
        source.select(false, null, frame, shader, rect, 12f);
        float[] viaSource = new float[4];
        source.aim(viaSource, 30, 900);
        float[] direct = new float[4];
        SharedFrameDrawable.aim(direct, rect, 405, 603, 30, 900, 12f);
        for (int i = 0; i < 4; i++) assertEquals(direct[i], viaSource[i], 0f);
    }
}
