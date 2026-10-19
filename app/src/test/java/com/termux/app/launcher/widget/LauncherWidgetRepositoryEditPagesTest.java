package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.widget.LauncherWidgetRecord.FolderApp;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * The store's side of Settings › Home: a folder past the old cap of 16 is written under a version
 * an older build will not overwrite, the empty-page trim says how it renumbered, and Edit pages
 * renumbers the shelved orientation with the live one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = android.app.Application.class)
public class LauncherWidgetRepositoryEditPagesTest {

    private static LauncherWidgetRecord widget(int id, int page) {
        return new LauncherWidgetRecord(id, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(0, 0, 1, 1), page, null, null);
    }

    private static LauncherWidgetRecord folderOf(int apps) {
        List<FolderApp> members = new ArrayList<>();
        for (int i = 0; i < apps; i++) {
            members.add(new FolderApp(new AppRef("com.a" + i, "com.a" + i + ".Main"), "a" + i));
        }
        return LauncherWidgetRecord.folder(-3, "Big", members, new WidgetCellRect(1, 0, 2, 1), 0);
    }

    private static int version(WidgetTestFixtures.Memory memory) throws Exception {
        return new JSONObject(memory.value).getInt("version");
    }

    @Test
    public void aFolderPastSixteenIsWrittenUnderItsOwnVersionAndReadBackWhole() throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        assertTrue(repository.putRecord(folderOf(16)));
        assertEquals("sixteen is the old cap: the folder version as before", 7, version(memory));
        assertTrue(repository.putRecord(folderOf(18)));
        assertEquals(8, version(memory));

        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(memory);
        assertEquals(18, reloaded.get(-3).folderApps().size());
        assertTrue("a v8 store is writable by this build", reloaded.putRecord(widget(5, 0)));
    }

    @Test
    public void aVersionPastThisBuildsIsLeftAlone() throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        new LauncherWidgetRepository(memory).putRecord(folderOf(18));
        JSONObject root = new JSONObject(memory.value);
        root.put("version", 9);
        memory.value = root.toString();
        LauncherWidgetRepository future = new LauncherWidgetRepository(memory);
        assertFalse(future.putRecord(widget(5, 0)));
        assertEquals(9, version(memory));
    }

}
