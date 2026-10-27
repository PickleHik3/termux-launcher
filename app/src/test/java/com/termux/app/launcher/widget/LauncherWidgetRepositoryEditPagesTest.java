package com.termux.app.launcher.widget;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import com.termux.app.launcher.home.HomePageEdits;
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

    private static final WidgetGridDefinition PORTRAIT = new WidgetGridDefinition(5, 4);
    private static final WidgetGridDefinition LANDSCAPE = new WidgetGridDefinition(3, 6);

    private static LauncherWidgetRecord widget(int id, int page) {
        return widgetAt(id, page, 0, 0);
    }

    private static LauncherWidgetRecord widgetAt(int id, int page, int left, int top) {
        return new LauncherWidgetRecord(id, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(left, top, left + 1, top + 1),
            page, null, null);
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

    @Test
    public void theTrimSaysHowItRenumbered() {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(new WidgetTestFixtures.Memory());
        assertEquals(1, repository.addPage());
        assertEquals(2, repository.addPage());
        assertTrue(repository.putRecord(widget(10, 0)));
        assertTrue(repository.putRecord(widget(12, 2)));
        assertArrayEquals(new int[] {0, HomePageEdits.REMOVED, 1},
            repository.trimEmptyPagesRenumbering());
        assertEquals(1, repository.get(12).page);
        assertNull("nothing left to trim", repository.trimEmptyPagesRenumbering());
        assertTrue(repository.trimEmptyPages());
    }

    @Test
    public void aLayoutWithMorePagesThanTheMappingRenumbersPastIt() {
        int[] removal = HomePageEdits.removeMapping(3, 1);
        assertEquals(0, HomePageEdits.renumber(0, removal));
        assertEquals(HomePageEdits.REMOVED, HomePageEdits.renumber(1, removal));
        assertEquals("a page past the mapping moves down with the rest", 3,
            HomePageEdits.renumber(4, removal));
        assertEquals(4, HomePageEdits.pageCountAfter(5, removal));
        assertEquals(1, HomePageEdits.pageCountAfter(1, HomePageEdits.removeMapping(2, 0)));
        int[] move = HomePageEdits.moveMapping(3, 0, 2);
        assertEquals(5, HomePageEdits.renumber(5, move));
        assertEquals(6, HomePageEdits.pageCountAfter(6, move));
    }

    @Test
    public void aMoveRenumbersTheShelvedOrientationToo() {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(new WidgetTestFixtures.Memory());
        assertEquals(1, repository.addPage());
        assertTrue(repository.putRecord(widget(10, 0)));
        assertTrue(repository.putRecord(widget(11, 1)));
        assertFalse(repository.applyOrientation("portrait", PORTRAIT));
        assertTrue(repository.applyOrientation("landscape", LANDSCAPE));

        assertTrue(repository.movePage(0, 1));
        assertEquals(1, repository.get(10).page);

        assertTrue(repository.applyOrientation("portrait", PORTRAIT));
        assertEquals("portrait's page 1 moved too", 1, repository.get(10).page);
        assertEquals(0, repository.get(11).page);
    }

    @Test
    public void aRemovalDropsTheShelvedOrientationsPageAndRenumbersTheRest() {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(new WidgetTestFixtures.Memory());
        assertEquals(1, repository.addPage());
        assertEquals(2, repository.addPage());
        assertTrue(repository.putRecord(widget(11, 1)));
        assertTrue(repository.putRecord(widget(12, 2)));
        // Its own cell, so laying it out afresh in portrait moves nothing else.
        assertTrue(repository.putRecord(widgetAt(10, 0, 2, 2)));
        assertFalse(repository.applyOrientation("portrait", PORTRAIT));
        assertTrue(repository.applyOrientation("landscape", LANDSCAPE));

        // Edit pages empties page 0 of the wall on screen and removes it; the shelf names pages
        // by id, so the removed page's cells leave it with the page.
        assertTrue(repository.removeRecord(10));
        assertTrue(repository.removePage(0));

        assertTrue(repository.applyOrientation("portrait", PORTRAIT));
        assertEquals(0, repository.get(11).page);
        assertEquals(1, repository.get(12).page);
    }
}
