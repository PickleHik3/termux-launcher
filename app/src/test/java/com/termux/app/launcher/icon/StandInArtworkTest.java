package com.termux.app.launcher.icon;

import android.app.Application;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The stand-in drawn while an icon pack is still being parsed for the main thread: shown, and
 * kept by neither the artwork store nor the rendered-icon cache, so once the pack lands the next
 * read draws the pack's artwork rather than the stock icon held under the pack's identity.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class StandInArtworkTest {

    private static final String PACK_IDENTITY = "com.donnnno.arcticons:1570/";

    private Resources resources;
    private Drawable stock;
    private Drawable packArt;
    /** Whether the pack has landed; until then the loader answers a stand-in. */
    private boolean landed;
    private int loads;

    @Before
    public void setUp() {
        resources = RuntimeEnvironment.getApplication().getResources();
        stock = bitmap();
        packArt = bitmap();
        landed = false;
        loads = 0;
    }

    private Drawable bitmap() {
        return new BitmapDrawable(resources, Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888));
    }

    private LauncherIconStore store() {
        LauncherIconStore store = new LauncherIconStore(resources, 256, new LauncherIconStore.ArtworkLoader() {
            @Override
            public Drawable load(@NonNull AppRef ref) {
                return loadArtwork(ref).drawable;
            }

            @NonNull
            @Override
            public LauncherIconStore.Artwork loadArtwork(@NonNull AppRef ref) {
                loads++;
                return landed ? LauncherIconStore.Artwork.kept(packArt)
                    : LauncherIconStore.Artwork.standIn(stock);
            }
        });
        store.setIconPackIdentity(PACK_IDENTITY);
        return store;
    }

    private static LauncherAppEntry app() {
        return new LauncherAppEntry(new AppRef("com.google.android.calendar", "Main"), "Calendar", null);
    }

    @Test
    public void theStoreShowsAStandInButKeepsNothingUntilThePackLands() {
        LauncherIconStore store = store();

        LauncherIconStore.Artwork first = store.artworkDetailed(app());
        assertTrue(first.provisional);
        assertNotNull(first.drawable);
        assertEquals(0, store.sizeBytes());

        landed = true;
        LauncherIconStore.Artwork after = store.artworkDetailed(app());
        assertFalse(after.provisional);
        assertSame(after.drawable, store.artwork(app()));
        assertEquals(2, loads);
        assertTrue(store.sizeBytes() > 0);
    }

    @Test
    public void theRenderCacheDrawsAStandInButKeepsNoRenderOfIt() {
        LauncherIconStore store = store();
        DockIconCache cache = new DockIconCache(resources, 256, () -> new ColorDrawable(0xFF334455),
            new DockIconCache.ArtworkSource() {
                @Override
                public Drawable artwork(@NonNull LauncherAppEntry entry) {
                    return store.artwork(entry);
                }

                @NonNull
                @Override
                public LauncherIconStore.Artwork artworkDetailed(@NonNull LauncherAppEntry entry) {
                    return store.artworkDetailed(entry);
                }
            });
        cache.setIconPackIdentity(PACK_IDENTITY);

        assertNotNull(cache.icon(app(), 48));
        assertNull(cache.peek(app(), 48));
        assertEquals(0, cache.sizeBytes());

        landed = true;
        Drawable rendered = cache.icon(app(), 48);
        assertSame(rendered, cache.peek(app(), 48));
    }
}
