package com.termux.app.dock;

import static org.junit.Assert.assertEquals;

import com.termux.app.dock.TaskbarHomeTilePolicy.Position;

import org.junit.Test;

/**
 * The Taskbar's movable Home tile: its slot at the start, middle or end for any count of apps,
 * the apps keeping their order around it, the drop in thirds, and the stored value read back.
 */
public class TaskbarHomeTilePolicyTest {

    @Test
    public void homeSlot_startMiddleEnd() {
        assertEquals(0, TaskbarHomeTilePolicy.homeSlot(Position.START, 4));
        assertEquals(2, TaskbarHomeTilePolicy.homeSlot(Position.MIDDLE, 4));
        assertEquals(4, TaskbarHomeTilePolicy.homeSlot(Position.END, 4));
        // Three apps: the extra one stands after the tile.
        assertEquals(1, TaskbarHomeTilePolicy.homeSlot(Position.MIDDLE, 3));
        assertEquals(0, TaskbarHomeTilePolicy.homeSlot(Position.MIDDLE, 0));
        assertEquals(0, TaskbarHomeTilePolicy.homeSlot(Position.END, -2));
    }

    @Test
    public void appsKeepTheirOrderAroundTheTile() {
        for (Position position : Position.values()) {
            for (int apps = 0; apps <= 6; apps++) {
                int expected = 0;
                int home = TaskbarHomeTilePolicy.homeSlot(position, apps);
                for (int slot = 0; slot <= apps; slot++) {
                    int app = TaskbarHomeTilePolicy.appAt(slot, position, apps);
                    if (slot == home) {
                        assertEquals(-1, app);
                        continue;
                    }
                    assertEquals(position + " " + apps, expected, app);
                    assertEquals(slot, TaskbarHomeTilePolicy.slotOf(app, position, apps));
                    expected++;
                }
                assertEquals(apps, expected);
            }
        }
    }

    @Test
    public void slotsOutsideTheRow_holdNothing() {
        assertEquals(-1, TaskbarHomeTilePolicy.appAt(-1, Position.START, 4));
        assertEquals(-1, TaskbarHomeTilePolicy.appAt(5, Position.START, 4));
        assertEquals(-1, TaskbarHomeTilePolicy.slotOf(4, Position.START, 4));
        assertEquals(-1, TaskbarHomeTilePolicy.slotOf(-1, Position.END, 4));
    }

    @Test
    public void appSlots_leaveOneForTheTile() {
        assertEquals(4, TaskbarHomeTilePolicy.appSlots(5));
        assertEquals(0, TaskbarHomeTilePolicy.appSlots(0));
    }

    @Test
    public void dropAt_readsTheRowInThirds() {
        assertEquals(Position.START, TaskbarHomeTilePolicy.dropAt(-0.2f));
        assertEquals(Position.START, TaskbarHomeTilePolicy.dropAt(0.3f));
        assertEquals(Position.MIDDLE, TaskbarHomeTilePolicy.dropAt(0.34f));
        assertEquals(Position.MIDDLE, TaskbarHomeTilePolicy.dropAt(0.6f));
        assertEquals(Position.END, TaskbarHomeTilePolicy.dropAt(0.7f));
        assertEquals(Position.END, TaskbarHomeTilePolicy.dropAt(1.4f));
        assertEquals(TaskbarHomeTilePolicy.DEFAULT, TaskbarHomeTilePolicy.dropAt(Float.NaN));
    }

    @Test
    public void storedValue_roundTrips_andUnknownIsTheStart() {
        for (Position position : Position.values()) {
            assertEquals(position,
                TaskbarHomeTilePolicy.parse(TaskbarHomeTilePolicy.persisted(position)));
        }
        assertEquals(Position.MIDDLE, TaskbarHomeTilePolicy.parse(" Middle "));
        assertEquals(Position.START, TaskbarHomeTilePolicy.parse(null));
        assertEquals(Position.START, TaskbarHomeTilePolicy.parse(""));
        assertEquals(Position.START, TaskbarHomeTilePolicy.parse("left"));
    }
}
