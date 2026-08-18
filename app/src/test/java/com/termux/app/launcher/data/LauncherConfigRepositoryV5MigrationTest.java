package com.termux.app.launcher.data;

import static org.junit.Assert.*;

import com.termux.app.launcher.model.PinnedFolderItem;
import org.json.JSONObject;
import org.junit.Test;

public class LauncherConfigRepositoryV5MigrationTest {
    @Test public void v1ThroughV4BecomeStableLosslessV5() throws Exception {
        for (int version = 1; version <= 4; version++) {
            TestLauncherStore store = new TestLauncherStore();
            store.raw = "{\"schemaVersion\":" + version + ",\"items\":["
                + "{\"type\":\"app\",\"packageName\":\"before\",\"activityName\":\"A\"},"
                + "{\"type\":\"folder\",\"id\":\"kept\",\"title\":\"Work\",\"rows\":4,\"cols\":5,"
                + "\"tintOverrideEnabled\":true,\"tintColor\":123,\"apps\":["
                + "{\"packageName\":\"one\",\"activityName\":\"A\",\"userId\":10,\"userSerialNumber\":42,\"clonedProfile\":true,\"profileLabel\":\"Clone\","
                + "\"iconOverride\":{\"sourceType\":\"icon_pack\",\"iconPackPackage\":\"pack\",\"drawableName\":\"one\",\"displayLabel\":\"One\"}},"
                + "{\"packageName\":\"two\",\"activityName\":\"B\"}]},"
                + "{\"type\":\"app\",\"packageName\":\"after\",\"activityName\":\"A\"}],"
                + "\"appIconOverrides\":[{\"packageName\":\"root\",\"activityName\":\"R\",\"iconOverride\":{\"sourceType\":\"icon_pack\",\"iconPackPackage\":\"pack\",\"drawableName\":\"root\",\"displayLabel\":\"Root\"}}]}";
            LauncherConfigSnapshot first = new LauncherConfigRepository(store).loadSnapshot();
            assertEquals(3, first.dockItems.size());
            PinnedFolderItem folder = first.folder("kept");
            assertSame(folder, first.dockItems.get(1));
            assertEquals("Work", folder.title);
            assertEquals(4, folder.rows);
            assertEquals(5, folder.cols);
            assertEquals(123, folder.tintColor);
            assertEquals("Clone", folder.apps.get(0).appRef.profileLabel);
            assertEquals("one", folder.apps.get(0).iconOverride.drawableName);
            assertTrue(first.appIconOverridesJson.contains("root"));
            JSONObject migrated = new JSONObject(store.raw);
            assertEquals(5, migrated.getInt("schemaVersion"));
            assertEquals("folderRef", migrated.getJSONArray("items").getJSONObject(1).getString("type"));
            String once = store.raw;
            LauncherConfigSnapshot second = new LauncherConfigRepository(store).loadSnapshot();
            assertEquals(once, store.raw);
            assertEquals(first.folders.keySet(), second.folders.keySet());
        }
    }
}
