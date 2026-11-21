package com.termux.shared.termux.settings.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.LookGrid.Kind;
import com.termux.shared.termux.settings.preferences.LookGrid.Property;

import org.junit.Test;

public class LookGridMarginMigrationTest {

    private static LookGrid grid(LookGrid.MemoryStore store) {
        return new LookGrid(store);
    }

    @Test
    public void homesOwnMarginBecomesGlobalAndEveryOtherCellGoes() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        LookGrid grid = grid(store);
        grid.write(Property.MARGIN, null, 8);
        store.putInt(LookGrid.key(Property.MARGIN, null, Kind.HOME), 14);
        store.putInt(LookGrid.key(Property.MARGIN, null, Kind.TERMINAL), 20);
        store.putInt(LookGrid.key(Property.MARGIN, null, 3), 30);

        assertTrue(LookGridMarginMigration.fold(grid));

        assertEquals(14, grid.value(Property.MARGIN, null));
        assertFalse(store.contains(LookGrid.key(Property.MARGIN, null, Kind.HOME)));
        assertFalse(store.contains(LookGrid.key(Property.MARGIN, null, Kind.TERMINAL)));
        assertFalse(store.contains(LookGrid.key(Property.MARGIN, null, 3)));
    }

    @Test
    public void homeFollowingGlobalKeepsGlobalAndDropsTheTerminals() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        LookGrid grid = grid(store);
        grid.write(Property.MARGIN, null, 8);
        store.putInt(LookGrid.key(Property.MARGIN, null, Kind.TERMINAL), 20);

        assertTrue(LookGridMarginMigration.fold(grid));

        assertEquals(8, grid.value(Property.MARGIN, null));
        assertFalse(store.contains(LookGrid.key(Property.MARGIN, null, Kind.TERMINAL)));
    }

    @Test
    public void baseAloneChangesNothing() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        LookGrid grid = grid(store);
        grid.write(Property.MARGIN, null, 8);
        store.putInt(LookGrid.key(Property.OPACITY, null, Kind.HOME), 40);

        assertFalse(LookGridMarginMigration.fold(grid));

        assertEquals(8, grid.value(Property.MARGIN, null));
        assertTrue("other properties keep their kind cells",
            store.contains(LookGrid.key(Property.OPACITY, null, Kind.HOME)));
    }

    @Test
    public void runsOnce() {
        LookGrid.MemoryStore store = new LookGrid.MemoryStore();
        LookGrid grid = grid(store);
        store.putInt(LookGrid.key(Property.MARGIN, null, Kind.HOME), 14);
        LookGridMarginMigration.run(store);
        assertTrue(store.getBoolean(LookGridMarginMigration.KEY_MIGRATED, false));

        store.putInt(LookGrid.key(Property.MARGIN, null, Kind.HOME), 22);
        assertFalse(LookGridMarginMigration.run(store));
        assertTrue(store.contains(LookGrid.key(Property.MARGIN, null, Kind.HOME)));
    }
}
