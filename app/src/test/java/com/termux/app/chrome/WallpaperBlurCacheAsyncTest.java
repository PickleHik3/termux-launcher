package com.termux.app.chrome;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayDeque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** With a worker, a miss answers null now and files the frame when the worker is done. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallpaperBlurCacheAsyncTest {

    private FakeWallpaperBlurSource source;
    private final ArrayDeque<Runnable> worker = new ArrayDeque<>();
    private int framesReady;
    private WallpaperBlurCache cache;
    private View wallpaperFrame;

    @Before
    public void setUp() {
        source = new FakeWallpaperBlurSource();
        cache = new WallpaperBlurCache(source, null,
            WallpaperBlurCache.DEFAULT_MAX_CACHED_WALLPAPER_BLUR_BYTES, worker::add,
            Runnable::run, () -> framesReady++);
        wallpaperFrame = new View(RuntimeEnvironment.getApplication());
    }

    private void runWorker() {
        while (!worker.isEmpty()) worker.poll().run();
    }

    @Test
    public void aMissAnswersNullAndTheFrameLandsWhenTheWorkerIsDone() {
        assertNull(cache.obtain(12, wallpaperFrame));
        assertTrue(cache.isPending(12));
        assertEquals("the capture is handed to the worker, not run here", 1, source.captureCount);
        assertEquals(0, cache.residentRadiiCount());
        assertEquals(0, framesReady);

        runWorker();

        assertFalse(cache.isPending(12));
        assertEquals(1, cache.residentRadiiCount());
        assertEquals(1, framesReady);
        Bitmap frame = cache.obtain(12, wallpaperFrame);
        assertNotNull(frame);
        assertSame("a resident frame is answered inline", frame, cache.obtain(12, wallpaperFrame));
        assertEquals(1, source.captureCount);
    }

    @Test
    public void aSecondRequestForAPendingRadiusDoesNotQueueAnotherJob() {
        assertNull(cache.obtain(12, wallpaperFrame));
        assertNull(cache.obtain(12, wallpaperFrame));
        assertNull(cache.obtain(12, wallpaperFrame));

        assertEquals(1, worker.size());
        assertEquals(1, source.captureCount);
        runWorker();
        assertEquals(1, cache.residentRadiiCount());
        assertEquals(1, framesReady);
    }

    @Test
    public void distinctRadiiEachGetTheirOwnJob() {
        assertNull(cache.obtain(8, wallpaperFrame));
        assertNull(cache.obtain(0, wallpaperFrame));
        assertEquals(2, worker.size());
        runWorker();
        assertTrue(cache.hasRadius(8));
        assertTrue(cache.hasRadius(0));
        assertEquals(2, framesReady);
    }

    @Test
    public void aFrameFromBeforeAClearIsDroppedNotFiled() {
        assertNull(cache.obtain(12, wallpaperFrame));
        cache.clear();
        assertFalse("a clear forgets what was pending", cache.isPending(12));

        runWorker();

        assertEquals(0, cache.residentRadiiCount());
        assertEquals("nothing landed, so nothing asked for a render", 0, framesReady);
        assertTrue("the stale result was recycled", source.captured.get(0).isRecycled());
    }

    @Test
    public void aMovedFrameRectWhileInFlightDropsTheResult() {
        assertNull(cache.obtain(12, wallpaperFrame));
        source.frameRect.set(0, 0, 200, 100);
        // The next request notices the moved source, clears, and queues a fresh capture.
        assertNull(cache.obtain(12, wallpaperFrame));
        assertEquals(2, worker.size());

        runWorker();

        assertEquals(1, cache.residentRadiiCount());
        assertEquals(200, cache.frameRectWidth());
        assertEquals(1, framesReady);
    }
}
