package com.termux.app.launcher.data;

import static org.junit.Assert.*;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;
import org.junit.Test;

public class LauncherFolderMutatorTest {
    private static PinnedAppItem app(int i) { return new PinnedAppItem(new AppRef("p" + i, "A")); }

    @Test public void targetFirstAppendCapacityDuplicateAndStaleContracts() {
        PinnedFolderItem folder = LauncherFolderMutator.create("f", app(1), app(2));
        assertEquals("p1", folder.apps.get(0).appRef.packageName);
        assertEquals("p2", folder.apps.get(1).appRef.packageName);
        assertFalse(LauncherFolderMutator.append(folder, app(2)));
        for (int i = 3; i <= 36; i++) assertTrue(LauncherFolderMutator.append(folder, app(i)));
        assertEquals(36, folder.apps.size());
        assertFalse(LauncherFolderMutator.append(folder, app(37)));

        TestLauncherStore store = new TestLauncherStore();
        LauncherConfigRepository repo = new LauncherConfigRepository(store);
        LauncherConfigSnapshot empty = repo.loadSnapshot();
        assertEquals(LauncherConfigRepository.MutationResult.STALE,
            repo.createFolder(empty.revision - 1, "x", app(1), app(2)));
        assertEquals(LauncherConfigRepository.MutationResult.MISSING,
            repo.addAppToFolder(empty.revision, "missing", app(3)));
    }
}
