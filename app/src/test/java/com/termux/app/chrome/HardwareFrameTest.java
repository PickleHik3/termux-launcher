package com.termux.app.chrome;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The cache's frames drawn from GPU memory: the drawable twin is HARDWARE, the copy pixel reads
 * use is an eighth of the frame each way, reads go to that copy, and the full-size software frame
 * is let go.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HardwareFrameTest {

    private FakeWallpaperBlurSource source;
    private WallpaperBlurCache cache;
    private View wallpaperFrame;

    @Before
    public void setUp() {
        HardwareFrame.clearForTest();
        source = new FakeWallpaperBlurSource();
        source.frameRect.set(0, 0, 160, 320);
        source.hardwareFrames = true;
        cache = new WallpaperBlurCache(source);
        wallpaperFrame = new View(RuntimeEnvironment.getApplication());
    }

    @After
    public void tearDown() {
        HardwareFrame.clearForTest();
    }

    @Test
    public void aPublishedFrameIsHardwareWithAnEighthScaleSamplingCopy() {
        Bitmap frame = cache.obtain(12, wallpaperFrame);

        assertNotNull(frame);
        assertEquals(Bitmap.Config.HARDWARE, frame.getConfig());
        Bitmap readable = HardwareFrame.readable(frame);
        assertNotNull(readable);
        assertNotSame(frame, readable);
        assertEquals(Bitmap.Config.ARGB_8888, readable.getConfig());
        assertEquals(160 / HardwareFrame.SAMPLE_DIVISOR, readable.getWidth());
        assertEquals(320 / HardwareFrame.SAMPLE_DIVISOR, readable.getHeight());
    }

    @Test
    public void theFullSizeSoftwareFrameIsNotKept() {
        Bitmap frame = cache.obtain(12, wallpaperFrame);
        Bitmap sharp = cache.obtain(0, wallpaperFrame);

        assertEquals(Bitmap.Config.HARDWARE, frame.getConfig());
        assertEquals(Bitmap.Config.HARDWARE, sharp.getConfig());
        // Radius 0 used to be the capture itself, kept alive as the frame; now its twin is the
        // frame, and the full-size capture is recycled once no blur reads it.
        Bitmap capture = source.captured.get(source.captured.size() - 1);
        assertNotSame(capture, sharp);
        assertTrue("the full-size capture is let go", capture.isRecycled());
    }

    @Test
    public void aSourceThatPublishesNoTwinsKeepsTheSoftwareFrame() {
        source.hardwareFrames = false;
        Bitmap frame = cache.obtain(12, wallpaperFrame);

        assertEquals(Bitmap.Config.ARGB_8888, frame.getConfig());
        assertSame("a software frame is read directly", frame, HardwareFrame.readable(frame));
    }

    @Test
    public void aSoftwareCanvasConsumerGetsASoftwareBitmap() {
        Bitmap frame = cache.obtain(12, wallpaperFrame);

        Bitmap readable = HardwareFrame.readable(frame);
        assertNotNull(readable);
        assertFalse(readable.getConfig() == Bitmap.Config.HARDWARE);
        // Drawing it into a software canvas is what a HARDWARE bitmap cannot survive.
        new android.graphics.Canvas(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
            .drawBitmap(readable, 0f, 0f, null);
    }

    @Test
    public void anUnpublishedHardwareBitmapReadsAsNothing() {
        Bitmap stray = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            .copy(Bitmap.Config.HARDWARE, false);

        assertNull(HardwareFrame.readable(stray));
        assertEquals(GlassBackdropCache.UNREADABLE,
            GlassBackdropCache.sampleBitmapRegion(stray, new Rect(0, 0, 8, 8)));
    }

    @Test
    public void samplingAHardwareFrameReadsItsCopyOverTheSameRegion() {
        Bitmap software = Bitmap.createBitmap(160, 320, Bitmap.Config.ARGB_8888);
        software.eraseColor(Color.BLUE);
        // The bottom half red: a sample of the bottom half reads red through the copy.
        for (int y = 160; y < 320; y++)
            for (int x = 0; x < 160; x++) software.setPixel(x, y, Color.RED);
        Bitmap twin = HardwareFrame.publish(software);
        assertNotNull(twin);

        assertEquals(Color.RED, GlassBackdropCache.sampleBitmapRegion(twin,
            new Rect(0, 176, 160, 320)));
        assertEquals(Color.BLUE, GlassBackdropCache.sampleBitmapRegion(twin,
            new Rect(0, 0, 160, 144)));
    }

    @Test
    public void aRegionMapsOntoTheCopyRoundedOutward() {
        Bitmap frame = Bitmap.createBitmap(160, 320, Bitmap.Config.ARGB_8888);
        Bitmap copy = Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888);
        Rect out = new Rect();

        HardwareFrame.mapToReadable(frame, copy, new Rect(4, 12, 20, 13), out);

        assertEquals(new Rect(0, 1, 3, 2), out);
        HardwareFrame.mapToReadable(frame, frame, new Rect(4, 12, 20, 13), out);
        assertEquals(new Rect(4, 12, 20, 13), out);
    }

    @Test
    public void aSampleSideIsAnEighthAndNeverZero() {
        assertEquals(203, HardwareFrame.sampleSize(1620));
        assertEquals(302, HardwareFrame.sampleSize(2412));
        assertEquals(1, HardwareFrame.sampleSize(3));
    }
}
