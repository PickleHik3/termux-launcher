package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

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

    private static LauncherWidgetRecord record(int id, WidgetCellRect cell, int page) {
        return new LauncherWidgetRecord(id, new ComponentName("pkg", "P" + id), 0,
            LauncherWidgetRecord.State.ACTIVE, cell, page, new Bundle(), null);
    }
}
