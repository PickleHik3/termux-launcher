package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
 * Shrinking the grid by rows (the always-on keyboard's room) and giving them back: a snapshot of
 * the wall is replayed when nothing was edited, and dropped, leaving the rows empty, when
 * something was.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LauncherWidgetRepositoryRowSnapshotTest {

    private final WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
    private final LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);

    /** On the default 5x4 grid: a wide widget on top, a corner one at the bottom, a full-page one. */
    private void seed() {
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 4, 2), 0)));
        assertTrue(repository.putRecord(record(2, new WidgetCellRect(2, 3, 4, 5), 0)));
        assertTrue(repository.addPage() == 1);
        assertTrue(repository.putRecord(record(3, new WidgetCellRect(0, 0, 4, 5), 1)));
    }

    @Test public void shrinkReportsWhatMovedAndShrank() {
        seed();

        WidgetReflowReport report = repository.shrinkGridRows(2);

        assertNotNull(report);
        assertEquals(new WidgetGridDefinition(3, 4), repository.gridDefinition());
        assertEquals(2, report.movedCount());
        assertEquals(1, report.shrunkCount());
        WidgetReflowReport.Entry corner = entry(report, 2);
        assertEquals(new WidgetCellRect(2, 3, 4, 5), corner.fromCell);
        // Page 0 keeps only a one-row strip under the wide widget: no room for a 2x2, so it
        // goes onto a new page after the existing ones.
        assertEquals(new WidgetCellRect(0, 0, 2, 2), corner.toCell);
        assertEquals(2, corner.toPage);
        assertFalse(corner.shrank());
        WidgetReflowReport.Entry tall = entry(report, 3);
        assertEquals(new WidgetCellRect(0, 0, 4, 3), tall.toCell);
        assertTrue(tall.shrank());
    }

    @Test public void aWidgetThatFindsNoRoomGoesToANewPageAndIsReported() {
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 4, 3), 0)));
        assertTrue(repository.putRecord(record(2, new WidgetCellRect(0, 3, 4, 5), 0)));

        WidgetReflowReport report = repository.shrinkGridRows(3);

        assertNotNull(report);
        assertEquals(2, repository.pageCount());
        WidgetReflowReport.Entry sent = entry(report, 2);
        assertEquals(0, sent.fromPage);
        assertEquals(1, sent.toPage);
        assertTrue(sent.changedPage());
        assertEquals(1, report.pageChangeCount());
    }

    @Test public void restoreWithNoEditReplaysTheSnapshotExactly() {
        seed();
        String before = repository.serialize();
        assertNotNull(repository.shrinkGridRows(2));

        assertTrue(repository.hasGridRowSnapshot());
        assertTrue(repository.restoreGridRows());

        assertFalse(repository.hasGridRowSnapshot());
        assertEquals(new WidgetGridDefinition(5, 4), repository.gridDefinition());
        assertEquals(new WidgetCellRect(0, 0, 4, 2), repository.get(1).cell);
        assertEquals(new WidgetCellRect(2, 3, 4, 5), repository.get(2).cell);
        assertEquals(new WidgetCellRect(0, 0, 4, 5), repository.get(3).cell);
        assertEquals(2, repository.pageCount());
        assertEquals(1, repository.get(3).page);
        assertEquals(stripRevision(before), stripRevision(repository.serialize()));
    }

    @Test public void restoreBringsBackPagesTheShrinkAdded() {
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 4, 3), 0)));
        assertTrue(repository.putRecord(record(2, new WidgetCellRect(0, 3, 4, 5), 0)));
        assertNotNull(repository.shrinkGridRows(3));
        assertEquals(2, repository.pageCount());

        assertTrue(repository.restoreGridRows());

        assertEquals(1, repository.pageCount());
        assertEquals(0, repository.get(2).page);
        assertEquals(new WidgetCellRect(0, 3, 4, 5), repository.get(2).cell);
    }

    @Test public void anEditDropsTheSnapshotAndOnlyGrowsTheGrid() {
        seed();
        assertNotNull(repository.shrinkGridRows(2));
        // The user moves a widget on the shrunk grid.
        assertTrue(repository.putRecord(repository.get(2).withCell(new WidgetCellRect(0, 2, 2, 3))));

        assertFalse(repository.restoreGridRows());

        assertFalse(repository.hasGridRowSnapshot());
        assertEquals(new WidgetGridDefinition(5, 4), repository.gridDefinition());
        assertEquals(new WidgetCellRect(0, 2, 2, 3), repository.get(2).cell);
        assertEquals(new WidgetCellRect(0, 0, 4, 3), repository.get(3).cell);
    }

    @Test public void everyKindOfEditCountsAsDirty() {
        // add / remove / resize / page add each leave the wall unrestorable.
        java.util.function.Consumer<LauncherWidgetRepository>[] edits = new java.util.function.Consumer[] {
            (java.util.function.Consumer<LauncherWidgetRepository>) r ->
                assertTrue(r.putRecord(record(9, new WidgetCellRect(0, 2, 1, 3), 0))),
            (java.util.function.Consumer<LauncherWidgetRepository>) r ->
                assertTrue(r.removeRecord(1)),
            (java.util.function.Consumer<LauncherWidgetRepository>) r ->
                assertTrue(r.putRecord(r.get(2).withCell(new WidgetCellRect(2, 1, 3, 3)))),
            (java.util.function.Consumer<LauncherWidgetRepository>) r ->
                assertTrue(r.addFreshPage() >= 0),
        };
        for (java.util.function.Consumer<LauncherWidgetRepository> edit : edits) {
            LauncherWidgetRepository r =
                new LauncherWidgetRepository(new WidgetTestFixtures.Memory());
            assertTrue(r.putRecord(record(1, new WidgetCellRect(0, 0, 4, 2), 0)));
            assertTrue(r.putRecord(record(2, new WidgetCellRect(2, 3, 4, 5), 0)));
            assertNotNull(r.shrinkGridRows(2));
            edit.accept(r);
            assertFalse(r.restoreGridRows());
            assertFalse(r.hasGridRowSnapshot());
            assertEquals(new WidgetGridDefinition(5, 4), r.gridDefinition());
        }
    }

    @Test public void removingAPageAfterTheShrinkIsAnEdit() {
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 4, 2), 0)));
        assertTrue(repository.addFreshPage() == 1);
        assertNotNull(repository.shrinkGridRows(1));
        assertTrue(repository.removePage(1));

        assertFalse(repository.restoreGridRows());
        assertEquals(1, repository.pageCount());
    }

    @Test public void theSnapshotSurvivesARestart() {
        seed();
        assertNotNull(repository.shrinkGridRows(2));

        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(storage);
        assertTrue(reloaded.hasGridRowSnapshot());
        assertTrue(reloaded.restoreGridRows());
        assertEquals(new WidgetCellRect(2, 3, 4, 5), reloaded.get(2).cell);
    }

    @Test public void theDirtyStateSurvivesARestart() {
        seed();
        assertNotNull(repository.shrinkGridRows(2));
        assertTrue(repository.putRecord(repository.get(2).withCell(new WidgetCellRect(0, 2, 2, 3))));

        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(storage);
        assertFalse(reloaded.restoreGridRows());
        assertEquals(new WidgetGridDefinition(5, 4), reloaded.gridDefinition());
        assertEquals(new WidgetCellRect(0, 2, 2, 3), reloaded.get(2).cell);
    }

    @Test public void shrinkIsRefusedWhileAnAddIsPendingAndChangesNothing() {
        seed();
        assertTrue(repository.setPending(new WidgetAddTransaction("t", 7,
            new ComponentName("pkg", "P7"), 0, WidgetAddTransaction.Stage.ALLOCATED,
            new WidgetCellRect(0, 2, 1, 3), 0, repository.revision(), null, new Bundle(), 1L)));
        String before = repository.serialize();

        assertNull(repository.shrinkGridRows(2));

        assertEquals(before, repository.serialize());
        assertFalse(repository.hasGridRowSnapshot());
    }

    @Test public void aShrinkThatLeavesNoRowsIsRefused() {
        seed();
        assertNull(repository.shrinkGridRows(5));
        assertNull(repository.shrinkGridRows(0));
        assertEquals(new WidgetGridDefinition(5, 4), repository.gridDefinition());
    }

    @Test public void twoShrinksWithoutAnEditRestoreInOneStep() {
        seed();
        assertNotNull(repository.shrinkGridRows(1));
        assertNotNull(repository.shrinkGridRows(1));

        assertTrue(repository.restoreGridRows());

        assertEquals(new WidgetGridDefinition(5, 4), repository.gridDefinition());
        assertEquals(new WidgetCellRect(2, 3, 4, 5), repository.get(2).cell);
    }

    @Test public void anOrientationGridChangeNeitherReadsNorWritesTheSnapshot() {
        seed();
        assertNotNull(repository.shrinkGridRows(2));
        String held = snapshotJson(repository.serialize());

        assertTrue(repository.setGridDefinition(new WidgetGridDefinition(3, 5)));

        assertEquals(held, snapshotJson(repository.serialize()));
        assertTrue(repository.hasGridRowSnapshot());
        // The grid differs from the one the shrink left, so this reads as an edit: no replay.
        assertFalse(repository.restoreGridRows());
    }

    @Test public void restoreWithoutASnapshotChangesNothing() {
        seed();
        String before = repository.serialize();

        assertFalse(repository.restoreGridRows());

        assertEquals(before, repository.serialize());
    }

    private static WidgetReflowReport.Entry entry(WidgetReflowReport report, int id) {
        for (WidgetReflowReport.Entry entry : report.entries()) {
            if (entry.appWidgetId == id) return entry;
        }
        throw new AssertionError("no entry for " + id);
    }

    private static String stripRevision(String payload) {
        return payload.replaceAll("\"revision\":\\d+", "");
    }

    private static String snapshotJson(String payload) {
        int at = payload.indexOf("\"rowSnapshot\"");
        assertTrue(at >= 0);
        return payload.substring(at, payload.indexOf("\"records\"", at));
    }

    private static LauncherWidgetRecord record(int id, WidgetCellRect cell, int page) {
        return new LauncherWidgetRecord(id, new ComponentName("pkg", "P" + id), 0,
            LauncherWidgetRecord.State.ACTIVE, cell, page, new Bundle(), null);
    }
}
