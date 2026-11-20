package com.termux.app.chrome;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A frame the blur cache hands to the surfaces is finished immutable, so the RenderThread uploads
 * it once instead of on every frame that draws it, and nothing writes it afterwards: every new
 * radius and every re-bake allocates a bitmap of its own.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class FramePublicationTest {

    private FakeWallpaperBlurSource source;
    private WallpaperBlurCache cache;
    private View wallpaperFrame;

    @Before
    public void setUp() {
        source = new FakeWallpaperBlurSource();
        cache = new WallpaperBlurCache(new DecodingSource(source));
        wallpaperFrame = new View(RuntimeEnvironment.getApplication());
    }

    @Test
    public void everyFrameHandedOutIsImmutable() {
        // Radius 0 is the capture itself, 2 the clear frame, 8 the pane's frost: all full-size.
        for (int radius : new int[] {0, 2, 8, 30}) {
            Bitmap frame = cache.obtain(radius, wallpaperFrame);
            assertNotNull(frame);
            assertFalse("radius " + radius + " must be published immutable", frame.isMutable());
        }
    }

    @Test
    public void aReBakeAllocatesInsteadOfWritingThePublishedFrame() {
        Bitmap before = cache.obtain(8, wallpaperFrame);
        int pixel = before.getPixel(0, 0);

        source.vibrancyPercent = 40;
        Bitmap after = cache.obtain(8, wallpaperFrame);

        assertNotSame(before, after);
        assertFalse(after.isMutable());
        assertFalse(before.isRecycled());
        assertEquals(pixel, before.getPixel(0, 0));
    }

    @Test
    public void inksStillReadAPublishedFrame() {
        Bitmap frame = cache.obtain(8, wallpaperFrame);
        int[] row = new int[frame.getWidth()];
        frame.getPixels(row, 0, frame.getWidth(), 0, 0, frame.getWidth(), 1);
        assertEquals(frame.getPixel(0, 0), row[0]);
    }

    @Test
    public void aMutableFrameIsCopiedOnceAndTheMutableOneRecycled() {
        Bitmap drawn = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        drawn.eraseColor(Color.RED);

        Bitmap frozen = FramePublication.frozen(drawn);

        assertNotSame(drawn, frozen);
        assertTrue(drawn.isRecycled());
        assertFalse(frozen.isMutable());
        assertEquals(Color.RED, frozen.getPixel(1, 1));
        // Already immutable: no second copy.
        assertSame(frozen, FramePublication.frozen(frozen));
        assertNull(FramePublication.frozen(null));
        FramePublication.publish(null);
        FramePublication.publish(frozen);
    }

    /** The fake, with its capture decoded the way the activity's is rather than handed in ready. */
    private static final class DecodingSource implements WallpaperBlurCache.Source {
        private final FakeWallpaperBlurSource mFake;

        DecodingSource(FakeWallpaperBlurSource fake) {
            mFake = fake;
        }

        @Override public android.graphics.Rect wallpaperFrameRect() { return mFake.wallpaperFrameRect(); }
        @Override public boolean useManagedWallpaperSource() { return mFake.useManagedWallpaperSource(); }
        @Override public int systemWallpaperId() { return mFake.systemWallpaperId(); }
        @Override public java.io.File managedWallpaperExactFile() { return mFake.managedWallpaperExactFile(); }
        @Override public int orientation() { return mFake.orientation(); }
        @Override public boolean isFrameInUse(Bitmap frame) { return mFake.isFrameInUse(frame); }
        @Override public void onCacheCleared() { mFake.onCacheCleared(); }
        @Override public int glassVibrancyPercent() { return mFake.glassVibrancyPercent(); }
        @Override public Bitmap preBlur(Bitmap sourceBitmap, int blurRadiusDp) {
            return mFake.preBlur(sourceBitmap, blurRadiusDp);
        }

        @Override
        public WallpaperBlurCache.FrameCapture beginCapture(android.graphics.Rect frameRect,
                                                            View wallpaperFrame) {
            WallpaperBlurCache.FrameCapture ready = mFake.beginCapture(frameRect, wallpaperFrame);
            if (ready == null) return null;
            return WallpaperBlurCache.FrameCapture.deferred(() -> ready.ready);
        }
    }
}
