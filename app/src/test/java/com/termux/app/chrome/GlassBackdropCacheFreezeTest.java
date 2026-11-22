package com.termux.app.chrome;

import android.app.Application;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** A crossing measures nothing: a frozen cache never reads the wallpaper, however it is asked. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassBackdropCacheFreezeTest {

    private static final int GRASS = 0xFF336633;
    private static final int SKY = 0xFF6699CC;
    private static final int FALLBACK = 0xFF222222;

    private int reads;
    private int colour;
    private boolean frozen;
    private GlassBackdropCache cache;

    @Before
    public void setUp() {
        reads = 0;
        colour = GRASS;
        frozen = false;
        cache = new GlassBackdropCache();
        cache.setFallbackWallpaper(FALLBACK);
        cache.setFreeze(() -> frozen);
        cache.setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect screenRect) {
                reads++;
                return colour;
            }

            @Override public int sampleWallpaper(@NonNull Rect screenRect, int blurRadiusDp) {
                reads++;
                return colour;
            }

            @Override public GlassBackdropCache.Lightness sampleLightness(@NonNull Rect screenRect) {
                reads++;
                return new GlassBackdropCache.Lightness(10, 50, 90, colour);
            }
        });
        // setSampler invalidates; count from here.
        reads = 0;
    }

    @Test
    public void aBandAskedAtAMovedRectInACrossingKeepsItsSampleAndReadsNothing() {
        Rect rest = new Rect(0, 1800, 1080, 2000);
        assertEquals(GRASS, cache.wallpaperUnder(GlassBackdropCache.Band.DOCK, rest));
        int before = reads;
        frozen = true;
        colour = SKY;
        Rect moved = new Rect(0, 1700, 1080, 1900);
        assertEquals(GRASS, cache.wallpaperUnder(GlassBackdropCache.Band.DOCK, moved));
        assertEquals(before, reads);
    }

    @Test
    public void aBandWithNoSampleAnswersTheFallbackAndReadsNothing() {
        frozen = true;
        assertEquals(FALLBACK, cache.wallpaperUnder(GlassBackdropCache.Band.KEYBOARD,
            new Rect(0, 1000, 1080, 1800)));
        assertEquals(0, reads);
    }

    @Test
    public void theUncachedReadsAnswerNothingInACrossing() {
        frozen = true;
        Rect rect = new Rect(0, 1000, 1080, 1800);
        assertEquals(Color.TRANSPARENT, cache.sampleUncached(rect));
        assertEquals(Color.TRANSPARENT, cache.sampleUncached(rect, 7));
        assertNull(cache.sampleLightnessUncached(rect));
        assertEquals(0, reads);
    }

    @Test
    public void theFirstAskAfterTheCrossingMeasuresAsItAlwaysDid() {
        Rect rest = new Rect(0, 1800, 1080, 2000);
        frozen = true;
        assertEquals(FALLBACK, cache.wallpaperUnder(GlassBackdropCache.Band.DOCK, rest));
        frozen = false;
        colour = SKY;
        assertEquals(SKY, cache.wallpaperUnder(GlassBackdropCache.Band.DOCK, rest));
        assertEquals(1, reads);
    }
}
