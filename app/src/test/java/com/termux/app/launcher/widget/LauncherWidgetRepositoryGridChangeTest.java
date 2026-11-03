package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Changing the grid's dimensions from Settings. Widgets keep their place while it still exists;
 * a widget the smaller grid cannot hold is shrunk and moved, onto a new page if need be, and
 * never dropped.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LauncherWidgetRepositoryGridChangeTest {

    @Test public void growingTheGridKeepsEveryCellWhereItWas() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 4, 2), 0)));
        assertTrue(repository.putRecord(record(2, new WidgetCellRect(1, 3, 3, 5), 0)));

        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(8, 6)));

        assertEquals(new WidgetGridDefinition(8, 6), repository.gridDefinition());
        assertEquals(new WidgetCellRect(0, 0, 4, 2), repository.get(1).cell);
        assertEquals(new WidgetCellRect(1, 3, 3, 5), repository.get(2).cell);
        // Persisted: a reload sees the new grid.
        assertEquals(new WidgetGridDefinition(8, 6),
            new LauncherWidgetRepository(storage).gridDefinition());
    }

    @Test public void shrinkingTheGridShrinksAndMovesWhatNoLongerFits() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        // A full-width 4x2 at the top and a 2x2 in the bottom-right corner of the 5x4 grid.
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 4, 2), 0)));
        assertTrue(repository.putRecord(record(2, new WidgetCellRect(2, 3, 4, 5), 0)));

        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(4, 3)));

        // The wide one loses a column; the corner one slides in to the new corner.
        assertEquals(new WidgetCellRect(0, 0, 3, 2), repository.get(1).cell);
        assertEquals(new WidgetCellRect(1, 2, 3, 4), repository.get(2).cell);
        assertEquals(0, repository.get(1).page);
        assertEquals(0, repository.get(2).page);
        assertEquals(1, repository.pageCount());
    }

    @Test public void aWidgetWithNoRoomLeftOnItsPageGoesOntoANewPage() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 4, 3), 0)));
        assertTrue(repository.putRecord(record(2, new WidgetCellRect(0, 3, 4, 5), 0)));

        // Two rows: the first widget fills both; the second has nowhere to go on page 0.
        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(2, 4)));

        assertEquals(new WidgetCellRect(0, 0, 4, 2), repository.get(1).cell);
        assertEquals(0, repository.get(1).page);
        assertEquals(new WidgetCellRect(0, 0, 4, 2), repository.get(2).cell);
        assertEquals(1, repository.get(2).page);
        assertEquals(2, repository.pageCount());
    }

    @Test public void refusedWhileAnAddIsInFlight() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertTrue(repository.setPending(new WidgetAddTransaction("token", 7,
            new ComponentName("pkg", "Provider"), 0, WidgetAddTransaction.Stage.ALLOCATED,
            new Bundle(), 10)));

        assertFalse(repository.setGridDefinition(new WidgetGridDefinition(6, 6)));
        assertEquals(WidgetGridDefinition.DEFAULT, repository.gridDefinition());
        // The same grid is always fine to "adopt".
        assertTrue(repository.setGridDefinition(WidgetGridDefinition.DEFAULT));
    }

    /**
     * Pong's wall, 2026-11-02: 6 x 8 with eight widgets on Home pages 1 and 8. Turned, the caps
     * cut it to five columns and the reflow moved widgets onto new pages (17 and 18); turned
     * back, every move stayed. A cut by the caps is now undone exactly when the caps grow back:
     * the same cells, the same pages, the same page list, the added pages gone again.
     */
    @Test public void aGridTheCapsCutComesBackExactlyWhenTheCapsGrowBack() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        Owner owner = new Owner(17, 1, 8);
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage, owner);
        WidgetGridDefinition full = new WidgetGridDefinition(8, 6);
        assertTrue(repository.setGridDefinition(full));
        assertTrue(repository.putRecords(pongWall()));
        Map<Integer, String> start = places(repository);
        assertEquals(Arrays.asList(1, 8), repository.pageIds());

        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(8, 5), true));
        assertEquals("the cut reflows as before", new WidgetGridDefinition(8, 5),
            repository.gridDefinition());
        assertTrue("onto new pages", repository.pageIds().size() > 2);
        assertTrue(repository.hasGridCapSnapshot());

        assertTrue(repository.setGridDefinition(full, true));
        assertEquals(full, repository.gridDefinition());
        assertEquals(Arrays.asList(1, 8), repository.pageIds());
        assertEquals(Arrays.asList(1, 8), owner.pages);
        assertEquals(start, places(repository));
        assertFalse(repository.hasGridCapSnapshot());
    }

    /** The kept wall is saved with the cut, so a restart while turned still gets it back. */
    @Test public void aCapCutSurvivesARestart() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        Owner owner = new Owner(17, 1, 8);
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage, owner);
        WidgetGridDefinition full = new WidgetGridDefinition(8, 6);
        assertTrue(repository.setGridDefinition(full));
        assertTrue(repository.putRecords(pongWall()));
        Map<Integer, String> start = places(repository);
        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(8, 5), true));

        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(storage, owner);
        assertTrue(reloaded.hasGridCapSnapshot());
        assertTrue(reloaded.setGridDefinition(full, true));
        assertEquals(Arrays.asList(1, 8), reloaded.pageIds());
        assertEquals(start, places(reloaded));
    }

    /** Two cuts in a row are undone by the one return to the grid before the first. */
    @Test public void aSecondCutWidensTheFirst() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        Owner owner = new Owner(17, 1, 8);
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage, owner);
        WidgetGridDefinition full = new WidgetGridDefinition(8, 6);
        assertTrue(repository.setGridDefinition(full));
        assertTrue(repository.putRecords(pongWall()));
        Map<Integer, String> start = places(repository);
        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(8, 5), true));
        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(7, 4), true));
        assertTrue(repository.setGridDefinition(full, true));
        assertEquals(Arrays.asList(1, 8), repository.pageIds());
        assertEquals(start, places(repository));
    }

    /** The user's own smaller grid in Settings reflows and stays, as before. */
    @Test public void theUsersOwnCutStays() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        Owner owner = new Owner(17, 1, 8);
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage, owner);
        WidgetGridDefinition full = new WidgetGridDefinition(8, 6);
        assertTrue(repository.setGridDefinition(full));
        assertTrue(repository.putRecords(pongWall()));
        Map<Integer, String> start = places(repository);
        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(8, 5)));
        assertFalse(repository.hasGridCapSnapshot());
        Map<Integer, String> cut = places(repository);
        assertTrue(repository.setGridDefinition(full));
        assertEquals(cut, places(repository));
        assertFalse(start.equals(places(repository)));
    }

    /** An edit after the cut stands: the caps growing back only grow the grid. */
    @Test public void anEditAfterTheCutIsKept() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        Owner owner = new Owner(17, 1, 8);
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage, owner);
        WidgetGridDefinition full = new WidgetGridDefinition(8, 6);
        assertTrue(repository.setGridDefinition(full));
        assertTrue(repository.putRecords(pongWall()));
        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(8, 5), true));
        assertTrue(repository.removeRecord(107));
        Map<Integer, String> edited = places(repository);
        assertTrue(repository.setGridDefinition(full, true));
        assertEquals(full, repository.gridDefinition());
        assertEquals(edited, places(repository));
        assertFalse(repository.hasGridCapSnapshot());
    }

    /** Pong's eight built-ins as cells (left, top, right, bottom) on pages 1 and 8 by index. */
    private static List<LauncherWidgetRecord> pongWall() {
        return Arrays.asList(
            record(101, new WidgetCellRect(0, 0, 3, 2), 0),   // clock
            record(102, new WidgetCellRect(3, 0, 6, 2), 0),   // calendar
            record(103, new WidgetCellRect(0, 2, 3, 5), 0),   // system
            record(104, new WidgetCellRect(3, 2, 6, 5), 0),   // notifications
            record(105, new WidgetCellRect(0, 5, 6, 7), 0),   // media
            record(106, new WidgetCellRect(0, 0, 6, 2), 1),
            record(107, new WidgetCellRect(0, 2, 3, 4), 1),
            record(108, new WidgetCellRect(1, 4, 6, 6), 1));  // weather
    }

    /** Every widget's page id and cell, by widget id. */
    private static Map<Integer, String> places(LauncherWidgetRepository repository) {
        LinkedHashMap<Integer, String> out = new LinkedHashMap<>();
        for (LauncherWidgetRecord record : repository.records()) {
            out.put(record.appWidgetId, repository.pageIdAt(record.page) + " "
                + record.cell.left + "," + record.cell.top + ","
                + record.cell.right + "," + record.cell.bottom);
        }
        return out;
    }

    /** The Home pages as ids in order; ids handed out from {@code next}. */
    private static final class Owner implements LauncherWidgetRepository.HomePageOwner {
        final List<Integer> pages = new ArrayList<>();
        int next;
        Owner(int next, Integer... ids) { this.next = next; pages.addAll(Arrays.asList(ids)); }
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

    private static LauncherWidgetRecord record(int id, WidgetCellRect cell, int page) {
        return new LauncherWidgetRecord(id, new ComponentName("pkg", "P" + id), 0,
            LauncherWidgetRecord.State.ACTIVE, cell, page, new Bundle(), null);
    }
}
