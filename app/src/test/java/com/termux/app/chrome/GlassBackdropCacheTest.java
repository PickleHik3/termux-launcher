package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;

import com.termux.app.theme.SchemeTone;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * What a band is drawn over, and how seldom it is worth asking. Chrome bands repaint constantly, so
 * the point of this class is that the expensive half happens on a wallpaper, palette or mode change
 * and never in a draw.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassBackdropCacheTest {

    private static final int LIGHT_INK = 0xFF345CA8;
    private static final int NIGHT_INK = 0xFFB8C7FF;
    private static final int LIGHT_SURFACE = 0xFFF4F6FB;
    /** Measured behind the status bar in light mode on the reporting device. */
    private static final int STATUS_GLASS_LIGHT = 0xFF6A5755;
    /** Measured behind the A&ndash;Z strip in light mode. */
    private static final int AZ_GLASS_LIGHT = 0xFF657271;
    /** The nominal light glass a band assumes before anything has been measured. */
    private static final int NOMINAL_LIGHT_GLASS = 0xFFE9EDF4;

    private static final Rect STATUS_RECT = new Rect(0, 0, 1080, 96);
    private static final Rect AZ_RECT = new Rect(1020, 300, 1080, 1800);

    /** A sampler that counts its calls and can be told to fail, like a blur still decoding. */
    private static final class RecordingSampler implements GlassBackdropCache.Sampler {
        final List<Rect> calls = new ArrayList<>();
        int answer = STATUS_GLASS_LIGHT;

        @Override
        public int sampleWallpaper(@NonNull Rect screenRect) {
            calls.add(new Rect(screenRect));
            return answer;
        }
    }

    private GlassBackdropCache mCache;
    private RecordingSampler mSampler;

    @Before
    public void setUp() {
        mCache = new GlassBackdropCache();
        mSampler = new RecordingSampler();
        mCache.setFallbackWallpaper(NOMINAL_LIGHT_GLASS);
        mCache.setSampler(mSampler);
    }

    // ------------------------------------------------------------ before the first sample

    @Test
    public void aBandWithNoSamplerAtAllAnswersFromTheNominalGlass() {
        GlassBackdropCache cache = new GlassBackdropCache();
        cache.setFallbackWallpaper(NOMINAL_LIGHT_GLASS);
        assertFalse(cache.hasSample(GlassBackdropCache.Band.STATUS_BAR));
        assertEquals(NOMINAL_LIGHT_GLASS,
            cache.wallpaperUnder(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT));
        // And it is a real answer, not a crash and not a flash of unstyled colour.
        OnGlass.Resolution resolution = cache.resolve(GlassBackdropCache.Band.STATUS_BAR,
            STATUS_RECT, Color.TRANSPARENT, Color.TRANSPARENT, LIGHT_INK, NIGHT_INK,
            LIGHT_SURFACE, OnGlass.TARGET_BODY_TEXT);
        assertEquals(NOMINAL_LIGHT_GLASS, resolution.surface);
        assertTrue(SchemeTone.contrastRatio(resolution.ink, resolution.surface)
            >= OnGlass.TARGET_BODY_TEXT);
    }

    @Test
    public void aBandWhoseWallpaperCannotBeReadYetAlsoFallsBack() {
        mSampler.answer = GlassBackdropCache.UNREADABLE;
        assertEquals(NOMINAL_LIGHT_GLASS,
            mCache.wallpaperUnder(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT));
        assertFalse(mCache.hasSample(GlassBackdropCache.Band.STATUS_BAR));
    }

    @Test
    public void aBandHealsItselfWhenTheWallpaperBecomesReadable() {
        mSampler.answer = GlassBackdropCache.UNREADABLE;
        mCache.wallpaperUnder(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT);
        // No invalidate: the blur frame simply landed on a later frame.
        mSampler.answer = STATUS_GLASS_LIGHT;
        assertEquals(STATUS_GLASS_LIGHT,
            mCache.wallpaperUnder(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT));
        assertTrue(mCache.hasSample(GlassBackdropCache.Band.STATUS_BAR));
    }

    @Test
    public void anEmptyRectIsNeverSampled() {
        mCache.wallpaperUnder(GlassBackdropCache.Band.STATUS_BAR, new Rect());
        assertTrue(mSampler.calls.isEmpty());
    }

    // ------------------------------------------------------------ how often it recomputes

    @Test
    public void aBandIsSampledOnceHoweverOftenItRepaints() {
        for (int frame = 0; frame < 60; frame++) {
            mCache.resolve(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT, Color.TRANSPARENT,
                Color.TRANSPARENT, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, OnGlass.TARGET_BODY_TEXT);
        }
        assertEquals(1, mSampler.calls.size());
    }

    @Test
    public void theResolutionItselfIsMemoisedUntilAnInputMoves() {
        OnGlass.Resolution body = resolveStatus(OnGlass.TARGET_BODY_TEXT);
        assertSame(body, resolveStatus(OnGlass.TARGET_BODY_TEXT));
        OnGlass.Resolution large = resolveStatus(OnGlass.TARGET_LARGE_TEXT);
        assertNotSame(body, large);
        // One band asks in several tiers at once — labels, glyphs, separator dots — and alternating
        // between them must not throw the veil search away each time.
        assertSame(body, resolveStatus(OnGlass.TARGET_BODY_TEXT));
        assertSame(large, resolveStatus(OnGlass.TARGET_LARGE_TEXT));
        assertSame(body, resolveStatus(OnGlass.TARGET_BODY_TEXT));
        // A different ink is a different question.
        assertNotSame(body, mCache.resolve(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            Color.TRANSPARENT, Color.TRANSPARENT, 0xFF7A1F1F, NIGHT_INK, LIGHT_SURFACE,
            OnGlass.TARGET_BODY_TEXT));
        assertSame(body, resolveStatus(OnGlass.TARGET_BODY_TEXT));
    }

    @Test
    public void aNewWallpaperSampleThrowsTheMemoisedAnswersAway() {
        OnGlass.Resolution before = resolveStatus(OnGlass.TARGET_BODY_TEXT);
        mCache.invalidate();
        mSampler.answer = AZ_GLASS_LIGHT;
        OnGlass.Resolution after = resolveStatus(OnGlass.TARGET_BODY_TEXT);
        assertNotSame(before, after);
        assertNotEquals(before.surface, after.surface);
    }

    @Test
    public void aMovedBandIsResampled() {
        mCache.wallpaperUnder(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT);
        mCache.wallpaperUnder(GlassBackdropCache.Band.STATUS_BAR, new Rect(0, 0, 2400, 96));
        assertEquals(2, mSampler.calls.size());
    }

    @Test
    public void everyBandIsMeasuredOnItsOwnRect() {
        mSampler.answer = STATUS_GLASS_LIGHT;
        mCache.wallpaperUnder(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT);
        mSampler.answer = AZ_GLASS_LIGHT;
        mCache.wallpaperUnder(GlassBackdropCache.Band.AZ_STRIP, AZ_RECT);
        assertEquals(STATUS_GLASS_LIGHT,
            mCache.wallpaperUnder(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT));
        assertEquals(AZ_GLASS_LIGHT,
            mCache.wallpaperUnder(GlassBackdropCache.Band.AZ_STRIP, AZ_RECT));
        assertEquals(2, mSampler.calls.size());
        assertEquals(STATUS_RECT, mSampler.calls.get(0));
        assertEquals(AZ_RECT, mSampler.calls.get(1));
    }

    @Test
    public void invalidatingDropsEverySampleAndBumpsTheGeneration() {
        int generation = mCache.generation();
        mCache.wallpaperUnder(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT);
        assertEquals(1, mSampler.calls.size());
        mCache.invalidate();
        assertTrue(mCache.generation() > generation);
        assertFalse(mCache.hasSample(GlassBackdropCache.Band.STATUS_BAR));
        mSampler.answer = AZ_GLASS_LIGHT;
        assertEquals("a new wallpaper has to reach the band", AZ_GLASS_LIGHT,
            mCache.wallpaperUnder(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT));
        assertEquals(2, mSampler.calls.size());
    }

    @Test
    public void changingTheFallbackOrTheSamplerInvalidates() {
        mCache.wallpaperUnder(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT);
        int generation = mCache.generation();
        mCache.setFallbackWallpaper(0xFF101010);
        assertTrue(mCache.generation() > generation);
        assertFalse(mCache.hasSample(GlassBackdropCache.Band.STATUS_BAR));
        generation = mCache.generation();
        mCache.setSampler(null);
        assertTrue(mCache.generation() > generation);
        assertEquals(0xFF101010,
            mCache.wallpaperUnder(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT));
    }

    // ------------------------------------------------------------ the composed answer

    @Test
    public void theBackdropCarriesTheDimAndTheBandsOwnTint() {
        int bare = mCache.backdropUnder(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            Color.TRANSPARENT, Color.TRANSPARENT);
        int dimmed = mCache.backdropUnder(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            0x66000000, Color.TRANSPARENT);
        assertEquals(STATUS_GLASS_LIGHT, bare);
        assertTrue("the user's dim can only darken", SchemeTone.tone(dimmed) < SchemeTone.tone(bare));
        int tinted = mCache.backdropUnder(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT,
            Color.TRANSPARENT, 0x66FFFFFF);
        assertTrue(SchemeTone.tone(tinted) > SchemeTone.tone(bare));
    }

    @Test
    public void theUsersMeasuredStatusBandComesBackLegible() {
        OnGlass.Resolution resolution = resolveStatus(OnGlass.TARGET_BODY_TEXT);
        assertTrue(mCache.hasSample(GlassBackdropCache.Band.STATUS_BAR));
        assertTrue(SchemeTone.contrastRatio(resolution.ink, resolution.surface)
            >= OnGlass.TARGET_BODY_TEXT);
        assertTrue(resolution.veilAlpha() <= OnGlass.MAX_VEIL_ALPHA);
    }

    // ------------------------------------------------------------ sampling helpers

    @Test
    public void averagingIgnoresTransparentPixelsAndSaysSoWhenThereAreNone() {
        assertEquals(GlassBackdropCache.UNREADABLE,
            GlassBackdropCache.averageColor(new int[] {0, 0, 0}, 0, 3));
        assertEquals(GlassBackdropCache.UNREADABLE,
            GlassBackdropCache.averageColor(new int[0], 0, 0));
        int[] pixels = new int[] {0xFF000000, 0xFFFFFFFF, 0x00123456};
        assertEquals(Color.rgb(127, 127, 127),
            GlassBackdropCache.averageColor(pixels, 0, pixels.length));
        // Out-of-range offsets and counts are clamped, not thrown.
        assertEquals(Color.rgb(255, 255, 255),
            GlassBackdropCache.averageColor(pixels, 1, 1));
        assertEquals(GlassBackdropCache.UNREADABLE,
            GlassBackdropCache.averageColor(pixels, 9, 40));
    }

    @Test
    public void sampleBitmapRegionReadsTheRegionAndClampsToTheBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                bitmap.setPixel(x, y, y < 32 ? STATUS_GLASS_LIGHT : Color.BLACK);
            }
        }
        assertEquals(STATUS_GLASS_LIGHT,
            GlassBackdropCache.sampleBitmapRegion(bitmap, new Rect(0, 0, 64, 32)));
        // A rect running off the bitmap is clamped to what exists.
        assertEquals(STATUS_GLASS_LIGHT,
            GlassBackdropCache.sampleBitmapRegion(bitmap, new Rect(-40, -40, 64, 32)));
        // A rect entirely outside it reads as unreadable rather than throwing.
        assertEquals(GlassBackdropCache.UNREADABLE,
            GlassBackdropCache.sampleBitmapRegion(bitmap, new Rect(200, 200, 300, 300)));
        assertEquals(GlassBackdropCache.UNREADABLE,
            GlassBackdropCache.sampleBitmapRegion(null, new Rect(0, 0, 10, 10)));
        // Half and half averages to something between the two.
        int mixed = GlassBackdropCache.sampleBitmapRegion(bitmap, new Rect(0, 0, 64, 64));
        assertTrue(Color.red(mixed) < Color.red(STATUS_GLASS_LIGHT));
        assertTrue(Color.red(mixed) > 0);
    }

    @NonNull
    private OnGlass.Resolution resolveStatus(double target) {
        return mCache.resolve(GlassBackdropCache.Band.STATUS_BAR, STATUS_RECT, Color.TRANSPARENT,
            Color.TRANSPARENT, LIGHT_INK, NIGHT_INK, LIGHT_SURFACE, target);
    }
}
