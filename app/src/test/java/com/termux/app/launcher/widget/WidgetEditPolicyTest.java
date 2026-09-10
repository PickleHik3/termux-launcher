package com.termux.app.launcher.widget;

import android.graphics.Rect;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class WidgetEditPolicyTest {
    private static final WidgetGridDefinition GRID = new WidgetGridDefinition(4, 4);

    private static WidgetGridMetrics metrics() {
        return new WidgetGridMetrics(new Rect(0, 0, 400, 400), 0, 0, 0, GRID, false);
    }

    private static LauncherWidgetRecord record(int id, WidgetCellRect cell) {
        return new LauncherWidgetRecord(id, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, cell, null, null);
    }

    @Test public void snapMoveFindsNearestFreePlacement() {
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 0, 2, 1)));
        // Dragged near cell column 2 row 2 (each cell is 100px).
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.snapMove(metrics(), records, 1,
            new WidgetCellRect(0, 0, 2, 1), new Rect(190, 195, 390, 295));
        assertTrue(candidate.valid);
        assertEquals(new WidgetCellRect(2, 2, 4, 3), candidate.rect);
    }

    @Test public void snapMoveIgnoresOwnFootprint() {
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 0, 2, 1)));
        // Dragged barely off its own spot: snapping back onto itself must be valid.
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.snapMove(metrics(), records, 1,
            new WidgetCellRect(0, 0, 2, 1), new Rect(10, 5, 210, 105));
        assertTrue(candidate.valid);
        assertEquals(new WidgetCellRect(0, 0, 2, 1), candidate.rect);
    }

    @Test public void snapMoveSkipsOccupiedCells() {
        // A full page: the 2x2 blocker under the finger has nowhere to go, so the old
        // nearest-free behaviour stands and nobody is displaced.
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 3, 1, 4)));
        records.add(record(2, new WidgetCellRect(1, 0, 3, 2)));
        records.add(record(3, new WidgetCellRect(0, 0, 1, 2)));
        records.add(record(4, new WidgetCellRect(3, 0, 4, 2)));
        records.add(record(5, new WidgetCellRect(0, 2, 4, 3)));
        records.add(record(6, new WidgetCellRect(1, 3, 4, 4)));
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.snapMove(metrics(), records, 1,
            new WidgetCellRect(0, 3, 1, 4), new Rect(100, 0, 200, 100));
        assertTrue(candidate.valid);
        assertNotEquals(new WidgetCellRect(1, 0, 2, 1), candidate.rect);
        assertEquals(new WidgetCellRect(0, 3, 1, 4), candidate.rect);
        assertTrue(candidate.displaced.isEmpty());
    }

    @Test public void snapMoveDisplacesTheWidgetUnderTheFinger() {
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 0, 1, 1)));
        records.add(record(2, new WidgetCellRect(2, 2, 3, 3)));
        // Dropped exactly on widget 2: the finger wins and widget 2 steps aside.
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.snapMove(metrics(), records, 1,
            new WidgetCellRect(0, 0, 1, 1), new Rect(200, 200, 300, 300));
        assertTrue(candidate.valid);
        assertEquals(new WidgetCellRect(2, 2, 3, 3), candidate.rect);
        assertEquals(1, candidate.displaced.size());
        assertEquals(new WidgetCellRect(2, 1, 3, 2), candidate.displaced.get(2));
        assertLayoutValid(records, 1, candidate);
    }

    @Test public void displacedBlockerTakesTheFreeSpotNearestItsOwnCell() {
        // Only one hole on the page, one cell along from the blocker; it must find it.
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(3, 3, 4, 4)));
        records.add(record(2, new WidgetCellRect(0, 0, 1, 1)));
        records.add(record(3, new WidgetCellRect(2, 0, 4, 1)));
        records.add(record(4, new WidgetCellRect(0, 1, 4, 2)));
        records.add(record(5, new WidgetCellRect(0, 2, 4, 3)));
        records.add(record(6, new WidgetCellRect(0, 3, 3, 4)));
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.snapMove(metrics(), records, 1,
            new WidgetCellRect(3, 3, 4, 4), new Rect(0, 0, 100, 100));
        assertTrue(candidate.valid);
        assertEquals(new WidgetCellRect(0, 0, 1, 1), candidate.rect);
        assertEquals(new WidgetCellRect(1, 0, 2, 1), candidate.displaced.get(2));
        assertLayoutValid(records, 1, candidate);
    }

    @Test public void snapMoveDisplacesTwoBlockersAtOnce() {
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 3, 2, 4)));
        records.add(record(2, new WidgetCellRect(1, 1, 2, 2)));
        records.add(record(3, new WidgetCellRect(2, 1, 3, 2)));
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.snapMove(metrics(), records, 1,
            new WidgetCellRect(0, 3, 2, 4), new Rect(100, 100, 300, 200));
        assertTrue(candidate.valid);
        assertEquals(new WidgetCellRect(1, 1, 3, 2), candidate.rect);
        assertEquals(2, candidate.displaced.size());
        assertEquals(new WidgetCellRect(1, 0, 2, 1), candidate.displaced.get(2));
        assertEquals(new WidgetCellRect(2, 0, 3, 1), candidate.displaced.get(3));
        assertLayoutValid(records, 1, candidate);
    }

    @Test public void snapMoveFallsBackWhenTheBlockerFitsNowhereElse() {
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 0, 1, 1)));
        records.add(record(2, new WidgetCellRect(1, 1, 4, 4)));
        // The 3x3 blocker cannot move without covering the target, so nothing moves.
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.snapMove(metrics(), records, 1,
            new WidgetCellRect(0, 0, 1, 1), new Rect(200, 200, 300, 300));
        assertTrue(candidate.valid);
        assertEquals(new WidgetCellRect(2, 0, 3, 1), candidate.rect);
        assertTrue(candidate.displaced.isEmpty());
        assertLayoutValid(records, 1, candidate);
    }

    @Test public void snapMoveNeverTreatsTheDraggedWidgetAsItsOwnBlocker() {
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 0, 2, 2)));
        records.add(record(2, new WidgetCellRect(2, 0, 4, 2)));
        // The target overlaps the dragged widget's own cells and its neighbour's.
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.snapMove(metrics(), records, 1,
            new WidgetCellRect(0, 0, 2, 2), new Rect(100, 0, 300, 200));
        assertTrue(candidate.valid);
        assertEquals(new WidgetCellRect(1, 0, 3, 2), candidate.rect);
        assertEquals(1, candidate.displaced.size());
        assertNull(candidate.displaced.get(1));
        assertEquals(new WidgetCellRect(2, 2, 4, 4), candidate.displaced.get(2));
        assertLayoutValid(records, 1, candidate);
    }

    @Test public void snapMoveOntoFreeSpaceDisplacesNobody() {
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 0, 2, 1)));
        records.add(record(2, new WidgetCellRect(0, 1, 2, 2)));
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.snapMove(metrics(), records, 1,
            new WidgetCellRect(0, 0, 2, 1), new Rect(190, 195, 390, 295));
        assertTrue(candidate.valid);
        assertEquals(new WidgetCellRect(2, 2, 4, 3), candidate.rect);
        assertTrue(candidate.displaced.isEmpty());
        assertEquals(new WidgetCellRect(0, 1, 2, 2), records.get(1).cell);
    }

    @Test public void resizeNeverDisplacesNeighbours() {
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 0, 1, 1)));
        records.add(record(2, new WidgetCellRect(2, 0, 3, 1)));
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.resize(metrics(), records, 1,
            new WidgetCellRect(0, 0, 1, 1), WidgetEditPolicy.Handle.RIGHT, 400, 1, 1);
        assertEquals(new WidgetCellRect(0, 0, 2, 1), candidate.rect);
        assertTrue(candidate.displaced.isEmpty());
    }

    /** The whole plan applied at once must be a layout the repository would accept. */
    private static void assertLayoutValid(List<LauncherWidgetRecord> records, int draggedId,
                                          WidgetEditPolicy.Candidate candidate) {
        List<LauncherWidgetRecord> applied = new ArrayList<>();
        for (LauncherWidgetRecord record : records) {
            if (record.appWidgetId == draggedId) {
                applied.add(record.withCell(candidate.rect));
                continue;
            }
            WidgetCellRect moved = candidate.displaced.get(record.appWidgetId);
            applied.add(moved == null ? record : record.withCell(moved));
        }
        assertTrue(WidgetGridPlacementPolicy.validate(GRID, applied));
    }

    @Test public void snapMoveWithFullGridIsInvalid() {
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 0, 4, 4)));
        records = Collections.singletonList(records.get(0));
        // A second 4x4 widget can never place anywhere (the only spot ignores id 1, so use id 2
        // spanning the whole grid against a full occupancy owned by id 1).
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.snapMove(metrics(), records, 2,
            new WidgetCellRect(0, 0, 4, 4), new Rect(0, 0, 400, 400));
        assertFalse(candidate.valid);
    }

    @Test public void resizeGrowsTowardDesiredEdgeInWholeCells() {
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 0, 1, 1)));
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.resize(metrics(), records, 1,
            new WidgetCellRect(0, 0, 1, 1), WidgetEditPolicy.Handle.RIGHT, 305, 1, 1);
        assertTrue(candidate.valid);
        assertEquals(new WidgetCellRect(0, 0, 3, 1), candidate.rect);
    }

    @Test public void resizeStopsAtNeighborCollision() {
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 0, 1, 1)));
        records.add(record(2, new WidgetCellRect(2, 0, 3, 1)));
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.resize(metrics(), records, 1,
            new WidgetCellRect(0, 0, 1, 1), WidgetEditPolicy.Handle.RIGHT, 400, 1, 1);
        assertEquals(new WidgetCellRect(0, 0, 2, 1), candidate.rect);
    }

    @Test public void resizeRespectsMinimumSpan() {
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 0, 3, 1)));
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.resize(metrics(), records, 1,
            new WidgetCellRect(0, 0, 3, 1), WidgetEditPolicy.Handle.RIGHT, 0, 2, 1);
        assertEquals(new WidgetCellRect(0, 0, 2, 1), candidate.rect);
    }

    @Test public void resizeLeftEdgeMovesLeftBoundary() {
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(2, 0, 4, 1)));
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.resize(metrics(), records, 1,
            new WidgetCellRect(2, 0, 4, 1), WidgetEditPolicy.Handle.LEFT, 0, 1, 1);
        assertEquals(new WidgetCellRect(0, 0, 4, 1), candidate.rect);
    }

    @Test public void resizeVerticalBottomEdge() {
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 0, 1, 1)));
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.resize(metrics(), records, 1,
            new WidgetCellRect(0, 0, 1, 1), WidgetEditPolicy.Handle.BOTTOM, 400, 1, 1);
        assertEquals(new WidgetCellRect(0, 0, 1, 4), candidate.rect);
    }
}
