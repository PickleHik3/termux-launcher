package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;

import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.data.LauncherConfigSnapshot;
import com.termux.app.launcher.folder.DrawerFolderChoices;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The drawer shows only its own folders: a folder on the dock is not one of them. */
public class AppDrawerDockFolderTest {
    private static final List<LauncherAppEntry> APPS =
        Arrays.asList(entry("a"), entry("b"), entry("c"), entry("d"));

    @Test public void dockFolderYieldsNoTileAndItsMembersAreListed() {
        LauncherConfigSnapshot snapshot = load(dockFolder("f", "\"\"", "a", "c"));
        assertTrue(snapshot.isOnDock("f"));
        List<AppDrawerItem> items = compose(snapshot);
        assertEquals(Arrays.asList("a/A", "b/A", "c/A", "d/A"), ids(items));
        for (AppDrawerItem item : items) assertEquals(AppDrawerItem.Kind.APP, item.kind);
    }

    @Test public void drawerOnlyFolderBehavesAsBefore() {
        LauncherConfigSnapshot snapshot = load(drawerFolder("f", "\"d/A\"", "a", "c"));
        assertFalse(snapshot.isOnDock("f"));
        List<AppDrawerItem> items = compose(snapshot);
        assertEquals(3, items.size());
        assertEquals("b/A", items.get(0).stableId);
        assertEquals(AppDrawerItem.Kind.FOLDER, items.get(1).kind);
        assertEquals("d/A", items.get(2).stableId);
    }

    @Test public void drawerFolderStillSuppressesAnAppAlsoInADockFolder() {
        LauncherConfigSnapshot snapshot = load(twoFolders());
        List<AppDrawerItem> items = compose(snapshot);
        // a and c are in the drawer folder; b (dock folder only) and d are listed.
        assertEquals(3, items.size());
        assertEquals(AppDrawerItem.Kind.FOLDER, items.get(0).kind);
        assertEquals("own", items.get(0).stableId);
        assertEquals("b/A", items.get(1).stableId);
        assertEquals("d/A", items.get(2).stableId);
    }

    @Test public void folderLeavesTheDrawerOnTheDockAndReturnsAtItsAnchorOffIt() {
        Store store = new Store();
        store.raw = drawerFolder("f", "\"d/A\"", "a", "c");
        LauncherConfigRepository repository = new LauncherConfigRepository(store);
        assertEquals(3, compose(repository.loadSnapshot()).size());

        List<PinnedItem> dock = new ArrayList<>(repository.loadSnapshot().dockItems);
        dock.add(repository.loadSnapshot().folder("f"));
        repository.savePinnedItems(dock);
        LauncherConfigSnapshot onDock = repository.loadSnapshot();
        assertTrue(onDock.isOnDock("f"));
        assertEquals(Arrays.asList("a/A", "b/A", "c/A", "d/A"), ids(compose(onDock)));
        assertEquals("d/A", onDock.folder("f").drawerAnchor);

        assertEquals(LauncherConfigRepository.MutationResult.APPLIED,
            repository.unpinFolder(onDock.revision, "f"));
        LauncherConfigSnapshot off = repository.loadSnapshot();
        assertFalse(off.isOnDock("f"));
        List<AppDrawerItem> items = compose(off);
        assertEquals(3, items.size());
        assertEquals(AppDrawerItem.Kind.FOLDER, items.get(1).kind);
        assertEquals("d/A", items.get(2).stableId);
    }

    @Test public void drawerChoicesExcludeDockFolders() {
        LauncherConfigSnapshot snapshot = load(twoFolders());
        List<PinnedFolderItem> offered = DrawerFolderChoices.offered(snapshot.drawerFolders(),
            new AppRef("app", "A"));
        assertEquals(1, offered.size());
        assertEquals("own", offered.get(0).id);
        // b is only in the dock folder, so it is a free drawer app that can start a new folder.
        assertEquals(2, DrawerFolderChoices.secondApps(
            Arrays.asList(entry("app"), entry("b"), entry("d"), entry("c")),
            snapshot.drawerFolders(), new AppRef("app", "A")).size());
    }

    private static String twoFolders() {
        return "{\"schemaVersion\":5,\"items\":[{\"type\":\"folderRef\",\"folderId\":\"dock\"}],"
            + "\"folders\":[" + folderJson("dock", null, "a", "b") + ","
            + folderJson("own", null, "a", "c") + "],\"appIconOverrides\":[]}";
    }

    private static LauncherConfigSnapshot load(String raw) {
        Store store = new Store();
        store.raw = raw;
        return new LauncherConfigRepository(store).loadSnapshot();
    }

    private static List<AppDrawerItem> compose(LauncherConfigSnapshot snapshot) {
        return AppDrawerItemComposer.compose(APPS, snapshot, true, AppDrawerViewType.VERTICAL);
    }

    private static List<String> ids(List<AppDrawerItem> items) {
        List<String> ids = new ArrayList<>();
        for (AppDrawerItem item : items) ids.add(item.stableId);
        return ids;
    }

    private static String folderJson(String id, String anchor, String... pkgs) {
        StringBuilder apps = new StringBuilder();
        for (String pkg : pkgs) {
            if (apps.length() > 0) apps.append(',');
            apps.append("{\"packageName\":\"").append(pkg).append("\",\"activityName\":\"A\"}");
        }
        return "{\"id\":\"" + id + "\",\"title\":\"Folder\","
            + (anchor == null ? "" : "\"drawerAnchor\":" + anchor + ",")
            + "\"apps\":[" + apps + "]}";
    }

    private static String dockFolder(String id, String anchor, String... pkgs) {
        return "{\"schemaVersion\":5,\"items\":[{\"type\":\"folderRef\",\"folderId\":\"" + id
            + "\"}],\"folders\":[" + folderJson(id, anchor, pkgs) + "],\"appIconOverrides\":[]}";
    }

    private static String drawerFolder(String id, String anchor, String... pkgs) {
        return "{\"schemaVersion\":5,\"items\":[],\"folders\":[" + folderJson(id, anchor, pkgs)
            + "],\"appIconOverrides\":[]}";
    }

    private static LauncherAppEntry entry(String id) {
        return new LauncherAppEntry(new AppRef(id, "A"), id, null);
    }

    private static final class Store implements LauncherConfigRepository.PreferencesStore {
        String raw;
        @Override public String getPinnedItemsV2() { return raw; }
        @Override public int getPinnedItemsSchemaVersion() { return 0; }
        @Override public boolean commitPinnedItems(String value, int version) {
            raw = value; return true;
        }
        @Override public String getLegacyDefaultButtons() { return ""; }
    }
}
