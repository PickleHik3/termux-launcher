package com.termux.app.launcher.widget;

import android.content.ComponentName;
import android.os.Bundle;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class WidgetGridPlacementPolicyTest {
    private final WidgetGridDefinition grid = new WidgetGridDefinition(6, 4);

    @Test public void twoByTwoUsesFirstVisibleFreeRegionAndNeverMovesExisting() {
        List<LauncherWidgetRecord> records = Arrays.asList(record(1, 0, 0, 2, 1),
            record(2, 2, 0, 4, 2));
        WidgetGridPlacementPolicy.Result result = WidgetGridPlacementPolicy.findPlacement(grid,
            records, 2, 2);
        assertEquals(WidgetGridPlacementPolicy.Outcome.PLACED, result.outcome);
        assertEquals(new WidgetCellRect(0, 1, 2, 3), result.rect);
        assertEquals(new WidgetCellRect(0, 0, 2, 1), records.get(0).cell);
    }

    @Test public void narrowHolesAreSkippedAndNoncontiguousFullGridHasNoPlacement() {
        List<LauncherWidgetRecord> records = Arrays.asList(record(1, 0, 0, 1, 6),
            record(2, 2, 0, 3, 6));
        assertEquals(WidgetGridPlacementPolicy.Outcome.NO_CONTIGUOUS_SPACE,
            WidgetGridPlacementPolicy.findPlacement(grid, records, 2, 1).outcome);
    }

    @Test public void missingAndDeletingRecordsOccupyTheirCells() {
        List<LauncherWidgetRecord> records = Arrays.asList(
            record(1, LauncherWidgetRecord.State.PROVIDER_MISSING, 0, 0, 2, 2),
            record(2, LauncherWidgetRecord.State.DELETING, 2, 0, 4, 2));
        assertEquals(new WidgetCellRect(0, 2, 4, 3),
            WidgetGridPlacementPolicy.findPlacement(grid, records, 4, 1).rect);
    }

    @Test public void ignoredIdAllowsFutureMoveButNotOverlap() {
        List<LauncherWidgetRecord> records = Arrays.asList(record(1, 0, 0, 2, 2),
            record(2, 2, 0, 4, 2));
        assertTrue(WidgetGridPlacementPolicy.canPlace(grid, records,
            new WidgetCellRect(0, 2, 2, 4), 1));
        assertFalse(WidgetGridPlacementPolicy.canPlace(grid, records,
            new WidgetCellRect(2, 0, 4, 2), 1));
    }

    @Test public void invalidSnapshotAndOversizeAreTyped() {
        List<LauncherWidgetRecord> overlap = Arrays.asList(record(1, 0, 0, 2, 2),
            record(2, 1, 1, 2, 2));
        assertEquals(WidgetGridPlacementPolicy.Outcome.INVALID_SNAPSHOT,
            WidgetGridPlacementPolicy.findPlacement(grid, overlap, 1, 1).outcome);
        assertEquals(WidgetGridPlacementPolicy.Outcome.SPAN_EXCEEDS_GRID,
            WidgetGridPlacementPolicy.findPlacement(grid, new ArrayList<>(), 5, 1).outcome);
    }

    private static LauncherWidgetRecord record(int id, int l, int t, int r, int b) {
        return record(id, LauncherWidgetRecord.State.ACTIVE, l, t, r, b);
    }
    private static LauncherWidgetRecord record(int id, LauncherWidgetRecord.State state,
                                               int l, int t, int r, int b) {
        return new LauncherWidgetRecord(id, new ComponentName("pkg", "P" + id), 0, state,
            new WidgetCellRect(l, t, r, b), new Bundle(), null);
    }
}
