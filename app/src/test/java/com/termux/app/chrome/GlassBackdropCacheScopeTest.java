package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassBackdropCacheScopeTest {

    private static final int WHOLE_COLOR = 0xFF204060;
    private static final int ITEM_COLOR = 0xFFA0B0C0;

    private final Rect whole = new Rect(0, 0, 1000, 2000);
    private boolean adaptive;
    private boolean frozen;
    private final List<Rect> meanReads = new ArrayList<>();
    private final List<Rect> radiusReads = new ArrayList<>();
    private final List<Rect> lightnessReads = new ArrayList<>();
    private GlassBackdropCache cache;

    @Before
    public void setUp() {
        cache = new GlassBackdropCache();
        cache.setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect screenRect) {
                meanReads.add(new Rect(screenRect));
                return screenRect.equals(whole) ? WHOLE_COLOR : ITEM_COLOR;
            }

            @Override public int sampleWallpaper(@NonNull Rect screenRect, int radius) {
                radiusReads.add(new Rect(screenRect));
                return screenRect.equals(whole) ? WHOLE_COLOR : ITEM_COLOR;
            }

            @Nullable @Override
            public GlassBackdropCache.Lightness sampleLightness(@NonNull Rect screenRect) {
                lightnessReads.add(new Rect(screenRect));
                return new GlassBackdropCache.Lightness(10, 40, 70, WHOLE_COLOR);
            }
        });
        cache.setScope(new GlassBackdropCache.Scope() {
            @Override public boolean adaptive() {
                return adaptive;
            }

            @NonNull @Override public Rect wholePicture() {
                return whole;
            }
        });
        cache.setFreeze(() -> frozen);
    }

    @Test
    public void sharedReadsTheWholePictureOnceForEveryItemAndRadius() {
        assertEquals(WHOLE_COLOR, cache.sampleUncached(new Rect(0, 0, 10, 10)));
        assertEquals(WHOLE_COLOR, cache.sampleUncached(new Rect(50, 50, 90, 90)));
        assertEquals(WHOLE_COLOR, cache.sampleUncached(new Rect(50, 50, 90, 90), 12));
        assertEquals(WHOLE_COLOR, cache.sampleUncached(new Rect(5, 5, 9, 9), 25));
        cache.invalidate();
        assertEquals(WHOLE_COLOR, cache.sampleUncached(new Rect(0, 0, 10, 10)));
        assertEquals(WHOLE_COLOR, cache.wallpaperUnder(GlassBackdropCache.Band.DOCK,
            new Rect(0, 1900, 1000, 2000)));
        assertEquals(1, meanReads.size());
        assertEquals(whole, meanReads.get(0));
        assertEquals(0, radiusReads.size());
    }

    @Test
    public void sharedLightnessIsReadOnceAcrossInvalidate() {
        assertNotNull(cache.sampleLightnessUncached(new Rect(0, 0, 10, 10)));
        cache.invalidate();
        assertNotNull(cache.sampleLightnessUncached(new Rect(20, 20, 40, 40)));
        assertNotNull(cache.sampleLightnessPastFreeze(new Rect(20, 20, 40, 40)));
        assertEquals(1, lightnessReads.size());
        assertEquals(whole, lightnessReads.get(0));
    }

    @Test
    public void forgettingOrANewWholeRectReReads() {
        cache.sampleUncached(new Rect(0, 0, 10, 10));
        cache.forgetWholePicture();
        cache.sampleUncached(new Rect(0, 0, 10, 10));
        assertEquals(2, meanReads.size());
        whole.set(0, 0, 1000, 2400);
        cache.sampleUncached(new Rect(0, 0, 10, 10));
        assertEquals(3, meanReads.size());
        assertEquals(whole, meanReads.get(2));
    }

    @Test
    public void anUnreadableAnswerIsNotKept() {
        final int[] answer = {Color.TRANSPARENT};
        cache.setSampler(new GlassBackdropCache.Sampler() {
            @Override public int sampleWallpaper(@NonNull Rect screenRect) {
                meanReads.add(new Rect(screenRect));
                return answer[0];
            }
        });
        assertEquals(GlassBackdropCache.UNREADABLE, cache.sampleUncached(new Rect(0, 0, 10, 10)));
        answer[0] = WHOLE_COLOR;
        assertEquals(WHOLE_COLOR, cache.sampleUncached(new Rect(0, 0, 10, 10)));
        assertEquals(2, meanReads.size());
    }

    @Test
    public void anEmptyWholePictureReadsNothing() {
        whole.setEmpty();
        assertEquals(GlassBackdropCache.UNREADABLE, cache.sampleUncached(new Rect(0, 0, 10, 10)));
        assertNull(cache.sampleLightnessUncached(new Rect(0, 0, 10, 10)));
        assertEquals(0, meanReads.size());
    }

    @Test
    public void adaptiveReadsEachItemRect() {
        adaptive = true;
        Rect a = new Rect(0, 0, 10, 10);
        Rect b = new Rect(50, 50, 90, 90);
        assertEquals(ITEM_COLOR, cache.sampleUncached(a));
        assertEquals(ITEM_COLOR, cache.sampleUncached(b));
        cache.sampleUncached(b, 12);
        cache.sampleLightnessUncached(a);
        assertEquals(2, meanReads.size());
        assertEquals(a, meanReads.get(0));
        assertEquals(b, meanReads.get(1));
        assertEquals(1, radiusReads.size());
        assertEquals(b, radiusReads.get(0));
        assertEquals(a, lightnessReads.get(0));
    }

    @Test
    public void frozenReadsNothing() {
        frozen = true;
        assertEquals(GlassBackdropCache.UNREADABLE, cache.sampleUncached(new Rect(0, 0, 10, 10)));
        assertEquals(GlassBackdropCache.UNREADABLE,
            cache.sampleUncached(new Rect(0, 0, 10, 10), 12));
        assertNull(cache.sampleLightnessUncached(new Rect(0, 0, 10, 10)));
        cache.wallpaperUnder(GlassBackdropCache.Band.DOCK, new Rect(0, 0, 10, 10));
        assertEquals(0, meanReads.size() + radiusReads.size() + lightnessReads.size());
    }

    @Test
    public void aNullScopeReadsEveryRect() {
        cache.setScope(null);
        Rect a = new Rect(0, 0, 10, 10);
        Rect b = new Rect(50, 50, 90, 90);
        cache.sampleUncached(a);
        cache.sampleUncached(b);
        assertEquals(2, meanReads.size());
        assertEquals(a, meanReads.get(0));
        assertEquals(b, meanReads.get(1));
    }
}
