package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;

import com.google.android.material.color.utilities.ColorUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * A chrome band that draws its own frame (the dock's backdrop, the keyboard's stack) covers the
 * page's dim, so its ink and its even-out are measured with no dim under them (measured
 * 2026-10-28 on the emulator: under the dock a bright stripe at L* 82 drew at 87 with the 45% page
 * dim not on screen, while the ink was chosen on a face of 33). A band without a frame still
 * stands on the page's dim.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class EvenOutBandFrameDimTest {

    /** The default page dim, 45%. */
    private static final int PAGE_DIM = 0x73000000;
    /** A bright yellow-green stripe, blurred. */
    private static final int BRIGHT = 0xFFD8E070;
    private static final Rect DOCK = new Rect(43, 1479, 1037, 1584);

    private FakeChromeSurfaces surfaces;
    private ChromeInk ink;

    @Before
    public void setUp() {
        surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.dim = PAGE_DIM;
        ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.backdrops().setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect rect) {
                return BRIGHT;
            }
        });
    }

    @Test
    public void groundDimIsNoneUnderAFrame() {
        assertEquals(Color.TRANSPARENT, ChromeInk.groundDim(true, PAGE_DIM));
        assertEquals(PAGE_DIM, ChromeInk.groundDim(false, PAGE_DIM));
    }

    @Test
    public void aFramedDocksFaceStandsOnNoDim() {
        int dimmed = ink.faceUnder(GlassBackdropCache.Band.DOCK, new Rect(DOCK));
        assertTrue(ink.noteBandFrame(GlassBackdropCache.Band.DOCK, true));
        assertFalse(ink.noteBandFrame(GlassBackdropCache.Band.DOCK, true));
        int framed = ink.faceUnder(GlassBackdropCache.Band.DOCK, new Rect(DOCK));
        assertNotEquals(dimmed, framed);
        assertTrue(ColorUtils.lstarFromArgb(framed) > ColorUtils.lstarFromArgb(dimmed) + 10d);

        // The framed face is the face the same band shows with no page dim at all.
        ink.noteBandFrame(GlassBackdropCache.Band.DOCK, false);
        surfaces.dim = Color.TRANSPARENT;
        ink.invalidate();
        assertEquals(framed, ink.faceUnder(GlassBackdropCache.Band.DOCK, new Rect(DOCK)));
    }

    @Test
    public void anUnframedBandKeepsThePageDim() {
        int before = ink.faceUnder(GlassBackdropCache.Band.KEYBOARD, new Rect(DOCK));
        ink.noteBandFrame(GlassBackdropCache.Band.DOCK, true);
        assertEquals(before, ink.faceUnder(GlassBackdropCache.Band.KEYBOARD, new Rect(DOCK)));
    }
}
