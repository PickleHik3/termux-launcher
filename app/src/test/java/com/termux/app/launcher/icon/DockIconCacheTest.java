package com.termux.app.launcher.icon;

import android.app.Application;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The rendered-icon subsystem in isolation: no view inflation, no dock. Robolectric is only here
 * because the cache renders real {@link Bitmap}s.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class DockIconCacheTest {

    private Resources resources;
    private Drawable defaultIcon;
    private int defaultIconRequests;

    @Before
    public void setUp() {
        resources = RuntimeEnvironment.getApplication().getResources();
        defaultIcon = new ColorDrawable(0xFF334455);
        defaultIconRequests = 0;
    }

    private DockIconCache cacheWithMemoryClass(int memoryClassMb) {
        return new DockIconCache(resources, memoryClassMb, () -> {
            defaultIconRequests++;
            return defaultIcon;
        });
    }

    private static LauncherAppEntry entry(String id) {
        return new LauncherAppEntry(new AppRef("com.example." + id, "Main"), id,
            new ColorDrawable(0xFF00FF00));
    }

    // ------------------------------------------------------------------ byte budgeting

    /**
     * The cache is shared with the app drawer, which asks for icons at its own size and scrolls a
     * whole catalogue through it. A count cap admits wildly different amounts of pixel data
     * depending on which sizes are live — at 4x density a single 48dp entry is ~294KB, so the old
     * 96-entry cap allowed ~28MB. The budget is bytes.
     */
    @Test
    public void theBudget_isOneEighthOfTheHeapClampedTo8And32Megabytes() {
        assertEquals(8 * 1024 * 1024, DockIconCache.resolveBudgetBytes(0));
        assertEquals(8 * 1024 * 1024, DockIconCache.resolveBudgetBytes(48));
        // 64MB / 8 = 8MB, exactly the floor.
        assertEquals(8 * 1024 * 1024, DockIconCache.resolveBudgetBytes(64));
        assertEquals(12 * 1024 * 1024, DockIconCache.resolveBudgetBytes(96));
        assertEquals(24 * 1024 * 1024, DockIconCache.resolveBudgetBytes(192));
        assertEquals(32 * 1024 * 1024, DockIconCache.resolveBudgetBytes(512));
        // A nonsensical memory class still yields a usable floor rather than a zero-size cache.
        assertEquals(8 * 1024 * 1024, DockIconCache.resolveBudgetBytes(-64));
    }

    @Test
    public void theLiveBudget_comesFromTheMemoryClassHandedIn() {
        assertEquals(8 * 1024 * 1024, cacheWithMemoryClass(0).budgetBytes());
        assertEquals(12 * 1024 * 1024, cacheWithMemoryClass(96).budgetBytes());
        assertEquals(32 * 1024 * 1024, cacheWithMemoryClass(4096).budgetBytes());
    }

    /** An unusable ActivityManager is not a reason to render nothing: the floor applies. */
    @Test
    public void memoryClass_fallsBackToZeroWithoutAContext() {
        assertEquals(0, DockIconCache.memoryClassMb(null));
    }

    @Test
    public void aBitmapEntryCostsItsPixels_andARenderedIconCostsTwice() {
        Bitmap bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
        int pixels = bitmap.getAllocationByteCount();

        assertEquals(pixels, DockIconCache.entrySize(new BitmapDrawable(resources, bitmap)));

        // A rendered icon retains the clean pre-shadow artwork beside the display bitmap.
        Drawable rendered = cacheWithMemoryClass(0).normalize(
            new ColorDrawable(0xFF112233), 192, true, false);
        assertTrue(rendered instanceof RenderedIconDrawable);
        assertEquals(pixels * 2, DockIconCache.entrySize(rendered));
    }

    @Test
    public void aDrawableWithoutPixels_stillOccupiesOneSlotSoClearingReturnsToZero() {
        assertEquals(1, DockIconCache.entrySize(new ColorDrawable(0xFF000000)));
        assertEquals(1, DockIconCache.entrySize(null));
        assertEquals(1, DockIconCache.entrySize(new BitmapDrawable(resources, (Bitmap) null)));
    }

    // ------------------------------------------------------------------ caching and eviction

    @Test
    public void theSameEntryAtTheSameSize_isTheSameDrawableInstance() {
        DockIconCache cache = cacheWithMemoryClass(0);
        LauncherAppEntry app = entry("a");

        Drawable first = cache.icon(app, 48);
        assertSame("a drawer cell and a dock icon of one size share one drawable",
            first, cache.icon(app, 48));
        // The pixel size is part of the key, so a different size is a different render.
        assertNotSame(first, cache.icon(app, 64));
        assertTrue(cache.sizeBytes() > 0);
    }

    @Test
    public void anUnknownSize_returnsTheRawArtworkAndCachesNothing() {
        DockIconCache cache = cacheWithMemoryClass(0);
        LauncherAppEntry app = entry("a");

        assertSame(app.icon, cache.icon(app, 0));
        assertSame(app.icon, cache.icon(app, -12));
        assertEquals(0, cache.sizeBytes());
    }

    @Test
    public void anEntryWithoutArtwork_fallsBackToTheDefaultIconSourceOnlyThen() {
        DockIconCache cache = cacheWithMemoryClass(0);
        LauncherAppEntry withArtwork = entry("a");
        LauncherAppEntry withoutArtwork =
            new LauncherAppEntry(new AppRef("com.example.b", "Main"), "b", null);

        cache.icon(withArtwork, 48);
        assertEquals("artwork on the entry must not touch the package manager", 0, defaultIconRequests);

        assertTrue(cache.icon(withoutArtwork, 48) instanceof RenderedIconDrawable);
        assertEquals(1, defaultIconRequests);
    }

    @Test
    public void invalidateAll_returnsOccupancyToZeroAndForcesARerender() {
        DockIconCache cache = cacheWithMemoryClass(0);
        LauncherAppEntry app = entry("a");
        Drawable before = cache.icon(app, 48);

        cache.invalidateAll();

        assertEquals(0, cache.sizeBytes());
        assertNotSame("the treatment may have changed; nothing is reused across an invalidation",
            before, cache.icon(app, 48));
    }

    @Test
    public void oversubscribingTheBudget_evictsTheLeastRecentlyUsedIcons() {
        DockIconCache cache = cacheWithMemoryClass(0);
        int budget = cache.budgetBytes();
        // 512x512 ARGB_8888, charged twice for the retained clean artwork = 2MB per entry.
        int perEntry = 512 * 512 * 4 * 2;
        int entries = (budget / perEntry) + 4;

        Drawable oldest = cache.icon(entry("app0"), 512);
        Drawable newest = null;
        for (int i = 1; i < entries; i++) {
            newest = cache.icon(entry("app" + i), 512);
        }

        assertTrue("the cache must stay inside its byte budget", cache.sizeBytes() <= budget);
        assertTrue("something must actually have been evicted", cache.evictionCount() > 0);
        assertNotSame("the oldest entry is the one that goes",
            oldest, cache.icon(entry("app0"), 512));
        assertSame("the newest entry survives", newest, cache.icon(entry("app" + (entries - 1)), 512));
    }

    // ------------------------------------------------------------------ normalization

    @Test
    public void normalize_leavesArtworkAloneWhenThereIsNothingToRenderInto() {
        DockIconCache cache = cacheWithMemoryClass(0);
        Drawable src = new ColorDrawable(0xFF112233);

        assertNull(cache.normalize(null, 48, true, false));
        assertSame(src, cache.normalize(src, 0, true, false));
        assertSame(src, cache.normalize(src, -1, true, false));
    }

    @Test
    public void normalize_rendersSquareArtworkAtTheRequestedSizeAndKeepsTheCleanCopy() {
        DockIconCache cache = cacheWithMemoryClass(0);

        RenderedIconDrawable rendered =
            (RenderedIconDrawable) cache.normalize(new ColorDrawable(0xFF112233), 96, true, false);

        assertEquals(96, rendered.getBitmap().getWidth());
        assertEquals(96, rendered.getBitmap().getHeight());
        // The contour source is the pre-shadow artwork, at the same dimensions as the display.
        assertEquals(96, rendered.cleanArtwork.getWidth());
        assertEquals(96, rendered.cleanArtwork.getHeight());
        assertNotSame(rendered.getBitmap(), rendered.cleanArtwork);
    }

    /** Rendering must not leave the caller's drawable resized: it is the live catalogue icon. */
    @Test
    public void normalize_restoresTheSourceBounds() {
        DockIconCache cache = cacheWithMemoryClass(0);
        Drawable src = new ColorDrawable(0xFF112233);
        src.setBounds(3, 5, 7, 11);

        cache.normalize(src, 64, true, false);

        assertEquals(3, src.getBounds().left);
        assertEquals(5, src.getBounds().top);
        assertEquals(7, src.getBounds().right);
        assertEquals(11, src.getBounds().bottom);
    }

    /** Clone-profile artwork gets a badge, so it must not collide with the un-badged key. */
    @Test
    public void cloneProfileAndIconPackArtwork_getTheirOwnCacheEntries() {
        DockIconCache cache = cacheWithMemoryClass(0);
        AppRef plain = new AppRef("com.example.a", "Main");
        AppRef cloned = new AppRef("com.example.a", "Main", -1, 7L, true, "clone");

        Drawable plainIcon = cache.icon(
            new LauncherAppEntry(plain, "a", new ColorDrawable(0xFF00FF00)), 48);
        Drawable clonedIcon = cache.icon(
            new LauncherAppEntry(cloned, "a", new ColorDrawable(0xFF00FF00)), 48);
        Drawable packIcon = cache.icon(
            new LauncherAppEntry(plain, "a", new ColorDrawable(0xFF00FF00), true), 48);

        assertNotSame(plainIcon, clonedIcon);
        assertNotSame(plainIcon, packIcon);
    }
}
