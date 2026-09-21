package com.termux.app.launcher.widget;

import android.app.Application;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * The spare page: exactly one empty page follows the last page that holds a widget. It is how a
 * new page appears at all, now that nothing adds one by hand, and it is what takes the pages away
 * again when the widgets are dragged forward off them.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LauncherWidgetSparePageTest {

    @Test public void aPopulatedLastPageGainsOneSpareBehindIt() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertEquals(1, repository.pageCount());

        assertTrue(repository.trimSparePages());
        assertEquals("one empty page behind the widget", 2, repository.pageCount());
        assertTrue("and trimming again changes nothing", repository.trimSparePages());
        assertEquals(2, repository.pageCount());
    }

    @Test public void widgetsDraggedForwardTakeTheTrailingPagesWithThem() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertEquals(1, repository.addPage());
        assertEquals(2, repository.addPage());
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 1)));
        assertTrue(repository.putRecord(record(2, new WidgetCellRect(1, 0, 2, 1), 2)));
        assertEquals(3, repository.pageCount());

        // The user's own example: both widgets end up on the first page.
        assertTrue(repository.putRecords(java.util.Arrays.asList(
            repository.get(1).withPage(0),
            repository.get(2).withPage(0))));
        assertTrue(repository.trimSparePages());

        assertEquals("the third page goes, the second stays empty", 2, repository.pageCount());
        assertEquals(2, repository.recordsOnPage(0).size());
        assertTrue(repository.recordsOnPage(1).isEmpty());
    }

    @Test public void anEmptyPageInTheMiddleIsNeverTouched() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertEquals(1, repository.addPage());
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 1)));

        assertTrue(repository.trimSparePages());

        assertEquals("page 0 stays empty and page 2 is the new spare", 3, repository.pageCount());
        assertTrue(repository.recordsOnPage(0).isEmpty());
        assertEquals(1, repository.recordsOnPage(1).size());
        assertTrue(repository.recordsOnPage(2).isEmpty());
    }

    @Test public void aLayoutWithNoWidgetsAnywhereKeepsOnePage() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertEquals(1, repository.addPage());
        assertEquals(2, repository.addPage());

        assertTrue(repository.trimSparePages());

        assertEquals(1, repository.pageCount());
    }

    @Test public void removingTheLastWidgetOffAPageTakesThatPageBack() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertEquals(1, repository.addPage());
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertTrue(repository.putRecord(record(2, new WidgetCellRect(0, 0, 1, 1), 1)));
        assertTrue(repository.trimSparePages());
        assertEquals(3, repository.pageCount());

        assertTrue(repository.removeRecord(2));
        assertTrue(repository.trimSparePages());

        assertEquals(2, repository.pageCount());
    }

    @Test public void anAddInFlightKeepsThePageItReserved() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertEquals(1, repository.addPage());
        assertEquals(2, repository.addPage());
        assertTrue(repository.reservePending(repository.revision(), new WidgetAddTransaction(
            "token", 7, WidgetTestFixtures.PROVIDER, 0,
            WidgetAddTransaction.Stage.ALLOCATED, new WidgetCellRect(0, 0, 1, 1), 2,
            repository.revision(), null, new Bundle(), 0L)));

        assertTrue(repository.trimSparePages());

        assertEquals("the reservation's own page cannot be trimmed away",
            4, repository.pageCount());
    }

    @Test public void theTrimmedPageCountIsPersisted() throws Exception {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 0)));
        assertTrue(repository.trimSparePages());

        assertEquals(2, new JSONObject(storage.value).getInt("pages"));
        assertEquals(2, new LauncherWidgetRepository(storage).pageCount());
    }

    @Test public void setPageCountPutsTheCountBackForARestore() {
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        assertTrue(repository.setPageCount(4));
        assertEquals(4, repository.pageCount());
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 1, 1), 3)));

        assertFalse("a count that would strand a widget is refused", repository.setPageCount(2));
        assertEquals(4, repository.pageCount());
        assertFalse(repository.setPageCount(0));
    }

    private static LauncherWidgetRecord record(int id, WidgetCellRect cell, int page) {
        return new LauncherWidgetRecord(id, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.PROVIDER_MISSING, cell, page, new Bundle(), null);
    }
}
