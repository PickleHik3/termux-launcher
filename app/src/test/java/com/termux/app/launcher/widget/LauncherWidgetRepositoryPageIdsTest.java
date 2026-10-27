package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import androidx.annotation.NonNull;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Home's widgets and icons are keyed by page id; the index API is a view of the owner's order. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class LauncherWidgetRepositoryPageIdsTest {
    private static final WidgetGridDefinition PORTRAIT = new WidgetGridDefinition(5, 4);
    private static final WidgetGridDefinition LANDSCAPE = new WidgetGridDefinition(4, 6);

    /** The wall's pages as the repository sees them: ids in order, ids handed out after 20. */
    private static final class Owner implements LauncherWidgetRepository.HomePageOwner {
        final List<Integer> pages = new ArrayList<>();
        int next = 20;
        Owner(Integer... ids) { pages.addAll(Arrays.asList(ids)); }
        @NonNull @Override public List<Integer> homePageIds() { return new ArrayList<>(pages); }
        @NonNull @Override public List<Integer> addHomePages(int count) {
            List<Integer> added = new ArrayList<>();
            for (int i = 0; i < count; i++) added.add(next++);
            pages.addAll(added);
            return added;
        }
        @Override public void removeHomePages(@NonNull Collection<Integer> ids) {
            List<Integer> kept = new ArrayList<>(pages);
            kept.removeAll(ids);
            if (!kept.isEmpty()) pages.removeAll(ids);
        }
        @Override public void reorderHomePages(@NonNull List<Integer> order) {
            pages.clear();
            pages.addAll(order);
        }
    }

    private static LauncherWidgetRecord record(int id, int cellLeft, int page) {
        return new LauncherWidgetRecord(id, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.PROVIDER_MISSING,
            new WidgetCellRect(cellLeft, 0, cellLeft + 1, 1), page, new Bundle(), null);
    }

    private static String legacy(int pages, String records, String extra) {
        return "{\"version\":4,\"revision\":3,\"grid\":{\"rows\":5,\"columns\":4},\"pages\":" + pages
            + ",\"records\":[" + records + "]" + extra + "}";
    }

    private static String legacyRecord(int id, int page) {
        return "{\"id\":" + id + ",\"provider\":\"" + WidgetTestFixtures.PROVIDER.flattenToString()
            + "\",\"profile\":0,\"state\":\"PROVIDER_MISSING\",\"page\":" + page
            + ",\"cell\":{\"left\":0,\"top\":0,\"right\":1,\"bottom\":1}}";
    }

    @Test public void aWallWithoutTheMarkerIsReadByPositionAndWrittenBackByIdOnce() throws Exception {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        // Positions 1 and 2 of three pages, on an owner whose ids are not 1..n.
        Owner owner = new Owner(7, 9, 4);
        storage.value = legacy(3, legacyRecord(15, 1) + "," + legacyRecord(16, 2), "");
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage, owner);
        assertEquals(3, repository.pageCount());
        assertEquals(1, repository.get(15).page);
        assertEquals(2, repository.get(16).page);
        JSONObject written = new JSONObject(storage.value);
        assertEquals("id", written.getString("pageKey"));
        assertEquals(9, written.getJSONArray("records").getJSONObject(0).getInt("page"));
        assertEquals(4, written.getJSONArray("records").getJSONObject(1).getInt("page"));

        // Reading it again changes nothing: the marker says the pages are ids already.
        String once = storage.value;
        LauncherWidgetRepository again = new LauncherWidgetRepository(storage, new Owner(7, 9, 4));
        assertEquals(once, storage.value);
        assertEquals(1, again.get(15).page);
        assertEquals(2, again.get(16).page);
    }

    @Test public void aWallKeyedByIdFollowsTheOwnersOrderNotTheOldPositions() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        Owner owner = new Owner(1, 2, 3);
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage, owner);
        assertTrue(repository.putRecord(record(11, 0, 0)));
        assertTrue(repository.putRecord(record(12, 0, 2)));
        // The owner moves its last page to the front, by another route.
        owner.pages.clear();
        owner.pages.addAll(Arrays.asList(3, 1, 2));
        repository.onHomePagesChanged();
        assertEquals(0, repository.get(12).page);
        assertEquals(1, repository.get(11).page);
        // A restart on the same owner reads the same places.
        LauncherWidgetRepository restored = new LauncherWidgetRepository(storage, owner);
        assertEquals(0, restored.get(12).page);
        assertEquals(1, restored.get(11).page);
    }

    @Test public void aPageTheOwnerDroppedKeepsItsRecordsAsideUntilItIsGoneForGood() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        Owner owner = new Owner(1, 2);
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage, owner);
        assertTrue(repository.putRecord(record(11, 0, 1)));
        owner.pages.remove(Integer.valueOf(2));
        repository.onHomePagesChanged();
        assertEquals(1, repository.pageCount());
        assertTrue(repository.recordsOnPage(0).isEmpty());
        assertNull(repository.get(11));
        // Undone: the page and what stood on it come back.
        owner.pages.add(2);
        repository.onHomePagesChanged();
        assertEquals(1, repository.get(11).page);
        // Removed and then final.
        owner.pages.remove(Integer.valueOf(2));
        repository.onHomePagesChanged();
        repository.pageRemoved(2);
        owner.pages.add(2);
        repository.onHomePagesChanged();
        assertNull(repository.get(11));
    }

    @Test public void trimAsksTheOwnerToRemoveTheEmptyPagesAndSparesTheLastAndTheFresh() {
        Owner owner = new Owner(1, 2, 3, 4);
        LauncherWidgetRepository repository =
            new LauncherWidgetRepository(new WidgetTestFixtures.Memory(), owner);
        assertTrue(repository.putRecord(record(11, 0, 2)));
        List<Integer> trimmed = repository.trimEmptyPageIds();
        assertEquals(Collections.singletonList(3), owner.pages);
        assertEquals(1, repository.pageCount());
        assertEquals(0, repository.get(11).page);
        assertEquals(Arrays.asList(1, 2, 4), trimmed);

        Owner empty = new Owner(5, 6);
        LauncherWidgetRepository none =
            new LauncherWidgetRepository(new WidgetTestFixtures.Memory(), empty);
        none.trimEmptyPages();
        assertEquals("one empty page is what is left", Collections.singletonList(5), empty.pages);
    }

    @Test public void aPageAddedByHandIsFreshByIdAndSurvivesTheTrimAndAMove() {
        Owner owner = new Owner(1, 2);
        LauncherWidgetRepository repository =
            new LauncherWidgetRepository(new WidgetTestFixtures.Memory(), owner);
        assertTrue(repository.putRecord(record(11, 0, 0)));
        int added = repository.addFreshPage();
        assertEquals(2, added);
        assertEquals(Arrays.asList(1, 2, 20), owner.pages);
        assertEquals(Collections.singleton(2), repository.freshPages());
        assertTrue(repository.movePage(2, 0));
        assertEquals(Arrays.asList(20, 1, 2), owner.pages);
        assertEquals("the fresh page moved with its id", Collections.singleton(0),
            repository.freshPages());
        repository.trimEmptyPages();
        assertEquals("page 2 was empty and not fresh", Arrays.asList(20, 1), owner.pages);
    }

    @Test public void aShelvedLayoutNamesPagesByIdAndSurvivesAMoveAndARemoval() throws Exception {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        Owner owner = new Owner(1, 2, 3);
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage, owner);
        assertFalse(repository.applyOrientation("portrait", PORTRAIT));
        assertTrue(repository.putRecord(record(11, 0, 1)));
        assertTrue(repository.putRecord(record(12, 0, 2)));
        assertTrue(repository.applyOrientation("landscape", LANDSCAPE));
        // While landscape is on screen the last page moves up front and the empty one goes.
        assertTrue(repository.movePage(2, 0));
        assertTrue(repository.removePage(1));
        assertEquals(Arrays.asList(3, 2), owner.pages);
        assertTrue(repository.applyOrientation("portrait", PORTRAIT));
        assertEquals(1, repository.get(11).page);
        assertEquals(0, repository.get(12).page);
        JSONObject root = new JSONObject(storage.value);
        assertFalse("a shelf has no page count of its own",
            root.has("layouts") && root.getJSONObject("layouts").length() > 0
                && root.getJSONObject("layouts").getJSONObject("landscape").has("pages"));
    }

    @Test public void aShelfBeyondTheOwnersPagesAsksTheOwnerToGrow() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        storage.value = legacy(1, legacyRecord(15, 0),
            ",\"orientation\":\"portrait\",\"layouts\":{\"landscape\":{\"grid\":{\"rows\":4,"
                + "\"columns\":6},\"pages\":4,\"cells\":[{\"id\":15,\"cell\":{\"left\":0,\"top\":0,"
                + "\"right\":1,\"bottom\":1},\"page\":3}]}}");
        Owner owner = new Owner(1);
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage, owner);
        assertEquals("the owner grew to the four pages the shelf names", 4, owner.pages.size());
        assertEquals(4, repository.pageCount());
        assertTrue(repository.applyOrientation("landscape", LANDSCAPE));
        assertEquals(3, repository.get(15).page);
    }

    @Test public void aFailedWriteTakesBackThePagesTheOwnerGrewForIt() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        Owner owner = new Owner(1);
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage, owner);
        storage.fail = true;
        assertEquals(-1, repository.addPage());
        assertEquals(Collections.singletonList(1), owner.pages);
        assertEquals(1, repository.pageCount());
    }
}
