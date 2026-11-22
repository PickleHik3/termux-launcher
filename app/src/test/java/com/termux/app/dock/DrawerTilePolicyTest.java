package com.termux.app.dock;

import static org.junit.Assert.assertEquals;

import com.termux.app.dock.DrawerTilePolicy.Position;

import org.junit.Test;

/**
 * The movable Drawer tile (#97): its slot at the start, middle or end for any count of apps,
 * the apps keeping their order around it, the drop in thirds, and the stored value read back.
 */
public class DrawerTilePolicyTest {

    @Test
    public void homeSlot_startMiddleEnd() {
        assertEquals(0, DrawerTilePolicy.tileSlot(Position.START, 4));
        assertEquals(2, DrawerTilePolicy.tileSlot(Position.MIDDLE, 4));
        assertEquals(4, DrawerTilePolicy.tileSlot(Position.END, 4));
        // Three apps: the extra one stands after the tile.
        assertEquals(1, DrawerTilePolicy.tileSlot(Position.MIDDLE, 3));
        assertEquals(0, DrawerTilePolicy.tileSlot(Position.MIDDLE, 0));
        assertEquals(0, DrawerTilePolicy.tileSlot(Position.END, -2));
    }

    @Test
    public void appsKeepTheirOrderAroundTheTile() {
        for (Position position : Position.values()) {
            for (int apps = 0; apps <= 6; apps++) {
                int expected = 0;
                int home = DrawerTilePolicy.tileSlot(position, apps);
                for (int slot = 0; slot <= apps; slot++) {
                    int app = DrawerTilePolicy.appAt(slot, position, apps);
                    if (slot == home) {
                        assertEquals(-1, app);
                        continue;
                    }
                    assertEquals(position + " " + apps, expected, app);
                    assertEquals(slot, DrawerTilePolicy.slotOf(app, position, apps));
                    expected++;
                }
                assertEquals(apps, expected);
            }
        }
    }

    @Test
    public void slotsOutsideTheRow_holdNothing() {
        assertEquals(-1, DrawerTilePolicy.appAt(-1, Position.START, 4));
        assertEquals(-1, DrawerTilePolicy.appAt(5, Position.START, 4));
        assertEquals(-1, DrawerTilePolicy.slotOf(4, Position.START, 4));
        assertEquals(-1, DrawerTilePolicy.slotOf(-1, Position.END, 4));
    }

    @Test
    public void appSlots_leaveOneForTheTile() {
        assertEquals(4, DrawerTilePolicy.appSlots(5));
        assertEquals(0, DrawerTilePolicy.appSlots(0));
    }

    @Test
    public void dropAt_readsTheRowInThirds() {
        assertEquals(Position.START, DrawerTilePolicy.dropAt(-0.2f));
        assertEquals(Position.START, DrawerTilePolicy.dropAt(0.3f));
        assertEquals(Position.MIDDLE, DrawerTilePolicy.dropAt(0.34f));
        assertEquals(Position.MIDDLE, DrawerTilePolicy.dropAt(0.6f));
        assertEquals(Position.END, DrawerTilePolicy.dropAt(0.7f));
        assertEquals(Position.END, DrawerTilePolicy.dropAt(1.4f));
        assertEquals(DrawerTilePolicy.DEFAULT, DrawerTilePolicy.dropAt(Float.NaN));
    }

    @Test
    public void storedValue_roundTrips_andUnknownIsTheStart() {
        for (Position position : Position.values()) {
            assertEquals(position,
                DrawerTilePolicy.parse(DrawerTilePolicy.persisted(position)));
        }
        assertEquals(Position.MIDDLE, DrawerTilePolicy.parse(" Middle "));
        assertEquals(Position.START, DrawerTilePolicy.parse(null));
        assertEquals(Position.START, DrawerTilePolicy.parse(""));
        assertEquals(Position.START, DrawerTilePolicy.parse("left"));
    }
}
