package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.os.Bundle;

import com.termux.app.launcher.model.AppRef;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Folders on Home in the durable store: made by a drop in one commit, grown, shrunk back to an
 * icon or to nothing, renamed, written as v7 while one exists, and read leniently so a folder
 * that cannot be read costs only its own cell.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = android.app.Application.class)
public class LauncherWidgetRepositoryFolderTest {
    private static final AppRef MAPS = new AppRef("com.maps", "com.maps.Main", 10, 7L, true,
        "Work");
    private static final AppRef CLOCK = new AppRef("com.clock", "com.clock.Main");
    private static final AppRef MAIL = new AppRef("com.mail", "com.mail.Main");

    private static Bundle label(String value) {
        Bundle options = new Bundle();
        options.putString(LauncherWidgetRecord.APP_LABEL, value);
        return options;
    }

    private static LauncherWidgetRecord widget(int id, WidgetCellRect cell, int page) {
        return new LauncherWidgetRecord(id, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, cell, page, null, null);
    }

    /** A wall with a widget, then Maps (key -1) and Clock (key -2) on page 0. */
    private static LauncherWidgetRepository wall(WidgetTestFixtures.Memory memory) {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        assertTrue(repository.putRecord(widget(7, new WidgetCellRect(0, 0, 2, 1), 0)));
        assertNotNull(repository.addApp(MAPS, 0, label("Maps")));
        assertNotNull(repository.addApp(CLOCK, 0, label("Clock")));
        return repository;
    }

    @Test public void droppingAnIconOnAnotherMakesAFolderInTheTargetsCell() throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        LauncherWidgetRecord maps = repository.get(-1);
        LauncherWidgetRecord clock = repository.get(-2);
        assertTrue(maps.isApp() && clock.isApp());
        long before = repository.revision();

        LauncherWidgetRecord folder = repository.mergeIntoFolder(-1, -2, "Folder");
        assertNotNull(folder);
        assertEquals("the target's key", -2, folder.appWidgetId);
        assertTrue(folder.isFolder());
        assertEquals(clock.cell, folder.cell);
        assertEquals("Maps", folder.folderName());
        assertEquals(2, folder.folderApps().size());
        assertEquals(MAPS.stableId(), folder.folderApps().get(0).stableId());
        assertEquals("Clock", folder.folderApps().get(1).label);
        assertNull("the dropped icon went in the same commit", repository.get(-1));
        assertEquals(before + 1, repository.revision());
        assertEquals(2, repository.records().size());
        assertEquals(7, new JSONObject(memory.value).getInt("version"));
    }

    @Test public void aFolderRoundTripsAsVersionSevenWithItsAppsWhole() throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        assertNotNull(repository.mergeIntoFolder(-1, -2, "Folder"));
        JSONObject root = new JSONObject(memory.value);
        assertEquals(7, root.getInt("version"));
        JSONArray records = root.getJSONArray("records");
        JSONObject stored = null;
        for (int i = 0; i < records.length(); i++) {
            if ("FOLDER".equals(records.getJSONObject(i).getString("kind"))) {
                stored = records.getJSONObject(i);
            }
        }
        assertNotNull(stored);
        assertEquals("Maps", stored.getJSONObject("folder").getString("name"));

        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(memory);
        LauncherWidgetRecord folder = reloaded.get(-2);
        assertNotNull(folder);
        assertTrue(folder.isFolder());
        assertFalse(folder.hasPlatformId());
        assertEquals(LauncherWidgetRecord.FOLDER_PROVIDER, folder.provider);
        assertEquals("Maps", folder.folderName());
        AppRef first = folder.folderApps().get(0).app();
        assertEquals(MAPS.stableId(), first.stableId());
        assertEquals(10, first.userId);
        assertEquals(7L, first.userSerialNumber);
        assertTrue(first.clonedProfile);
        assertEquals("Work", first.profileLabel);
        assertEquals("Maps", folder.folderApps().get(0).label);
        assertEquals("Clock", folder.folderApps().get(1).label);
        assertEquals(LauncherWidgetRecord.Kind.APP_WIDGET, reloaded.get(7).kind);
    }

    @Test public void aVersionSixWallLoadsUnchangedAndTheFirstFolderMakesItSeven()
        throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        wall(memory);
        String v6 = memory.value;
        assertEquals(6, new JSONObject(v6).getInt("version"));

        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(memory);
        assertEquals(3, reloaded.records().size());
        assertTrue(reloaded.get(-1).isApp());
        assertTrue(reloaded.get(-2).isApp());
        assertEquals("loading wrote nothing", v6, memory.value);

        assertNotNull(reloaded.mergeIntoFolder(-2, -1, "Folder"));
        assertEquals(7, new JSONObject(memory.value).getInt("version"));
        assertEquals("Clock", reloaded.get(-1).folderName());
    }

    @Test public void anIconDroppedOnAFolderJoinsItAtTheEnd() {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        assertNotNull(repository.mergeIntoFolder(-1, -2, "Folder"));
        LauncherWidgetRecord mail = repository.addApp(MAIL, 0, label("Mail"));
        assertNotNull(mail);
        LauncherWidgetRecord folder = repository.mergeIntoFolder(mail.appWidgetId, -2, "Folder");
        assertNotNull(folder);
        assertEquals(-2, folder.appWidgetId);
        assertEquals(3, folder.folderApps().size());
        assertEquals("Mail", folder.folderApps().get(2).label);
        assertEquals("the name stays", "Maps", folder.folderName());
        assertNull(repository.get(mail.appWidgetId));
    }

    @Test public void aDropThatIsNoMergeChangesNothing() {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        String before = memory.value;
        assertNull("onto a widget", repository.mergeIntoFolder(-1, 7, "Folder"));
        assertNull("onto itself", repository.mergeIntoFolder(-1, -1, "Folder"));
        assertNull("a widget dragged", repository.mergeIntoFolder(7, -1, "Folder"));
        assertEquals(before, memory.value);
        assertEquals(3, repository.records().size());
    }

    @Test public void removeFromFolderPutsTheAppOnThePageInFront() {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        assertNotNull(repository.mergeIntoFolder(-1, -2, "Folder"));
        LauncherWidgetRecord mail = repository.addApp(MAIL, 0, label("Mail"));
        assertNotNull(repository.mergeIntoFolder(mail.appWidgetId, -2, "Folder"));

        LauncherWidgetRecord placed = repository.removeFromFolder(-2, MAIL.stableId(), 0);
        assertNotNull(placed);
        assertTrue(placed.isApp());
        assertEquals(0, placed.page);
        assertEquals("Mail", placed.appLabel());
        assertEquals(MAIL.stableId(), placed.appRef().stableId());
        LauncherWidgetRecord folder = repository.get(-2);
        assertTrue(folder.isFolder());
        assertEquals(2, folder.folderApps().size());
    }

    @Test public void removingOneOfTwoLeavesTheOtherAppsIconInTheFoldersCell()
        throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        LauncherWidgetRecord folder = repository.mergeIntoFolder(-1, -2, "Folder");
        long before = repository.revision();

        LauncherWidgetRecord placed = repository.removeFromFolder(-2, MAPS.stableId(), 0);
        assertNotNull(placed);
        assertEquals("one commit", before + 1, repository.revision());
        LauncherWidgetRecord left = repository.get(-2);
        assertTrue("the folder is Clock's icon again", left.isApp());
        assertEquals(CLOCK.stableId(), left.appRef().stableId());
        assertEquals("Clock", left.appLabel());
        assertEquals(folder.cell, left.cell);
        assertEquals(MAPS.stableId(), placed.appRef().stableId());
        assertFalse(placed.cell.equals(left.cell));
        assertEquals(6, new JSONObject(memory.value).optInt("version"));
    }

    @Test public void removeOfAnAppNotThereChangesNothing() {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        assertNotNull(repository.mergeIntoFolder(-1, -2, "Folder"));
        String before = memory.value;
        assertNull(repository.removeFromFolder(-2, MAIL.stableId(), 0));
        assertNull("not a folder", repository.removeFromFolder(7, MAIL.stableId(), 0));
        assertEquals(before, memory.value);
    }

    @Test public void aFullPageInFrontSendsTheRemovedAppToANewPage() {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(1, 2)));
        assertNotNull(repository.addApp(MAPS, 0, label("Maps")));
        assertNotNull(repository.addApp(CLOCK, 0, label("Clock")));
        assertNotNull(repository.mergeIntoFolder(-1, -2, "Folder"));
        // The cell Maps left takes Mail, so the one page is full again.
        LauncherWidgetRecord mail = repository.addApp(MAIL, 0, label("Mail"));
        assertNotNull(mail);
        assertEquals(0, mail.page);
        assertEquals(1, repository.pageCount());

        LauncherWidgetRecord placed = repository.removeFromFolder(-2, MAPS.stableId(), 0);
        assertNotNull(placed);
        assertEquals("a new page after the last, in the same commit", 1, placed.page);
        assertEquals(2, repository.pageCount());
        assertTrue("the folder is Clock's icon again", repository.get(-2).isApp());
        assertEquals(0, repository.get(-2).page);
    }

    @Test public void renameTrimsAndFallsBack() {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        assertNotNull(repository.mergeIntoFolder(-1, -2, "Folder"));
        assertTrue(repository.renameFolder(-2, "  Travel ", "Folder"));
        assertEquals("Travel", repository.get(-2).folderName());
        assertTrue(repository.renameFolder(-2, "   ", "Folder"));
        assertEquals("Folder", repository.get(-2).folderName());
        assertFalse("not a folder", repository.renameFolder(7, "x", "Folder"));
        assertEquals("Folder", new LauncherWidgetRepository(memory).get(-2).folderName());
    }

    @Test public void aMalformedFolderInAVersionSevenWallIsDroppedAndTheRestStands()
        throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        assertNotNull(repository.mergeIntoFolder(-1, -2, "Folder"));
        assertNotNull(repository.addApp(MAIL, 0, label("Mail")));
        JSONObject root = new JSONObject(memory.value);
        assertEquals(7, root.getInt("version"));
        JSONArray records = root.getJSONArray("records");
        // A second folder with no readable app, and a third with no folder block at all.
        JSONObject broken = new JSONObject().put("id", -20).put("kind", "FOLDER")
            .put("provider", LauncherWidgetRecord.FOLDER_PROVIDER.flattenToString())
            .put("profile", 0).put("state", "ACTIVE")
            .put("cell", new JSONObject().put("left", 3).put("top", 3).put("right", 4)
                .put("bottom", 4))
            .put("page", 0).put("options", new JSONObject())
            .put("folder", new JSONObject().put("name", "Broken")
                .put("apps", new JSONArray().put(new JSONObject().put("label", "no package"))));
        records.put(broken);
        JSONObject bare = new JSONObject(broken.toString()).put("id", -21);
        bare.remove("folder");
        bare.put("cell", new JSONObject().put("left", 2).put("top", 3).put("right", 3)
            .put("bottom", 4));
        records.put(bare);
        memory.value = root.toString();

        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(memory);
        assertNull(reloaded.get(-20));
        assertNull(reloaded.get(-21));
        assertTrue("the good folder stands", reloaded.get(-2).isFolder());
        assertEquals(2, reloaded.get(-2).folderApps().size());
        assertNotNull(reloaded.get(7));
        assertEquals("the widget, the folder and Mail", 3, reloaded.records().size());
    }

    @Test public void aStoredFolderOfOneLoadsAsThatAppsIcon() throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        assertNotNull(repository.mergeIntoFolder(-1, -2, "Folder"));
        JSONObject root = new JSONObject(memory.value);
        JSONArray records = root.getJSONArray("records");
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            if (!"FOLDER".equals(record.getString("kind"))) continue;
            JSONArray apps = record.getJSONObject("folder").getJSONArray("apps");
            // The second app is unreadable: only Maps survives.
            apps.put(1, new JSONObject().put("label", "broken"));
        }
        memory.value = root.toString();

        LauncherWidgetRecord icon = new LauncherWidgetRepository(memory).get(-2);
        assertNotNull(icon);
        assertTrue(icon.isApp());
        assertEquals(MAPS.stableId(), icon.appRef().stableId());
        assertEquals("Maps", icon.appLabel());
    }

    @Test public void removingTheLastFolderWritesVersionSixAgain() throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        assertNotNull(repository.mergeIntoFolder(-1, -2, "Folder"));
        assertEquals(7, new JSONObject(memory.value).getInt("version"));
        assertTrue(repository.removeRecord(-2));
        assertEquals(4, new JSONObject(memory.value).getInt("version"));
    }

}
