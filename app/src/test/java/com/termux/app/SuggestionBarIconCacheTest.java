package com.termux.app;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.LruCache;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarIconCacheTest {

    private SuggestionBarView suggestionBarView;
    private LruCache<String, Drawable> renderedIconCache;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        suggestionBarView = new SuggestionBarView(context, null);
        renderedIconCache = ReflectionHelpers.getField(suggestionBarView, "normalizedIconCache");
    }

    @Test
    public void clearAppCache_evictsRenderedIconBitmaps() {
        renderedIconCache.put("cached-app", new ColorDrawable(0xFF112233));

        suggestionBarView.clearAppCache();

        assertEquals(0, renderedIconCache.size());
    }

    @Test
    public void persistedPinnedIconMutation_evictsRenderedIconBitmaps() {
        renderedIconCache.put("cached-pinned-app", new ColorDrawable(0xFF445566));

        ReflectionHelpers.callInstanceMethod(suggestionBarView, "persistPinsAndReload");

        assertEquals(0, renderedIconCache.size());
    }

    // ------------------------------------------------------------------ byte budgeting

    /**
     * The cache is shared with the app drawer, which asks for icons at its own size and scrolls a
     * whole catalogue through it. A count cap admits wildly different amounts of pixel data
     * depending on which sizes are live — at 4x density a single 48dp entry is ~294KB, so the old
     * 96-entry cap allowed ~28MB. The budget is bytes.
     */
    @Test
    public void theBudget_isOneTwelfthOfTheHeapClampedTo6And16Megabytes() {
        assertEquals(6 * 1024 * 1024, SuggestionBarView.resolveIconCacheBudgetBytes(0));
        assertEquals(6 * 1024 * 1024, SuggestionBarView.resolveIconCacheBudgetBytes(48));
        // 72MB / 12 = 6MB, exactly the floor.
        assertEquals(6 * 1024 * 1024, SuggestionBarView.resolveIconCacheBudgetBytes(72));
        assertEquals(8 * 1024 * 1024, SuggestionBarView.resolveIconCacheBudgetBytes(96));
        assertEquals(16 * 1024 * 1024, SuggestionBarView.resolveIconCacheBudgetBytes(192));
        assertEquals(16 * 1024 * 1024, SuggestionBarView.resolveIconCacheBudgetBytes(512));
    }

    @Test
    public void theLiveCacheBudget_staysInsideTheClamp() {
        assertTrue(renderedIconCache.maxSize() >= 6 * 1024 * 1024);
        assertTrue(renderedIconCache.maxSize() <= 16 * 1024 * 1024);
    }

    @Test
    public void aBitmapEntryCostsItsPixels_andARenderedIconCostsTwice() {
        Bitmap bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
        int pixels = bitmap.getAllocationByteCount();
        Resources resources = RuntimeEnvironment.getApplication().getResources();

        assertEquals(pixels,
            SuggestionBarView.renderedIconCacheEntrySize(new BitmapDrawable(resources, bitmap)));
        // A rendered icon retains the clean pre-shadow artwork beside the display bitmap.
        Object rendered = ReflectionHelpers.callConstructor(
            renderedIconDrawableClass(),
            ReflectionHelpers.ClassParameter.from(Resources.class, resources),
            ReflectionHelpers.ClassParameter.from(Bitmap.class, bitmap),
            ReflectionHelpers.ClassParameter.from(Bitmap.class,
                Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)));
        assertEquals(pixels * 2, SuggestionBarView.renderedIconCacheEntrySize((Drawable) rendered));
    }

    @Test
    public void aDrawableWithoutPixels_stillOccupiesOneSlotSoClearingReturnsToZero() {
        assertEquals(1, SuggestionBarView.renderedIconCacheEntrySize(new ColorDrawable(0xFF000000)));
        assertEquals(1, SuggestionBarView.renderedIconCacheEntrySize(null));

        renderedIconCache.put("a", new ColorDrawable(0xFF000000));
        renderedIconCache.put("b", new ColorDrawable(0xFF111111));
        assertEquals(2, renderedIconCache.size());

        renderedIconCache.evictAll();
        assertEquals(0, renderedIconCache.size());
    }

    @Test
    public void oversubscribingTheBudget_evictsTheLeastRecentlyUsedBitmaps() {
        int budget = renderedIconCache.maxSize();
        // 512x512 ARGB_8888 = 1MB each, so a handful of these overruns even the 16MB ceiling.
        int perEntry = 512 * 512 * 4;
        int entries = (budget / perEntry) + 4;

        for (int i = 0; i < entries; i++) {
            renderedIconCache.put("icon@" + i, new BitmapDrawable(
                RuntimeEnvironment.getApplication().getResources(),
                Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)));
        }

        assertTrue("the cache must stay inside its byte budget",
            renderedIconCache.size() <= budget);
        assertTrue("something must actually have been evicted",
            renderedIconCache.evictionCount() > 0);
        assertNull("the oldest entry is the one that goes", renderedIconCache.get("icon@0"));
        assertNotNull("the newest entry survives", renderedIconCache.get("icon@" + (entries - 1)));

        renderedIconCache.evictAll();
        assertEquals(0, renderedIconCache.size());
    }

    private static Class<?> renderedIconDrawableClass() {
        for (Class<?> candidate : SuggestionBarView.class.getDeclaredClasses()) {
            if ("RenderedIconDrawable".equals(candidate.getSimpleName())) return candidate;
        }
        throw new AssertionError("RenderedIconDrawable is gone; the icon cache accounting moved with it");
    }
}
