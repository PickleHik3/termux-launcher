package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.termux.app.wall.WallPages.Page;
import com.termux.app.wall.WallPages.PageKind;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** The page list store: saving, loading, announcing, and removal firing once. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class WallPagesStoreTest {

    private static final class Recorder implements WallPagesStore.Listener {
        int changed;
        final List<Page> removed = new ArrayList<>();
        @Override public void onChanged(@NonNull WallPages pages) { changed++; }
        @Override public void onRemoved(@NonNull Page page) { removed.add(page); }
    }

    private static WallPages rich() {
        // Home 1, Home 2 (pending), Display bound "gimp" named "Art", Terminal; one detached Home.
        WallPages p = WallPagesMigration.seed("workspace", 1, "home", 3);
        p = p.add(PageKind.DISPLAY, 1, "gimp").pages;
        p = p.rename(p.pages().get(1).id, "Art").pages;
        p = p.remove(2).pages;
        List<Page> detached = new ArrayList<>();
        detached.add(new Page(40, PageKind.HOME, "Old", null));
        return new WallPages(p.allPages(), p.pendingIds(), detached, p.homeId(), p.nextId(), true);
    }

    @Test
    public void emptyStorageGivesTheEmptyValueAndNotSeeded() {
        WallPagesStore store = new WallPagesStore(new WallPagesStore.MemoryStorage());
        assertFalse(store.wasSeeded());
        assertEquals(WallPages.EMPTY, store.current());
    }

    @Test
    public void roundTripThroughMemoryStorageKeepsEverything() {
        WallPagesStore.MemoryStorage storage = new WallPagesStore.MemoryStorage();
        WallPages value = rich();
        new WallPagesStore(storage).replace(value);
        WallPagesStore again = new WallPagesStore(storage);
        assertTrue(again.wasSeeded());
        assertEquals(value, again.current());
        assertEquals(1, again.current().detached().size());
        assertEquals("Old", again.current().detached().get(0).name);
        assertEquals(java.util.Arrays.asList(2), again.current().pendingIds());
        assertTrue(again.current().homeIsDisplayWindow());
        assertEquals("gimp", again.current().pages().get(1).binding);
    }

    @Test
    public void roundTripThroughPrefsStorage() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("wall_pages", Context.MODE_PRIVATE).edit().clear().commit();
        WallPages value = rich();
        new WallPagesStore(new WallPagesStore.PrefsStorage(context)).replace(value);
        assertNotNull(context.getSharedPreferences("wall_pages", Context.MODE_PRIVATE)
            .getString("state", null));
        WallPagesStore again = new WallPagesStore(new WallPagesStore.PrefsStorage(context));
        assertEquals(value, again.current());
    }

    @Test
    public void unreadableOrNewerJsonIsTreatedAsAbsent() {
        WallPagesStore.MemoryStorage storage = new WallPagesStore.MemoryStorage();
        storage.write("{not json");
        assertFalse(new WallPagesStore(storage).wasSeeded());
        storage.write("{\"version\":2,\"pages\":[],\"home\":0,\"next\":1}");
        assertFalse(new WallPagesStore(storage).wasSeeded());
    }

    @Test
    public void anAcceptedEditSavesAndAnnouncesOnceAndARefusalDoesNot() {
        WallPagesStore.MemoryStorage storage = new WallPagesStore.MemoryStorage();
        WallPagesStore store = new WallPagesStore(storage);
        store.replace(WallPagesMigration.seed("home", 0, "home", 2));
        Recorder rec = new Recorder();
        store.addListener(rec);
        store.edit(new WallPagesStore.Edit() {
            @NonNull @Override public WallPages.Result apply(@NonNull WallPages p) {
                return p.setHome(2);
            }
        });
        assertEquals(1, rec.changed);
        assertEquals(2, new WallPagesStore(storage).current().homeId());
        WallPages.Result refused = store.edit(new WallPagesStore.Edit() {
            @NonNull @Override public WallPages.Result apply(@NonNull WallPages p) {
                return p.add(PageKind.TERMINAL, WallPages.END, null);
            }
        });
        assertFalse(refused.ok());
        assertEquals(1, rec.changed);
        assertTrue(rec.removed.isEmpty());
    }

    @Test
    public void removedFiresOnlyOnCommitAndOnce() {
        WallPagesStore store = new WallPagesStore(new WallPagesStore.MemoryStorage());
        store.replace(WallPagesMigration.seed("home", 0, "home", 2));
        Recorder rec = new Recorder();
        store.addListener(rec);
        store.edit(new WallPagesStore.Edit() {
            @NonNull @Override public WallPages.Result apply(@NonNull WallPages p) {
                return p.remove(2);
            }
        });
        assertTrue(rec.removed.isEmpty());
        store.commitRemoval(2);
        assertEquals(1, rec.removed.size());
        assertEquals(2, rec.removed.get(0).id);
        store.commitRemoval(2);
        assertEquals(1, rec.removed.size());
    }

    @Test
    public void removeThenUndoNeverFiresRemoved() {
        WallPagesStore store = new WallPagesStore(new WallPagesStore.MemoryStorage());
        store.replace(WallPagesMigration.seed("home", 0, "home", 2));
        Recorder rec = new Recorder();
        store.addListener(rec);
        store.edit(new WallPagesStore.Edit() {
            @NonNull @Override public WallPages.Result apply(@NonNull WallPages p) {
                return p.remove(1);
            }
        });
        store.edit(new WallPagesStore.Edit() {
            @NonNull @Override public WallPages.Result apply(@NonNull WallPages p) {
                return p.undo(1);
            }
        });
        store.flushPending();
        assertTrue(rec.removed.isEmpty());
        assertEquals(3, store.current().pages().size());
    }

    @Test
    public void flushPendingCommitsEveryPendingPageOnce() {
        WallPagesStore store = new WallPagesStore(new WallPagesStore.MemoryStorage());
        store.replace(WallPagesMigration.seed("home", 0, "home", 3));
        Recorder rec = new Recorder();
        store.addListener(rec);
        for (final int id : new int[] {1, 2}) {
            store.edit(new WallPagesStore.Edit() {
                @NonNull @Override public WallPages.Result apply(@NonNull WallPages p) {
                    return p.remove(id);
                }
            });
        }
        store.flushPending();
        store.flushPending();
        assertEquals(2, rec.removed.size());
        assertTrue(store.current().pendingIds().isEmpty());
        assertEquals(2, store.current().allPages().size());
    }

    @Test
    public void aRemovedListenerStopsHearing() {
        WallPagesStore store = new WallPagesStore(new WallPagesStore.MemoryStorage());
        Recorder rec = new Recorder();
        store.addListener(rec);
        store.removeListener(rec);
        store.replace(WallPagesMigration.seed("home", 0, "home", 1));
        assertEquals(0, rec.changed);
    }
}
