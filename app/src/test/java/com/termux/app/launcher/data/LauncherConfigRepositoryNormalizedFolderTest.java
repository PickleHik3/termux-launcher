package com.termux.app.launcher.data;

import static org.junit.Assert.*;

import com.termux.app.launcher.model.PinnedFolderItem;
import org.junit.Test;

public class LauncherConfigRepositoryNormalizedFolderTest {
    private static final String V5 = "{\"schemaVersion\":5,\"items\":[{\"type\":\"folderRef\",\"folderId\":\"pinned\"}],\"folders\":["
        + "{\"id\":\"pinned\",\"title\":\"One\",\"apps\":[{\"packageName\":\"a\"},{\"packageName\":\"b\"}]},"
        + "{\"id\":\"drawer\",\"title\":\"Two\",\"apps\":[{\"packageName\":\"c\"},{\"packageName\":\"d\"}]}],\"appIconOverrides\":[]}";

    @Test public void dockAndDrawerResolveSameEntityAndUnpinRetainsIt() {
        TestLauncherStore store = new TestLauncherStore(); store.raw = V5;
        LauncherConfigRepository repo = new LauncherConfigRepository(store);
        LauncherConfigSnapshot first = repo.loadSnapshot();
        assertSame(first.folder("pinned"), first.dockItems.get(0));
        assertNotNull(first.folder("drawer"));
        assertEquals(LauncherConfigRepository.MutationResult.APPLIED,
            repo.renameFolder(first.revision, "pinned", "Renamed"));
        LauncherConfigSnapshot renamed = repo.loadSnapshot();
        assertEquals("Renamed", ((PinnedFolderItem) renamed.dockItems.get(0)).title);
        assertSame(renamed.folder("pinned"), renamed.dockItems.get(0));
        assertEquals(LauncherConfigRepository.MutationResult.APPLIED,
            repo.unpinFolder(renamed.revision, "pinned"));
        LauncherConfigSnapshot unpinned = repo.loadSnapshot();
        assertTrue(unpinned.dockItems.isEmpty());
        assertNotNull(unpinned.folder("pinned"));
    }
}
