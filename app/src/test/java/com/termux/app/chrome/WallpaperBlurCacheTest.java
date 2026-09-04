package com.termux.app.chrome;

import android.app.Application;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallpaperBlurCacheTest {

    private FakeWallpaperBlurSource source;
    private WallpaperBlurCache cache;
    private View wallpaperFrame;

    @Before
    public void setUp() {
        source = new FakeWallpaperBlurSource();
        cache = new WallpaperBlurCache(source);
        wallpaperFrame = new View(RuntimeEnvironment.getApplication());
    }

    @Test
    public void aResidentRadiusIsReusedInsteadOfReblurred() {
        Bitmap first = cache.obtain(12, wallpaperFrame);
        Bitmap again = cache.obtain(12, wallpaperFrame);

        assertSame(first, again);
        assertEquals(1, source.captureCount);
    }

    @Test
    public void independentlyTunedRadiiEachKeepTheirOwnFrame() {
        Bitmap dock = cache.obtain(12, wallpaperFrame);
        Bitmap status = cache.obtain(30, wallpaperFrame);

        assertNotSame(dock, status);
        assertEquals(2, source.captureCount);
        // Alternating between two tuned radii must not re-blur either of them.
        assertSame(dock, cache.obtain(12, wallpaperFrame));
        assertSame(status, cache.obtain(30, wallpaperFrame));
        assertEquals(2, source.captureCount);
    }

    @Test
    public void theLeastRecentlyUsedRadiusIsEvictedAtTheCap() {
        cache.obtain(4, wallpaperFrame);
        cache.obtain(8, wallpaperFrame);
        cache.obtain(12, wallpaperFrame);
        assertEquals(WallpaperBlurCache.MAX_CACHED_WALLPAPER_BLUR_RADII, cache.residentRadiiCount());

        // Touch the oldest so recency, not insertion order, decides what goes.
        cache.obtain(4, wallpaperFrame);
        cache.obtain(16, wallpaperFrame);

        assertEquals(WallpaperBlurCache.MAX_CACHED_WALLPAPER_BLUR_RADII, cache.residentRadiiCount());
        assertFalse("the least recently used radius should be gone", cache.hasRadius(8));
        assertTrue(cache.hasRadius(4));
        assertTrue(cache.hasRadius(12));
        assertTrue(cache.hasRadius(16));
    }

    @Test
    public void theByteBudgetEvictsBeforeTheRadiusCapDoes() {
        Bitmap probe = cache.obtain(4, wallpaperFrame);
        long frameBytes = probe.getAllocationByteCount();
        // Room for one frame and a half: the second frame in must push the first out.
        WallpaperBlurCache budgeted = new WallpaperBlurCache(source, null, frameBytes * 3 / 2);

        budgeted.obtain(4, wallpaperFrame);
        budgeted.obtain(8, wallpaperFrame);

        assertEquals(1, budgeted.residentRadiiCount());
        assertFalse(budgeted.hasRadius(4));
        assertTrue("the frame just cut always stays, however large", budgeted.hasRadius(8));
        assertTrue(budgeted.residentBytes() <= frameBytes * 3 / 2);
    }

    @Test
    public void anEvictedFrameSomeViewIsStillDrawingIsDroppedWithoutRecycling() {
        Bitmap doomed = cache.obtain(8, wallpaperFrame);
        source.inUse.add(doomed);
        cache.obtain(12, wallpaperFrame);
        cache.obtain(16, wallpaperFrame);
        cache.obtain(20, wallpaperFrame);

        assertFalse(cache.hasRadius(8));
        assertFalse("recycling a frame a view holds crashes its next draw", doomed.isRecycled());
    }

    @Test
    public void aNewSystemWallpaperInvalidatesEveryResidentRadius() {
        cache.obtain(8, wallpaperFrame);
        cache.obtain(12, wallpaperFrame);
        int clearsBefore = source.clearedCount;
        source.systemWallpaperId = 8;

        cache.obtain(8, wallpaperFrame);

        assertEquals(1, cache.residentRadiiCount());
        assertFalse(cache.hasRadius(12));
        assertEquals("a changed wallpaper drops the whole cache once",
            clearsBefore + 1, source.clearedCount);
    }

    @Test
    public void switchingToTheManagedWallpaperSourceInvalidatesTheCache() {
        Bitmap system = cache.obtain(8, wallpaperFrame);
        source.managedSource = true;

        Bitmap managed = cache.obtain(8, wallpaperFrame);

        assertNotSame(system, managed);
        assertEquals(2, source.captureCount);
    }

    /**
     * The shipped rotation race: {@code onConfigurationChanged} arrives before the window is
     * re-laid out, so a crop taken during that pass records the outgoing orientation's frame rect.
     * With only the rect to compare, that stale frame matched itself forever after — landscape kept
     * showing a mismatched, brighter wallpaper region with a hard seam at the pane's left edge.
     */
    @Test
    public void aFrameCapturedBeforeTheNewLayoutIsNotReusedAfterIt() {
        Bitmap portrait = cache.obtain(8, wallpaperFrame);

        // The configuration flips first; the window has not been re-laid out, so the frame rect the
        // source reports is still the portrait one.
        source.orientation = Configuration.ORIENTATION_LANDSCAPE;
        Bitmap capturedMidRotation = cache.obtain(8, wallpaperFrame);
        assertNotSame("the portrait frame must not survive the orientation change",
            portrait, capturedMidRotation);

        // Now the new layout lands and the frame rect finally describes landscape.
        source.frameRect.set(0, 0, 200, 100);
        Bitmap landscape = cache.obtain(8, wallpaperFrame);

        assertNotSame("the mid-rotation frame must not match itself once layout catches up",
            capturedMidRotation, landscape);
        assertEquals(200, cache.frameRectWidth());
        assertEquals(100, cache.frameRectHeight());
        assertEquals(3, source.captureCount);
    }

    @Test
    public void aFullFrameRequestIsAnsweredWithTheSharedFrameRatherThanACopy() {
        Bitmap full = cache.obtain(8, wallpaperFrame);
        Rect frameRect = new Rect();
        cache.copyFrameRect(frameRect);

        assertSame("a full-screen crop must not allocate a second full-screen bitmap",
            full, cache.crop(8, frameRect, wallpaperFrame));
    }

    @Test
    public void aSmallerTargetIsCutFromTheSharedFrame() {
        cache.obtain(8, wallpaperFrame);

        Bitmap crop = cache.crop(8, new Rect(0, 150, 100, 200), wallpaperFrame);

        assertEquals(100, crop.getWidth());
        assertEquals(50, crop.getHeight());
        assertEquals("cropping must not re-capture the wallpaper", 1, source.captureCount);
    }

    @Test
    public void clearingEmptiesTheCacheAndTellsTheOutsideWorld() {
        Bitmap frame = cache.obtain(8, wallpaperFrame);
        int clearsBefore = source.clearedCount;

        cache.clear();

        assertEquals(0, cache.residentRadiiCount());
        assertTrue(frame.isRecycled());
        assertEquals(0, cache.frameRectWidth());
        assertEquals(clearsBefore + 1, source.clearedCount);
        // And the next request captures again rather than trusting the emptied identity.
        cache.obtain(8, wallpaperFrame);
        assertEquals(2, source.captureCount);
    }
}
