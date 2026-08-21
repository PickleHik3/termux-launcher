package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;

import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.data.LauncherConfigSnapshot;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class AppDrawerFolderCompositionTest {
    @Test public void supportedAllAppsSuppressMembersAtEarliestPositionOnly() {
        Store store = new Store();
        store.raw = "{\"schemaVersion\":5,\"items\":[],\"folders\":[{\"id\":\"f\",\"title\":\"Folder\",\"apps\":[{\"packageName\":\"a\",\"activityName\":\"A\"},{\"packageName\":\"c\",\"activityName\":\"A\"}]}],\"appIconOverrides\":[]}";
        LauncherConfigSnapshot snapshot = new LauncherConfigRepository(store).loadSnapshot();
        List<LauncherAppEntry> apps = Arrays.asList(entry("a"), entry("b"), entry("c"), entry("d"));
        for (AppDrawerViewType type : new AppDrawerViewType[]{AppDrawerViewType.VERTICAL,
                AppDrawerViewType.HORIZONTAL}) {
            List<AppDrawerItem> mixed = AppDrawerItemComposer.compose(apps, snapshot, true, type);
            assertEquals(3, mixed.size());
            assertEquals(AppDrawerItem.Kind.FOLDER, mixed.get(0).kind);
            assertEquals("b/A", mixed.get(1).stableId);
            assertEquals("d/A", mixed.get(2).stableId);
        }
        assertEquals(4, AppDrawerItemComposer.compose(apps, snapshot, true,
            AppDrawerViewType.CATEGORIES).size());
        assertEquals(4, AppDrawerItemComposer.compose(apps, snapshot, false,
            AppDrawerViewType.VERTICAL).size());
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
