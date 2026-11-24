package com.termux.app.chrome.scene;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Build;

import com.termux.app.chrome.GlassSource;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The stored frame and the scene's live frame are one kind of input: over the same rect at the
 * same pixel size, a surface hands its program the same aim and frame size from either, so the
 * glass draws alike whichever is out (parallax aside: the scene does not pan).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassSourceParityTest {

    @After
    public void clearRect() {
        LiveSceneFrame.setRect(0, 0, 0, 0);
    }

    private static BitmapShader shaderOf(Bitmap bitmap) {
        return new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    }

    @Test
    public void storedAndSceneFramesOverOneRectAimTheProgramAlike() {
        Rect rect = new Rect(0, 0, 1080, 2400);
        LiveSceneFrame.setRect(rect.left, rect.top, rect.right, rect.bottom);
        Bitmap stored = Bitmap.createBitmap(270, 600, Bitmap.Config.ARGB_8888);
        Bitmap sceneBitmap = Bitmap.createBitmap(270, 600, Bitmap.Config.ARGB_8888);
        LiveSceneFrame.Frame scene = new LiveSceneFrame.Frame(sceneBitmap, shaderOf(sceneBitmap), 1);

        GlassSource source = new GlassSource();
        assertEquals(GlassSource.STORED, source.select(false, null, stored, shaderOf(stored), rect, 0f));
        float[] storedAim = new float[4];
        source.aim(storedAim, 48, 1700);
        int storedW = source.bitmap().getWidth();
        int storedH = source.bitmap().getHeight();

        assertEquals(GlassSource.SCENE, source.select(false, scene, stored, shaderOf(stored), rect, 0f));
        float[] sceneAim = new float[4];
        source.aim(sceneAim, 48, 1700);

        assertArrayEquals(storedAim, sceneAim, 0f);
        assertEquals(storedW, source.bitmap().getWidth());
        assertEquals(storedH, source.bitmap().getHeight());
        assertEquals(rect, source.rect());
    }

    @Test
    public void theSceneIsBoundOnceAndTheStoredFrameComesBackAfterIt() {
        LiveSceneFrame.setRect(0, 0, 100, 100);
        Bitmap stored = Bitmap.createBitmap(25, 25, Bitmap.Config.ARGB_8888);
        BitmapShader storedShader = shaderOf(stored);
        Bitmap sceneBitmap = Bitmap.createBitmap(25, 25, Bitmap.Config.ARGB_8888);
        BitmapShader sceneShader = shaderOf(sceneBitmap);
        Rect rect = new Rect(0, 0, 100, 100);
        GlassSource source = new GlassSource();

        source.select(false, new LiveSceneFrame.Frame(sceneBitmap, sceneShader, 4), stored,
            storedShader, rect, 0f);
        assertSame(sceneShader, source.shader());
        assertFalse(source.crossfades());
        assertTrue(source.takeRebind());
        assertFalse(source.takeRebind());

        // The next publish is bound again.
        source.select(false, new LiveSceneFrame.Frame(sceneBitmap, sceneShader, 5), stored,
            storedShader, rect, 0f);
        assertTrue(source.takeRebind());

        // The scene goes off: the stored frame goes back on, once.
        source.select(false, null, stored, storedShader, rect, 9f);
        assertTrue(source.takeRebind());
        assertFalse(source.takeRebind());
        assertEquals(9f, source.parallaxPx(), 0f);

        // A surface that re-bound its stored frame itself (a frame or look change) holds it.
        source.select(false, new LiveSceneFrame.Frame(sceneBitmap, sceneShader, 5), stored,
            storedShader, rect, 0f);
        assertTrue(source.takeRebind());
        source.storedBound();
        assertTrue(source.takeRebind());
    }
}
