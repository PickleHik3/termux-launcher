package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;

import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.data.LauncherConfigSnapshot;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedFolderItem;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** A folder dragged to a free spot in the drawer stays where it was dropped. */
public class AppDrawerFolderAnchorCompositionTest {

    @Test public void anchoredFolderSitsInFrontOfItsAnchorApp() {
        List<AppDrawerItem> mixed = composeWith("\"drawerAnchor\":\"d/A\",");
        // Members a and c are suppressed; the folder left its automatic spot at the head of the
        // list and now precedes d.
        assertEquals(3, mixed.size());
        assertEquals("b/A", mixed.get(0).stableId);
        assertEquals(AppDrawerItem.Kind.FOLDER, mixed.get(1).kind);
        assertEquals("d/A", mixed.get(2).stableId);
    }

    @Test public void endAnchorParksTheFolderAfterEveryApp() {
        List<AppDrawerItem> mixed = composeWith("\"drawerAnchor\":\"\",");
        assertEquals(3, mixed.size());
        assertEquals("b/A", mixed.get(0).stableId);
        assertEquals("d/A", mixed.get(1).stableId);
        assertEquals(AppDrawerItem.Kind.FOLDER, mixed.get(2).kind);
    }

    @Test public void anchorOnAnUninstalledAppFallsBackToAutomaticPlacement() {
        List<AppDrawerItem> mixed = composeWith("\"drawerAnchor\":\"gone/A\",");
        assertEquals(AppDrawerItem.Kind.FOLDER, mixed.get(0).kind);
    }

    @Test public void anchorSurvivesAWriteReadRound() {
        Store store = new Store();
        store.raw = json("\"drawerAnchor\":\"d/A\",");
        LauncherConfigRepository repository = new LauncherConfigRepository(store);
        LauncherConfigSnapshot before = repository.loadSnapshot();
        assertEquals(LauncherConfigRepository.MutationResult.APPLIED,
            repository.setFolderDrawerAnchor(before.revision, "f", PinnedFolderItem.DRAWER_ANCHOR_END));
        PinnedFolderItem folder = new LauncherConfigRepository(store).loadSnapshot().folder("f");
        assertNotNull(folder);
        assertEquals(PinnedFolderItem.DRAWER_ANCHOR_END, folder.drawerAnchor);
    }

    @Test public void automaticPlacementHasNoAnchorPersisted() {
        Store store = new Store();
        store.raw = json("");
        PinnedFolderItem folder = new LauncherConfigRepository(store).loadSnapshot().folder("f");
        assertNotNull(folder);
        assertNull(folder.drawerAnchor);
    }

    private static List<AppDrawerItem> composeWith(String anchorField) {
        Store store = new Store();
        store.raw = json(anchorField);
        LauncherConfigSnapshot snapshot = new LauncherConfigRepository(store).loadSnapshot();
        List<LauncherAppEntry> apps = Arrays.asList(entry("a"), entry("b"), entry("c"), entry("d"));
        return AppDrawerItemComposer.compose(apps, snapshot, true, AppDrawerViewType.VERTICAL);
    }

    private static String json(String anchorField) {
        return "{\"schemaVersion\":5,\"items\":[],\"folders\":[{\"id\":\"f\",\"title\":\"Folder\","
            + anchorField
            + "\"apps\":[{\"packageName\":\"a\",\"activityName\":\"A\"},"
            + "{\"packageName\":\"c\",\"activityName\":\"A\"}]}],\"appIconOverrides\":[]}";
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
