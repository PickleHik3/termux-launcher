package com.termux.app.launcher;

import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedIconOverride;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LauncherConfigRepositoryTest {
    private LauncherConfigRepository repository;
    private FakePreferencesStore preferences;

    private static final class FakePreferencesStore implements LauncherConfigRepository.PreferencesStore {
        private String pinnedItemsV2 = "";
        private int schemaVersion = 0;
        private String legacyDefaultButtons = "";

        @Override
        public String getPinnedItemsV2() {
            return pinnedItemsV2;
        }

        @Override
        public int getPinnedItemsSchemaVersion() {
            return schemaVersion;
        }

        @Override
        public boolean commitPinnedItems(String value, int version) {
            pinnedItemsV2 = value;
            schemaVersion = version;
            return true;
        }

        @Override
        public String getLegacyDefaultButtons() {
            return legacyDefaultButtons;
        }
    }

    @Before
    public void setUp() {
        preferences = new FakePreferencesStore();
        repository = new LauncherConfigRepository(preferences);
    }

    @Test
    public void pinnedAppRoundTrip_preservesOrder() {
        List<PinnedItem> items = new ArrayList<>();
        items.add(new PinnedAppItem(new AppRef("com.example.one", "A")));
        items.add(new PinnedAppItem(new AppRef("com.example.two", "B")));
        items.add(new PinnedAppItem(new AppRef("com.example.three", "C")));

        repository.savePinnedItems(items);
        List<PinnedItem> loaded = repository.loadPinnedItems();

        assertEquals(3, loaded.size());
        assertEquals(PinnedItem.TYPE_APP, loaded.get(0).getType());
        assertEquals("com.example.one", ((PinnedAppItem) loaded.get(0)).appRef.packageName);
        assertEquals("com.example.two", ((PinnedAppItem) loaded.get(1)).appRef.packageName);
        assertEquals("com.example.three", ((PinnedAppItem) loaded.get(2)).appRef.packageName);
    }

    @Test
    public void pinnedAppRoundTrip_preservesClonedProfileIdentity() {
        List<PinnedItem> items = new ArrayList<>();
        items.add(new PinnedAppItem(new AppRef("com.example.chat", "Main",
            10, 42L, true, "Clone 10")));

        repository.savePinnedItems(items);
        List<PinnedItem> loaded = repository.loadPinnedItems();

        assertEquals(1, loaded.size());
        PinnedAppItem item = (PinnedAppItem) loaded.get(0);
        assertEquals("com.example.chat", item.appRef.packageName);
        assertEquals("Main", item.appRef.activityName);
        assertEquals(10, item.appRef.userId);
        assertEquals(42L, item.appRef.userSerialNumber);
        assertTrue(item.appRef.clonedProfile);
        assertEquals("com.example.chat/Main#user=10", item.appRef.stableId());
    }

    @Test
    public void folderRoundTrip_preservesGridAndTint() {
        List<PinnedItem> items = new ArrayList<>();
        PinnedFolderItem folder = new PinnedFolderItem("folder-1", "Media");
        folder.rows = 2;
        folder.cols = 3;
        folder.tintOverrideEnabled = true;
        folder.tintColor = 0xFF1A2B3C;
        folder.apps.add(new PinnedAppItem(new AppRef("com.example.music", "Main")));
        folder.apps.add(new PinnedAppItem(new AppRef("com.example.podcast", "Main")));
        items.add(folder);

        repository.savePinnedItems(items);
        List<PinnedItem> loaded = repository.loadPinnedItems();

        assertEquals(1, loaded.size());
        assertEquals(PinnedItem.TYPE_FOLDER, loaded.get(0).getType());
        PinnedFolderItem loadedFolder = (PinnedFolderItem) loaded.get(0);
        assertEquals(2, loadedFolder.rows);
        assertEquals(3, loadedFolder.cols);
        assertTrue(loadedFolder.tintOverrideEnabled);
        assertEquals(0xFF1A2B3C, loadedFolder.tintColor);
        assertEquals(2, loadedFolder.apps.size());
    }

    @Test
    public void folderRoundTrip_preservesPackageOnlyRefs() {
        List<PinnedItem> items = new ArrayList<>();
        PinnedFolderItem folder = new PinnedFolderItem("folder-2", "Fallbacks");
        folder.apps.add(new PinnedAppItem(new AppRef("com.example.packageonly", "")));
        folder.apps.add(new PinnedAppItem(new AppRef("com.example.full", "Main")));
        items.add(folder);

        repository.savePinnedItems(items);
        List<PinnedItem> loaded = repository.loadPinnedItems();

        assertEquals(1, loaded.size());
        PinnedFolderItem loadedFolder = (PinnedFolderItem) loaded.get(0);
        assertEquals(2, loadedFolder.apps.size());
        assertEquals("com.example.packageonly", loadedFolder.apps.get(0).appRef.packageName);
        assertEquals("", loadedFolder.apps.get(0).appRef.activityName);
    }

    @Test
    public void loadPinnedItems_acceptsPackageOnlyFolderRefsFromStoredJson() {
        preferences.pinnedItemsV2 = "{\"schemaVersion\":1,\"items\":[{\"type\":\"folder\",\"id\":\"folder-3\",\"title\":\"Stored\",\"apps\":[{\"packageName\":\"com.example.packageonly\"},{\"packageName\":\"com.example.full\",\"activityName\":\"Main\"}]}]}";

        List<PinnedItem> loaded = repository.loadPinnedItems();

        assertEquals(1, loaded.size());
        assertEquals(PinnedItem.TYPE_FOLDER, loaded.get(0).getType());
        PinnedFolderItem loadedFolder = (PinnedFolderItem) loaded.get(0);
        assertEquals(2, loadedFolder.apps.size());
        assertEquals("com.example.packageonly", loadedFolder.apps.get(0).appRef.packageName);
        assertEquals("", loadedFolder.apps.get(0).appRef.activityName);
        assertEquals("com.example.full", loadedFolder.apps.get(1).appRef.packageName);
        assertEquals("Main", loadedFolder.apps.get(1).appRef.activityName);
    }

    @Test
    public void pinnedAppRoundTrip_preservesIconOverrideAndBumpsSchema() {
        List<PinnedItem> items = new ArrayList<>();
        items.add(new PinnedAppItem(
            new AppRef("com.example.one", "A"),
            new PinnedIconOverride(PinnedIconOverride.SOURCE_ICON_PACK, "pack.example", "ic_one", "One")
        ));

        repository.savePinnedItems(items);
        List<PinnedItem> loaded = repository.loadPinnedItems();

        assertEquals(LauncherConfigRepository.SCHEMA_VERSION, preferences.schemaVersion);
        assertEquals(1, loaded.size());
        PinnedAppItem item = (PinnedAppItem) loaded.get(0);
        assertEquals("pack.example", item.iconOverride.iconPackPackage);
        assertEquals("ic_one", item.iconOverride.drawableName);
        assertEquals("One", item.iconOverride.displayLabel);
    }

    @Test
    public void folderRoundTrip_preservesIconOverride() {
        List<PinnedItem> items = new ArrayList<>();
        PinnedFolderItem folder = new PinnedFolderItem("folder-icons", "Icons");
        folder.apps.add(new PinnedAppItem(
            new AppRef("com.example.custom", "Main"),
            new PinnedIconOverride(PinnedIconOverride.SOURCE_ICON_PACK, "pack.example", "ic_custom", "Custom")
        ));
        folder.apps.add(new PinnedAppItem(new AppRef("com.example.second", "Main")));
        items.add(folder);

        repository.savePinnedItems(items);
        List<PinnedItem> loaded = repository.loadPinnedItems();

        PinnedFolderItem loadedFolder = (PinnedFolderItem) loaded.get(0);
        assertEquals(2, loadedFolder.apps.size());
        PinnedAppItem folderApp = loadedFolder.apps.get(0);
        assertEquals("com.example.custom", folderApp.appRef.packageName);
        assertEquals("pack.example", folderApp.iconOverride.iconPackPackage);
        assertEquals("ic_custom", folderApp.iconOverride.drawableName);
        assertEquals("Custom", folderApp.iconOverride.displayLabel);
    }

    @Test
    public void loadPinnedItems_migratesSchemaOneAppWithoutIconOverride() {
        preferences.pinnedItemsV2 = "{\"schemaVersion\":1,\"items\":[{\"type\":\"app\",\"packageName\":\"com.example\",\"activityName\":\"Main\"}]}";

        List<PinnedItem> loaded = repository.loadPinnedItems();

        assertEquals(1, loaded.size());
        PinnedAppItem item = (PinnedAppItem) loaded.get(0);
        assertEquals("com.example", item.appRef.packageName);
        assertEquals("Main", item.appRef.activityName);
        assertNull(item.iconOverride);
    }

    @Test
    public void appIconOverride_doesNotPinAppAndSurvivesDockSave() {
        AppRef ref = new AppRef("com.example.unpinned", "Main");
        PinnedIconOverride override = new PinnedIconOverride(
            PinnedIconOverride.SOURCE_ICON_PACK, "pack.example", "ic_unpinned", "Unpinned"
        );

        repository.saveAppIconOverride(ref, override);
        assertTrue(repository.loadPinnedItems().isEmpty());

        List<PinnedItem> pins = new ArrayList<>();
        pins.add(new PinnedAppItem(new AppRef("com.example.other", "Main")));
        repository.savePinnedItems(pins);

        PinnedIconOverride loaded = repository.loadAppIconOverride(ref);
        assertEquals("pack.example", loaded.iconPackPackage);
        assertEquals("ic_unpinned", loaded.drawableName);
        assertEquals(1, repository.loadPinnedItems().size());
    }

    @Test
    public void appIconOverride_keepsPrimaryAndCloneIndependent() {
        AppRef primary = new AppRef("com.example.shop", "Main");
        AppRef clone = new AppRef("com.example.shop", "Main", 10, 42L, true, "Clone 10");
        repository.saveAppIconOverride(primary, new PinnedIconOverride(
            PinnedIconOverride.SOURCE_ICON_PACK, "pack.example", "ic_primary", "Primary"));
        repository.saveAppIconOverride(clone, new PinnedIconOverride(
            PinnedIconOverride.SOURCE_ICON_PACK, "pack.example", "ic_clone", "Clone"));

        assertEquals("ic_primary", repository.loadAppIconOverride(primary).drawableName);
        assertEquals("ic_clone", repository.loadAppIconOverride(clone).drawableName);

        repository.saveAppIconOverride(primary, null);
        assertNull(repository.loadAppIconOverride(primary));
        assertEquals("ic_clone", repository.loadAppIconOverride(clone).drawableName);
    }

    @Test
    public void pruneInvalidIconOverrides_cleansAppDockAndFolderScopes() {
        PinnedIconOverride valid = new PinnedIconOverride(
            PinnedIconOverride.SOURCE_ICON_PACK, "pack.example", "keep", "Keep");
        PinnedIconOverride missing = new PinnedIconOverride(
            PinnedIconOverride.SOURCE_ICON_PACK, "pack.example", "missing", "Missing");
        AppRef appWide = new AppRef("com.example.appwide", "Main");
        repository.saveAppIconOverride(appWide, missing);

        List<PinnedItem> pins = new ArrayList<>();
        pins.add(new PinnedAppItem(new AppRef("com.example.valid", "Main"), valid));
        pins.add(new PinnedAppItem(new AppRef("com.example.invalid", "Main"), missing));
        PinnedFolderItem folder = new PinnedFolderItem("folder-prune", "Folder");
        folder.apps.add(new PinnedAppItem(new AppRef("com.example.folder", "Main"), missing));
        folder.apps.add(new PinnedAppItem(new AppRef("com.example.folder.second", "Main")));
        pins.add(folder);
        repository.savePinnedItems(pins);

        assertTrue(repository.pruneInvalidIconOverrides(override -> "keep".equals(override.drawableName)));

        assertNull(repository.loadAppIconOverride(appWide));
        List<PinnedItem> loaded = repository.loadPinnedItems();
        assertEquals("keep", ((PinnedAppItem) loaded.get(0)).iconOverride.drawableName);
        assertNull(((PinnedAppItem) loaded.get(1)).iconOverride);
        assertNull(((PinnedFolderItem) loaded.get(2)).apps.get(0).iconOverride);
    }
}
