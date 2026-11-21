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
    public void aFramedBandThatDrawsNoGroundStandsOnNoDim() {
        GlassBackdropCache.Band band = GlassBackdropCache.Band.AZ_STRIP;
        int dimmed = ink.faceUnder(band, new Rect(DOCK));
        assertTrue(ink.noteBandFrame(band, true));
        assertFalse(ink.noteBandFrame(band, true));
        int framed = ink.faceUnder(band, new Rect(DOCK));
        assertNotEquals(dimmed, framed);
        assertTrue(ColorUtils.lstarFromArgb(framed) > ColorUtils.lstarFromArgb(dimmed) + 10d);

        // The framed face is the face the same band shows with no page dim at all.
        ink.noteBandFrame(band, false);
        surfaces.dim = Color.TRANSPARENT;
        ink.invalidate();
        assertEquals(framed, ink.faceUnder(band, new Rect(DOCK)));
    }

    /**
     * Prototype #283: the dock's glass draws the page's ground over its frame, so a framed dock's
     * face keeps the dim (gate on); with the gate off it stands on none, as the keyboard's does.
     */
    @Test
    public void aFramedDocksFaceFollowsTheGlassGroundGate() {
        int dimmed = ink.faceUnder(GlassBackdropCache.Band.DOCK, new Rect(DOCK));
        assertTrue(ink.noteBandFrame(GlassBackdropCache.Band.DOCK, true));
        int framed = ink.faceUnder(GlassBackdropCache.Band.DOCK, new Rect(DOCK));
        if (GlassGround.ON) {
            assertEquals(dimmed, framed);
        } else {
            assertTrue(ColorUtils.lstarFromArgb(framed) > ColorUtils.lstarFromArgb(dimmed) + 10d);
        }
    }

    /**
     * Prototype #283: the face a page's dock will show at its rest, measured from another page
     * over that page's ground, is the face measured at that rest: the crossing lands on it.
     */
    @Test
    public void theArrivingPagesDockFaceIsMeasuredOverItsGround() {
        if (!GlassGround.ON) return;
        ink.noteBandFrame(GlassBackdropCache.Band.DOCK, true);
        int tint = 0xFF202020;
        surfaces.dim = Color.TRANSPARENT;
        ink.invalidate();
        int fromHome = ink.faceUnderGlass(GlassBackdropCache.Band.DOCK, new Rect(DOCK), 0.3f, tint,
            PAGE_DIM);
        surfaces.dim = PAGE_DIM;
        ink.invalidate();
        int atRest = ink.faceUnderGlass(GlassBackdropCache.Band.DOCK, new Rect(DOCK), 0.3f, tint);
        assertEquals(atRest, fromHome);
        assertNotEquals(fromHome, ink.faceUnderGlass(GlassBackdropCache.Band.DOCK, new Rect(DOCK),
            0.3f, tint, Color.TRANSPARENT));
    }

    @Test
    public void anUnframedBandKeepsThePageDim() {
        int before = ink.faceUnder(GlassBackdropCache.Band.KEYBOARD, new Rect(DOCK));
        ink.noteBandFrame(GlassBackdropCache.Band.DOCK, true);
        assertEquals(before, ink.faceUnder(GlassBackdropCache.Band.KEYBOARD, new Rect(DOCK)));
    }

    /**
     * ADR 0031 section 4: the Terminal's Wallpaper dim is everything around the panes. A glass
     * slab's frame covers the ground strip, so its text stands on no dim, at rest and through a
     * crossing alike; a pane with no slab stands on the page's ground.
     */
    @Test
    public void aGlassPaneStandsOnNoDimAndABarePaneOnTheGround() {
        assertEquals(Color.TRANSPARENT, ChromeInk.paneStandsOnDim(true, PAGE_DIM));
        assertEquals(PAGE_DIM, ChromeInk.paneStandsOnDim(false, PAGE_DIM));
        // The crossing (Home's ground under the pane's rest) and the rest (the Terminal's) give
        // a glass pane the same dim: no step at the landing.
        assertEquals(ChromeInk.paneStandsOnDim(true, Color.TRANSPARENT),
            ChromeInk.paneStandsOnDim(true, PAGE_DIM));
    }

    /** The pane's answer through the ink, both phases on the one rule. */
    @Test
    public void aGlassPanesAnswerIsTheSameOverEitherPagesGround() {
        surfaces.paneSlabs = true;
        Rect pane = new Rect(40, 300, 1040, 1300);
        surfaces.dim = Color.TRANSPARENT;
        ink.invalidate();
        OnGlass.Resolution overHome = ink.terminalPane(pane, new Object(), 0x66202020,
            0xFF202020, 0xFFE0E0E0, 0xFF909090);
        surfaces.dim = PAGE_DIM;
        ink.invalidate();
        OnGlass.Resolution overTerminal = ink.terminalPane(pane, new Object(), 0x66202020,
            0xFF202020, 0xFFE0E0E0, 0xFF909090);
        assertEquals(overHome.surface, overTerminal.surface);
        assertEquals(overHome.veil, overTerminal.veil);
    }
}
