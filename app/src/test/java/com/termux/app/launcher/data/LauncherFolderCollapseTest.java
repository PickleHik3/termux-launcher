package com.termux.app.launcher.data;

import static org.junit.Assert.*;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Test;

public class LauncherFolderCollapseTest {
    private static PinnedAppItem app(String id) { return new PinnedAppItem(new AppRef(id, "A")); }
    @Test public void zeroDeletesOneReplacesEveryRefAndTwoStays() {
        List<PinnedItem> dock = new ArrayList<>();
        LinkedHashMap<String, PinnedFolderItem> folders = new LinkedHashMap<>();
        PinnedFolderItem zero = new PinnedFolderItem("zero", "Z");
        PinnedFolderItem one = new PinnedFolderItem("one", "O"); one.apps.add(app("survivor"));
        PinnedFolderItem two = new PinnedFolderItem("two", "T"); two.apps.add(app("a")); two.apps.add(app("b"));
        folders.put(zero.id, zero); folders.put(one.id, one); folders.put(two.id, two);
        dock.add(zero); dock.add(one); dock.add(two); dock.add(one);
        LauncherFolderMutator.normalize(dock, folders);
        assertFalse(folders.containsKey("zero")); assertFalse(folders.containsKey("one"));
        assertTrue(folders.containsKey("two"));
        assertEquals(3, dock.size());
        assertEquals("survivor", ((PinnedAppItem) dock.get(0)).appRef.packageName);
        assertSame(two, dock.get(1));
        assertEquals("survivor", ((PinnedAppItem) dock.get(2)).appRef.packageName);
    }
}
