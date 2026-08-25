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

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Raw artwork ownership in isolation: no views, no package manager. Robolectric is only here
 * because shrinking artwork draws a real {@link Bitmap}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherIconStoreTest {

    private Resources resources;
    private List<String> loads;

    @Before
    public void setUp() {
        resources = RuntimeEnvironment.getApplication().getResources();
        loads = new ArrayList<>();
    }

    private static AppRef ref(String id) {
        return new AppRef("com.example." + id, "Main");
    }

    private static LauncherAppEntry entry(String id, Drawable bespokeIcon) {
        return new LauncherAppEntry(ref(id), id, bespokeIcon);
    }

    /** A bitmap-backed drawable of a given square size, which is what the framework hands back. */
    private Drawable artwork(int sizePx) {
        return new BitmapDrawable(resources,
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888));
    }

    private LauncherIconStore store(int memoryClassMb, Drawable loaded) {
        return new LauncherIconStore(resources, memoryClassMb, appRef -> {
            loads.add(appRef.stableId());
            return loaded;
        });
    }

    // ------------------------------------------------------------------ ownership

    /**
     * The whole point: an entry carries identity, and the pixels are fetched. An entry that does
     * carry artwork of its own — a folder, a per-app override, a test stub — is bespoke and there
     * is nothing to load it from, so it is answered directly and never looked up.
     */
    @Test
    public void bespokeArtworkOnTheEntry_isUsedWithoutAskingTheLoader() {
        Drawable bespoke = new ColorDrawable(0xFF00FF00);
        LauncherIconStore store = store(256, artwork(64));
        assertSame(bespoke, store.artwork(entry("folder", bespoke)));
        assertTrue("a bespoke icon must not reach the loader", loads.isEmpty());
    }

    @Test
    public void artworkForACatalogueEntry_isLoadedOnceAndThenHeld() {
        LauncherIconStore store = store(256, artwork(64));
        LauncherAppEntry entry = entry("alpha", null);
        Drawable first = store.artwork(entry);
        assertNotNull(first);
        assertSame("the second read must not load again", first, store.artwork(entry));
        assertEquals(1, loads.size());
    }

    @Test
    public void primedArtwork_isServedWithoutALoad() {
        LauncherIconStore store = store(256, artwork(64));
        Drawable primed = artwork(64);
        store.prime(ref("alpha"), primed);
        assertSame(primed, store.artwork(entry("alpha", null)));
        assertTrue("the catalogue load already resolved this one", loads.isEmpty());
    }

    @Test
    public void anAppWithNoArtwork_answersNullRatherThanASubstitute() {
        LauncherIconStore store = store(256, null);
        assertNull(store.artwork(entry("ghost", null)));
        assertNull(store.artwork((LauncherAppEntry) null));
    }

    @Test
    public void invalidateAll_dropsEverythingHeld() {
        LauncherIconStore store = store(256, artwork(64));
        store.artwork(entry("alpha", null));
        assertTrue(store.sizeBytes() > 0);
        store.invalidateAll();
        assertEquals(0, store.sizeBytes());
        store.artwork(entry("alpha", null));
        assertEquals("the next read reloads at the current treatment", 2, loads.size());
    }

    // ------------------------------------------------------------------ rasterising down

    /**
     * The framework hands back the system-density rasterisation — 284x284 on the device this was
     * measured on — where the drawer grid caps its cells at 48dp and the dock rail asks for 38dp.
     * Keeping those pixels is what made one icon per installed app cost 323 KB.
     */
    @Test
    public void oversizedBitmapArtwork_isRasterisedDownToTheRetainedSize() {
        LauncherIconStore store = store(256, artwork(284));
        Drawable held = store.artwork(entry("alpha", null));
        assertEquals(LauncherIconStore.MAX_RETAINED_PX, held.getIntrinsicWidth());
        assertEquals(LauncherIconStore.MAX_RETAINED_PX, held.getIntrinsicHeight());
    }

    @Test
    public void artworkAlreadySmallEnough_isKeptAsItIs() {
        Drawable small = artwork(96);
        LauncherIconStore store = store(256, small);
        assertSame(small, store.artwork(entry("alpha", null)));
    }

    /**
     * A vector costs a few kilobytes and scales for free, so flattening one to a bitmap would spend
     * memory rather than save it. Only a drawable already made of pixels is worth redrawing.
     */
    @Test
    public void oversizedArtworkThatIsNotMadeOfPixels_isLeftAlone() {
        Drawable vectorLike = new ColorDrawable(0xFF112233) {
            @Override public int getIntrinsicWidth() { return 512; }
            @Override public int getIntrinsicHeight() { return 512; }
        };
        LauncherIconStore store = store(256, vectorLike);
        assertSame(vectorLike, store.artwork(entry("alpha", null)));
    }

    @Test
    public void aNonSquareOversizedIcon_keepsItsProportions() {
        Drawable wide = new BitmapDrawable(resources,
            Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888));
        LauncherIconStore store = store(256, wide);
        Drawable held = store.artwork(entry("alpha", null));
        assertEquals(LauncherIconStore.MAX_RETAINED_PX, held.getIntrinsicWidth());
        assertEquals(LauncherIconStore.MAX_RETAINED_PX / 2, held.getIntrinsicHeight());
    }

    // ------------------------------------------------------------------ budgeting

    /**
     * Bounded in bytes, because this is the cost that used to be unbounded: one icon per installed
     * app, so a 300-app device paid for 300 of them whether or not any was on screen.
     */
    @Test
    public void theBudget_isOneSixteenthOfTheHeapClampedTo4And16Megabytes() {
        assertEquals(4 * 1024 * 1024, LauncherIconStore.resolveBudgetBytes(0));
        assertEquals(4 * 1024 * 1024, LauncherIconStore.resolveBudgetBytes(48));
        // 64MB / 16 = 4MB, exactly the floor.
        assertEquals(4 * 1024 * 1024, LauncherIconStore.resolveBudgetBytes(64));
        assertEquals(8 * 1024 * 1024, LauncherIconStore.resolveBudgetBytes(128));
        assertEquals(16 * 1024 * 1024, LauncherIconStore.resolveBudgetBytes(512));
        assertEquals(4 * 1024 * 1024, LauncherIconStore.resolveBudgetBytes(-64));
    }

    @Test
    public void theOldestArtworkIsEvictedRatherThanGrowingWithoutBound() {
        // The floor budget with icons deliberately larger than a fraction of it.
        LauncherIconStore store = new LauncherIconStore(resources, 0,
            appRef -> {
                loads.add(appRef.stableId());
                return artwork(LauncherIconStore.MAX_RETAINED_PX);
            });
        int perIcon = LauncherIconStore.MAX_RETAINED_PX * LauncherIconStore.MAX_RETAINED_PX * 4;
        int fits = store.budgetBytes() / perIcon;
        for (int i = 0; i < fits + 4; i++) store.artwork(entry("app" + i, null));
        assertTrue("the budget is never exceeded", store.sizeBytes() <= store.budgetBytes());

        // The first one is gone, so reading it again is a fresh load rather than a hit.
        int loadsBefore = loads.size();
        store.artwork(entry("app0", null));
        assertEquals(loadsBefore + 1, loads.size());
    }
}
