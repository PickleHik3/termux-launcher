package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class LauncherFolderMoveTransactionTest {
    @Test public void rejectedDuplicateOrCapacityMoveKeepsTopLevelSourcePin() {
        PinnedAppItem duplicate = app("duplicate");
        PinnedFolderItem folder = new PinnedFolderItem("f", "Folder");
        folder.apps.add(app("duplicate"));
        List<PinnedItem> dock = new ArrayList<>();
        dock.add(duplicate);
        dock.add(folder);

        assertEquals(LauncherFolderMutator.AppendResult.DUPLICATE,
            LauncherFolderMutator.moveTopLevelAppIntoFolder(dock, 0, folder, duplicate));
        assertEquals(2, dock.size());
        assertSame(duplicate, dock.get(0));

        folder.apps.clear();
        for (int i = 0; i < PinnedFolderItem.MAX_APPS; i++) folder.apps.add(app("p" + i));
        assertEquals(LauncherFolderMutator.AppendResult.CAPACITY,
            LauncherFolderMutator.moveTopLevelAppIntoFolder(dock, 0, folder, duplicate));
        assertEquals(2, dock.size());
        assertSame(duplicate, dock.get(0));
    }

    private static PinnedAppItem app(String id) {
        return new PinnedAppItem(new AppRef(id, "Main"));
    }
}
