package com.termux.app.launcher.widget;

import android.app.Application;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Pages come from the widgets on them. Every empty page goes — the first, one in the middle, the
 * last — and what is left renumbers behind it. Two empty pages stay: the one page a pane with no
 * widgets at all is, and a page the user added by hand that has not held a widget yet.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LauncherWidgetEmptyPageTest {

    @Test public void aPopulatedLastPageGainsNothingBehindIt() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 0)));

        assertTrue(repository.trimEmptyPages());

        assertEquals("no spare page follows the widgets any more", 1, repository.pageCount());
    }

    @Test public void everyEmptyPageGoes_leadingMiddleAndTrailing() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        for (int i = 0; i < 4; i++) assertTrue(repository.addPage() >= 0);
        assertEquals(5, repository.pageCount());
        // Pages 0, 2 and 4 are empty; the widgets sit on 1 and 3.
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 1)));
        assertTrue(repository.putRecord(record(2, new WidgetCellRect(0, 0, 1, 1), 3)));

        assertTrue(repository.trimEmptyPages());

        assertEquals(2, repository.pageCount());
        assertEquals("the widgets renumbered down with the pages", 0, repository.get(1).page);
        assertEquals(1, repository.get(2).page);
    }

    @Test public void aLayoutWithNoWidgetsAnywhereKeepsOnePage() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertEquals(1, repository.addPage());
        assertEquals(2, repository.addPage());

        assertTrue(repository.trimEmptyPages());

        assertEquals(1, repository.pageCount());
        assertTrue("and trimming again changes nothing", repository.trimEmptyPages());
        assertEquals(1, repository.pageCount());
    }

    @Test public void removingTheLastWidgetOffAPageTakesThatPageBack() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertEquals(1, repository.addPage());
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertTrue(repository.putRecord(record(2, new WidgetCellRect(0, 0, 1, 1), 1)));
        assertEquals(2, repository.pageCount());

        assertTrue(repository.removeRecord(2));
        assertTrue(repository.trimEmptyPages());

        assertEquals(1, repository.pageCount());
        assertEquals(0, repository.get(1).page);
    }

    @Test public void aPageAddedByHandStaysUntilItHasHeldAWidget() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertEquals(1, repository.addFreshPage());

        assertTrue(repository.trimEmptyPages());
        assertEquals("the page the user asked for stays, empty as it is", 2,
            repository.pageCount());
        assertEquals(Collections.singleton(1), repository.freshPages());

        // A widget lands on it: it is an ordinary page from now on.
        assertTrue(repository.putRecord(record(2, new WidgetCellRect(0, 0, 1, 1), 1)));
        assertTrue(repository.freshPages().isEmpty());
        assertTrue(repository.trimEmptyPages());
        assertEquals(2, repository.pageCount());

        // And the last widget leaving takes it with it.
        assertTrue(repository.removeRecord(2));
        assertTrue(repository.trimEmptyPages());
        assertEquals(1, repository.pageCount());
    }

    @Test public void aPageAddedByHandRenumbersWithTheRest() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertEquals(1, repository.addPage());
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 1)));
        assertEquals(2, repository.addFreshPage());

        assertTrue(repository.trimEmptyPages());

        assertEquals("the empty first page went", 2, repository.pageCount());
        assertEquals(0, repository.get(1).page);
        assertEquals(Collections.singleton(1), repository.freshPages());
    }

    @Test public void removingAPageByHandRenumbersTheFreshOnesToo() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertEquals(1, repository.addFreshPage());
        assertEquals(2, repository.addFreshPage());

        assertTrue(repository.removePage(1));

        assertEquals(2, repository.pageCount());
        assertEquals(Collections.singleton(1), repository.freshPages());
    }

    @Test public void anAddInFlightKeepsThePageItReserved() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertEquals(1, repository.addPage());
        assertEquals(2, repository.addPage());
        assertTrue(repository.reservePending(repository.revision(), new WidgetAddTransaction(
            "token", 7, WidgetTestFixtures.PROVIDER, 0,
            WidgetAddTransaction.Stage.ALLOCATED, new WidgetCellRect(0, 0, 1, 1), 2,
            repository.revision(), null, new Bundle(), 0L)));

        assertTrue(repository.trimEmptyPages());

        assertEquals("the reservation's own page is all that is left", 1, repository.pageCount());
        assertEquals("and it renumbered down to it", 0, repository.pending().page);
    }

    @Test public void theTrimmedPageCountIsPersisted() throws Exception {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertEquals(1, repository.addPage());
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 1)));
        assertTrue(repository.trimEmptyPages());

        assertEquals(1, new JSONObject(storage.value).getInt("pages"));
        assertEquals(1, new LauncherWidgetRepository(storage).pageCount());
    }

    @Test public void aPageAddedByHandSurvivesASaveAndALoad() throws Exception {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertEquals(1, repository.addFreshPage());

        JSONArray fresh = new JSONObject(storage.value).getJSONArray("freshPages");
        assertEquals(1, fresh.length());
        assertEquals(1, fresh.getInt(0));

        LauncherWidgetRepository restored = new LauncherWidgetRepository(storage);
        assertEquals(2, restored.pageCount());
        assertEquals(Collections.singleton(1), restored.freshPages());
        assertTrue(restored.trimEmptyPages());
        assertEquals("still the user's page after a restart", 2, restored.pageCount());
    }

    @Test public void aSaveWithoutTheFieldHasNoPagesAddedByHand() throws Exception {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository seed = new LauncherWidgetRepository(storage);
        assertTrue(seed.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertEquals(1, seed.addFreshPage());
        // Exactly what a build before hand-added pages wrote: the count, and no such field.
        JSONObject older = new JSONObject(storage.value);
        older.remove("freshPages");
        storage.value = older.toString();

        LauncherWidgetRepository loaded = new LauncherWidgetRepository(storage);
        assertEquals(2, loaded.pageCount());
        assertTrue(loaded.freshPages().isEmpty());
        assertTrue(loaded.trimEmptyPages());
        assertEquals("so the empty page is trimmed like any other", 1, loaded.pageCount());
    }

    @Test public void theFieldIsOnlyWrittenWhileThereIsSuchAPage() throws Exception {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertFalse(new JSONObject(storage.value).has("freshPages"));
        assertEquals(1, repository.addFreshPage());
        assertTrue(new JSONObject(storage.value).has("freshPages"));

        assertTrue(repository.putRecord(record(2, new WidgetCellRect(1, 0, 2, 1), 1)));
        assertFalse("spent the moment a widget is on it",
            new JSONObject(storage.value).has("freshPages"));
    }

    @Test public void setPagesPutsTheCountAndTheHandAddedPagesBackForARestore() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertTrue(repository.setPages(4, Arrays.asList(2, 3)));
        assertEquals(4, repository.pageCount());
        assertEquals(2, repository.freshPages().size());
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 3)));
        assertEquals("and the widget spent that page's freshness",
            Collections.singleton(2), repository.freshPages());

        assertFalse("a count that would strand a widget is refused", repository.setPageCount(2));
        assertEquals(4, repository.pageCount());
        assertFalse(repository.setPageCount(0));
    }

    private static LauncherWidgetRecord record(int id, WidgetCellRect cell, int page) {
        return new LauncherWidgetRecord(id, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.PROVIDER_MISSING, cell, page, new Bundle(), null);
    }
}
