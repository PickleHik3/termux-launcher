package com.termux.app.launcher.folder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class DrawerFolderChoicesTest {
    private static AppRef ref(String pkg) {
        return new AppRef(pkg, pkg + ".Main");
    }

    private static PinnedFolderItem folder(String id, String... pkgs) {
        PinnedFolderItem folder = new PinnedFolderItem(id, id);
        for (String pkg : pkgs) folder.apps.add(new PinnedAppItem(ref(pkg)));
        return folder;
    }

    @Test public void offersEveryFolderTheAppIsNotIn() {
        PinnedFolderItem a = folder("a", "x", "y");
        PinnedFolderItem b = folder("b", "app", "y");
        PinnedFolderItem c = folder("c", "z", "y");
        assertEquals(Arrays.asList(a, c),
            DrawerFolderChoices.offered(Arrays.asList(a, b, c), ref("app")));
    }

    @Test public void skipsAFullFolder() {
        PinnedFolderItem full = new PinnedFolderItem("full", "full");
        for (int i = 0; i < PinnedFolderItem.MAX_APPS; i++) {
            full.apps.add(new PinnedAppItem(ref("p" + i)));
        }
        assertTrue(DrawerFolderChoices.offered(Collections.singletonList(full), ref("app")).isEmpty());
    }

    @Test public void noFoldersOffersNothing() {
        assertTrue(DrawerFolderChoices.offered(Collections.<PinnedFolderItem>emptyList(),
            ref("app")).isEmpty());
    }

    private static LauncherAppEntry entry(String pkg) {
        return new LauncherAppEntry(ref(pkg), pkg, null);
    }

    @Test public void secondAppsSkipsTheAppAndFolderMembers() {
        java.util.List<LauncherAppEntry> all =
            Arrays.asList(entry("app"), entry("a"), entry("b"), entry("c"));
        PinnedFolderItem f = folder("f", "b", "z");
        assertEquals(Arrays.asList(all.get(1), all.get(3)),
            DrawerFolderChoices.secondApps(all, Collections.singletonList(f), ref("app")));
    }

    @Test public void rowShowsWithNoFoldersWhenAnotherAppIsFree() {
        assertTrue(DrawerFolderChoices.showsAddToFolder(Arrays.asList(entry("app"), entry("a")),
            Collections.<PinnedFolderItem>emptyList(), ref("app")));
    }

    @Test public void rowHidesWhenNothingCanTakeTheApp() {
        assertFalse(DrawerFolderChoices.showsAddToFolder(Collections.singletonList(entry("app")),
            Collections.<PinnedFolderItem>emptyList(), ref("app")));
        // The only other app is already in a folder the app is also in: nothing to offer.
        assertFalse(DrawerFolderChoices.showsAddToFolder(Arrays.asList(entry("app"), entry("a")),
            Collections.singletonList(folder("f", "app", "a")), ref("app")));
    }
}
