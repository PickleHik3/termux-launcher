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
        List<LauncherWidgetRecord> records = new ArrayList<>();
        records.add(record(1, new WidgetCellRect(0, 0, 1, 1)));
        records.add(record(2, new WidgetCellRect(1, 0, 2, 1)));
        // Dragged directly over widget 2's cell; nearest free placement wins instead.
        WidgetEditPolicy.Candidate candidate = WidgetEditPolicy.snapMove(metrics(), records, 1,
            new WidgetCellRect(0, 0, 1, 1), new Rect(100, 0, 200, 100));
        assertTrue(candidate.valid);
        assertNotEquals(new WidgetCellRect(1, 0, 2, 1), candidate.rect);
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
