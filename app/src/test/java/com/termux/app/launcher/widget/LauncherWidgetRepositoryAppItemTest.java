package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;

import com.termux.app.launcher.model.AppRef;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * App icons on Home pages in the durable store: a {@code kind} on every record of a v6 payload,
 * negative keys from the built-ins' allocator, 1×1 placement on the page in front, then the first
 * page with room, then a new page; v5 and older load unchanged.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = android.app.Application.class)
public class LauncherWidgetRepositoryAppItemTest {
    private static final AppRef MAPS = new AppRef("com.maps", "com.maps.Main", 10, 7L, true,
        "Work");
    private static final AppRef CLOCK = new AppRef("com.clock", "com.clock.Main");

    private static Bundle label(String value) {
        Bundle options = new Bundle();
        options.putString(LauncherWidgetRecord.APP_LABEL, value);
        return options;
    }

    private static LauncherWidgetRecord widget(int id, WidgetCellRect cell, int page) {
        return new LauncherWidgetRecord(id, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, cell, page, null, null);
    }

    @Test public void anAppIconRoundTripsAsVersionSixWithEveryKindNamed() throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        assertTrue(repository.putRecord(widget(7, new WidgetCellRect(0, 0, 2, 1), 0)));
        assertTrue(repository.putRecord(LauncherWidgetRecord.builtin(-1, "battery",
            new WidgetCellRect(2, 0, 4, 1), 0, null)));
        LauncherWidgetRecord placed = repository.addApp(MAPS, 0, label("Maps"));
        assertNotNull(placed);
        assertEquals(-2, placed.appWidgetId);
        assertEquals(new WidgetCellRect(0, 1, 1, 2), placed.cell);

        JSONObject root = new JSONObject(memory.value);
        assertEquals(6, root.getInt("version"));
        JSONArray records = root.getJSONArray("records");
        List<String> kinds = new ArrayList<>();
        for (int i = 0; i < records.length(); i++) {
            kinds.add(records.getJSONObject(i).getString("kind"));
        }
        assertEquals(java.util.Arrays.asList("APP_WIDGET", "BUILTIN", "APP"), kinds);

        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(memory);
        LauncherWidgetRecord app = reloaded.get(-2);
        assertNotNull(app);
        assertTrue(app.isApp());
        assertFalse(app.isBuiltin());
        assertFalse(app.hasPlatformId());
        assertEquals(LauncherWidgetRecord.Kind.APP, app.kind);
        AppRef ref = app.appRef();
        assertNotNull(ref);
        assertEquals(MAPS.stableId(), ref.stableId());
        assertEquals(10, ref.userId);
        assertEquals(7L, ref.userSerialNumber);
        assertTrue(ref.clonedProfile);
        assertEquals("Work", ref.profileLabel);
        assertEquals(new ComponentName("com.maps", "com.maps.Main"), app.provider);
        assertEquals("Maps", app.appLabel());
        assertEquals(LauncherWidgetRecord.Kind.BUILTIN, reloaded.get(-1).kind);
        assertEquals(LauncherWidgetRecord.Kind.APP_WIDGET, reloaded.get(7).kind);
        assertTrue(reloaded.get(7).hasPlatformId());
    }

    @Test public void aVersionFiveWallLoadsUnchangedWithItsKindsInferred() throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository writer = new LauncherWidgetRepository(memory);
        assertTrue(writer.putRecord(widget(7, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertTrue(writer.putRecord(LauncherWidgetRecord.builtin(-3, "clock.digital",
            new WidgetCellRect(1, 0, 3, 2), 0, null)));
        String v5 = memory.value;
        JSONObject root = new JSONObject(v5);
        assertEquals(5, root.getInt("version"));
        assertFalse(root.getJSONArray("records").getJSONObject(0).has("kind"));

        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(memory);
        assertEquals(2, reloaded.records().size());
        assertEquals(LauncherWidgetRecord.Kind.APP_WIDGET, reloaded.get(7).kind);
        assertEquals(LauncherWidgetRecord.Kind.BUILTIN, reloaded.get(-3).kind);
        assertEquals("clock.digital", reloaded.get(-3).builtinKind);
        assertEquals(new WidgetCellRect(1, 0, 3, 2), reloaded.get(-3).cell);
        assertEquals("loading wrote nothing", v5, memory.value);

        // The first app icon is what turns the payload into v6; its key is below the built-in's.
        LauncherWidgetRecord app = reloaded.addApp(CLOCK, 0, label("Clock"));
        assertNotNull(app);
        assertEquals(-4, app.appWidgetId);
        assertEquals(6, new JSONObject(memory.value).getInt("version"));
    }

    @Test public void removingTheLastAppIconWritesTheOlderVersionAgain() throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        assertTrue(repository.putRecord(widget(7, new WidgetCellRect(0, 0, 1, 1), 0)));
        LauncherWidgetRecord app = repository.addApp(CLOCK, 0, label("Clock"));
        assertNotNull(app);
        assertEquals(6, new JSONObject(memory.value).getInt("version"));
        assertTrue(repository.removeRecord(app.appWidgetId));
        assertNull(repository.get(app.appWidgetId));
        JSONObject root = new JSONObject(memory.value);
        assertEquals(4, root.getInt("version"));
        assertFalse(root.getJSONArray("records").getJSONObject(0).has("kind"));
    }

    @Test public void aFullPageInFrontSendsTheIconToTheFirstPageWithRoom() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(1, 2)));
        assertEquals(1, repository.addPage());
        assertEquals(2, repository.addPage());
        // Page 0 has one free cell, page 1 is full, page 2 is empty.
        assertTrue(repository.putRecord(widget(7, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertTrue(repository.putRecord(widget(8, new WidgetCellRect(0, 0, 2, 1), 1)));

        LauncherWidgetRecord inFront = repository.addApp(CLOCK, 2, label("Clock"));
        assertNotNull(inFront);
        assertEquals("the page in front has room", 2, inFront.page);
        assertEquals(new WidgetCellRect(0, 0, 1, 1), inFront.cell);

        LauncherWidgetRecord fromFull = repository.addApp(MAPS, 1, label("Maps"));
        assertNotNull(fromFull);
        assertEquals("the first page with room, from the first", 0, fromFull.page);
        assertEquals(new WidgetCellRect(1, 0, 2, 1), fromFull.cell);
    }

    @Test public void everyPageFullAppendsAPageInTheSameCommit() {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(1, 1)));
        assertTrue(repository.putRecord(widget(7, new WidgetCellRect(0, 0, 1, 1), 0)));
        long revision = repository.revision();
        LauncherWidgetRecord app = repository.addApp(CLOCK, 0, label("Clock"));
        assertNotNull(app);
        assertEquals(1, app.page);
        assertEquals(2, repository.pageCount());
        assertEquals("one commit for the page and the icon", revision + 1, repository.revision());
    }

    @Test public void aFailedWriteLeavesNothingBehind() {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        memory.fail = true;
        assertNull(repository.addApp(CLOCK, 0, label("Clock")));
        assertTrue(repository.records().isEmpty());
        assertEquals(1, repository.pageCount());
    }

    @Test public void theIconAvoidsTheCellAWidgetAddHasReserved() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(1, 2)));
        long revision = repository.revision();
        WidgetAddTransaction reservation = new WidgetAddTransaction("token", 20,
            WidgetTestFixtures.PROVIDER, 0, WidgetAddTransaction.Stage.ALLOCATED,
            new WidgetCellRect(0, 0, 1, 1), 0, revision, null, new Bundle(), 10);
        assertTrue(repository.reservePending(revision, reservation));
        LauncherWidgetRecord app = repository.addApp(CLOCK, 0, label("Clock"));
        assertNotNull(app);
        assertEquals(new WidgetCellRect(1, 0, 2, 1), app.cell);
        assertNotNull("the reservation stands", repository.pending());
    }

    @Test public void aMoveToAnotherPageKeepsTheIconWhole() throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        LauncherWidgetRecord app = repository.addApp(MAPS, 0, label("Maps"));
        assertNotNull(app);
        assertEquals(1, repository.addFreshPage());
        LauncherWidgetRecord moved = app.withPage(1).withCell(new WidgetCellRect(2, 3, 3, 4));
        assertTrue(repository.putRecords(Collections.singletonList(moved)));

        LauncherWidgetRecord reloaded = new LauncherWidgetRepository(memory).get(app.appWidgetId);
        assertNotNull(reloaded);
        assertTrue(reloaded.isApp());
        assertEquals(1, reloaded.page);
        assertEquals(new WidgetCellRect(2, 3, 3, 4), reloaded.cell);
        assertEquals(MAPS.stableId(), reloaded.appRef().stableId());
        assertEquals("Maps", reloaded.appLabel());
        assertTrue("the page it left is empty now", repository.recordsOnPage(0).isEmpty());
    }

    @Test public void aStoredFolderIsLeftOutAndTheRestOfTheWallStands() throws Exception {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository writer = new LauncherWidgetRepository(memory);
        assertTrue(writer.putRecord(widget(7, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertNotNull(writer.addApp(CLOCK, 0, label("Clock")));
        JSONObject root = new JSONObject(memory.value);
        JSONObject folder = new JSONObject().put("id", -9).put("kind", "FOLDER")
            .put("provider", "folder/x").put("profile", 0).put("state", "ACTIVE")
            .put("cell", new JSONObject().put("left", 3).put("top", 3).put("right", 4)
                .put("bottom", 4))
            .put("page", 0).put("options", new JSONObject());
        root.getJSONArray("records").put(folder);
        memory.value = root.toString();

        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(memory);
        assertEquals(2, reloaded.records().size());
        assertNull(reloaded.get(-9));
        assertNotNull(reloaded.get(7));
    }

    @Test(expected = IllegalArgumentException.class)
    public void anAppIconNeedsANegativeKey() {
        LauncherWidgetRecord.app(3, CLOCK, new WidgetCellRect(0, 0, 1, 1), 0, null);
    }

    @Test public void thePlacementPolicyPrefersThePageInFrontThenTheFirstThenANewOne() {
        WidgetGridDefinition grid = new WidgetGridDefinition(1, 1);
        List<List<LauncherWidgetRecord>> pages = new ArrayList<>();
        pages.add(Collections.singletonList(widget(1, new WidgetCellRect(0, 0, 1, 1), 0)));
        pages.add(new ArrayList<>());
        pages.add(new ArrayList<>());
        HomeItemPlacementPolicy.Choice inFront = HomeItemPlacementPolicy.place(grid, 3, 2,
            pages::get, 1, 1);
        assertEquals(2, inFront.page);
        assertFalse(inFront.appendsPage);
        HomeItemPlacementPolicy.Choice first = HomeItemPlacementPolicy.place(grid, 3, 0,
            pages::get, 1, 1);
        assertEquals(1, first.page);
        HomeItemPlacementPolicy.Choice clamped = HomeItemPlacementPolicy.place(grid, 3, 9,
            pages::get, 1, 1);
        assertEquals("a page past the last reads as the last", 2, clamped.page);
        pages.set(1, Collections.singletonList(widget(2, new WidgetCellRect(0, 0, 1, 1), 1)));
        pages.set(2, Collections.singletonList(widget(3, new WidgetCellRect(0, 0, 1, 1), 2)));
        HomeItemPlacementPolicy.Choice fresh = HomeItemPlacementPolicy.place(grid, 3, 1,
            pages::get, 1, 1);
        assertEquals(3, fresh.page);
        assertTrue(fresh.appendsPage);
        assertNull("larger than the grid", HomeItemPlacementPolicy.place(grid, 3, 0,
            pages::get, 2, 1));
    }
}
