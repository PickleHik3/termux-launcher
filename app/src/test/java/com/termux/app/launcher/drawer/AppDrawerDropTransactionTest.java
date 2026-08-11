package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;

import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.data.LauncherConfigSnapshot;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class AppDrawerDropTransactionTest {
    @Test public void createAndAppendEachWriteAndPublishExactlyOnceWithoutDockPin() {
        Store store = new Store();
        LauncherConfigRepository repo = new LauncherConfigRepository(store);
        LauncherConfigSnapshot empty = repo.loadSnapshot();
        store.writes = 0;
        AtomicInteger publications = new AtomicInteger();
        repo.addListener(snapshot -> publications.incrementAndGet());
        assertEquals(LauncherConfigRepository.MutationResult.APPLIED,
            repo.createFolder(empty.revision, "f", app("target"), app("source")));
        assertEquals(1, store.writes);
        assertEquals(1, publications.get());
        LauncherConfigSnapshot created = repo.loadSnapshot();
        assertTrue(created.dockItems.isEmpty());
        assertEquals("target", created.folder("f").apps.get(0).appRef.packageName);
        assertEquals("source", created.folder("f").apps.get(1).appRef.packageName);
        assertEquals(LauncherConfigRepository.MutationResult.APPLIED,
            repo.addAppToFolder(created.revision, "f", app("third")));
        assertEquals(2, store.writes);
        assertEquals(2, publications.get());
        LauncherConfigSnapshot appended = repo.loadSnapshot();
        assertEquals(LauncherConfigRepository.MutationResult.NO_OP,
            repo.addAppToFolder(appended.revision, "f", app("third")));
        assertEquals(2, store.writes);
    }
    private static PinnedAppItem app(String id) { return new PinnedAppItem(new AppRef(id, "A")); }
    private static final class Store implements LauncherConfigRepository.PreferencesStore {
        String raw = ""; int writes;
        @Override public String getPinnedItemsV2() { return raw; }
        @Override public void setPinnedItemsV2(String value) { raw = value; writes++; }
        @Override public void setPinnedItemsSchemaVersion(int version) {}
        @Override public String getLegacyDefaultButtons() { return ""; }
    }
}
