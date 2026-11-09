package com.termux.shared.termux.settings.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.LookGrid.Kind;
import com.termux.shared.termux.settings.preferences.LookGrid.Property;

import org.junit.Test;

public class LookGridMarginMigrationTest {

    private static LookGrid grid(LookGrid.MemoryStore store) {
        return new LookGrid(store, null, null);
    }

    @Test
    public void homesOwnMarginBecomesBaseAndEveryOtherCellGoes() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        LookGrid grid = grid(store);
        grid.write(Property.MARGIN, null, LookGrid.NO_PAGE, 8);
        grid.write(Property.MARGIN, null, Kind.HOME, 14);
        grid.write(Property.MARGIN, null, Kind.TERMINAL, 20);
        store.putInt(LookGrid.key(Property.MARGIN, null, 3), 30);

        assertTrue(LookGridMarginMigration.fold(grid));

        assertEquals(14, grid.value(Property.MARGIN, null, LookGrid.NO_PAGE));
        assertFalse(grid.has(Property.MARGIN, null, Kind.HOME));
        assertFalse(grid.has(Property.MARGIN, null, Kind.TERMINAL));
        assertFalse(store.contains(LookGrid.key(Property.MARGIN, null, 3)));
        assertEquals("every kind reads Base now", 14,
            grid.value(Property.MARGIN, null, Kind.TERMINAL));
    }

    @Test
    public void homeFollowingBaseKeepsBaseAndDropsTheWorkspaces() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        LookGrid grid = grid(store);
        grid.write(Property.MARGIN, null, LookGrid.NO_PAGE, 8);
        grid.write(Property.MARGIN, null, Kind.TERMINAL, 20);

        assertTrue(LookGridMarginMigration.fold(grid));

        assertEquals(8, grid.value(Property.MARGIN, null, LookGrid.NO_PAGE));
        assertFalse(grid.has(Property.MARGIN, null, Kind.TERMINAL));
    }

    @Test
    public void baseAloneChangesNothing() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        LookGrid grid = grid(store);
        grid.write(Property.MARGIN, null, LookGrid.NO_PAGE, 8);
        grid.write(Property.OPACITY, null, Kind.HOME, 40);

        assertFalse(LookGridMarginMigration.fold(grid));

        assertEquals(8, grid.value(Property.MARGIN, null, LookGrid.NO_PAGE));
        assertTrue("other properties keep their kind cells",
            grid.has(Property.OPACITY, null, Kind.HOME));
    }

    @Test
    public void runsOnce() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        LookGrid grid = grid(store);
        grid.write(Property.MARGIN, null, Kind.HOME, 14);
        LookGridMarginMigration.run(store);
        assertTrue(store.getBoolean(LookGridMarginMigration.KEY_MIGRATED, false));

        grid.write(Property.MARGIN, null, Kind.HOME, 22);
        assertFalse(LookGridMarginMigration.run(store));
        assertTrue(grid.has(Property.MARGIN, null, Kind.HOME));
    }
}
