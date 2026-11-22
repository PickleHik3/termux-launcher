package com.termux.app.statusbar;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** The status row's user order and the dots between its shown items. */
public class StatusRowOrderTest {

    private static final StatusRowItem CPU = StatusRowItem.CPU;
    private static final StatusRowItem GPU = StatusRowItem.GPU;
    private static final StatusRowItem RAM = StatusRowItem.RAM;
    private static final StatusRowItem BATTERY = StatusRowItem.BATTERY;
    private static final StatusRowItem WEATHER = StatusRowItem.WEATHER;
    private static final StatusRowItem TEXT_SIZE = StatusRowItem.TEXT_SIZE;
    private static final StatusRowItem NOTIFICATIONS = StatusRowItem.NOTIFICATIONS;
    private static final StatusRowItem AI = StatusRowItem.AI;
    private static final StatusRowItem MOUSE = StatusRowItem.MOUSE;

    /** Nothing moves for an install that never reordered: the row's order before this change. */
    @Test public void nothingStoredReadsAsTheShippedOrder() {
        StatusRowItem[] shipped = {CPU, GPU, RAM, BATTERY, WEATHER, TEXT_SIZE, NOTIFICATIONS, AI,
            MOUSE};
        assertArrayEquals(shipped, StatusRowOrder.parse(null));
        assertArrayEquals(shipped, StatusRowOrder.parse(""));
        assertArrayEquals(shipped, StatusRowOrder.defaults());
    }

    @Test public void aStoredOrderReadsBackAndRoundTrips() {
        StatusRowItem[] order = {MOUSE, WEATHER, CPU, AI, TEXT_SIZE, GPU, NOTIFICATIONS, RAM,
            BATTERY};
        String stored = StatusRowOrder.format(order);
        assertEquals("mouse,weather,cpu,ai,text_size,gpu,notifications,ram,battery", stored);
        assertArrayEquals(order, StatusRowOrder.parse(stored));
    }

    @Test public void unknownAndRepeatedKeysAreDroppedAndMissingOnesAppendedInShippedOrder() {
        assertArrayEquals(
            new StatusRowItem[] {WEATHER, RAM, CPU, GPU, BATTERY, TEXT_SIZE, NOTIFICATIONS, AI,
                MOUSE},
            StatusRowOrder.parse("weather, clock,ram,weather,,media,ram"));
    }

    @Test public void normalizeGivesEqualOrdersOneSpelling() {
        assertEquals(StatusRowOrder.normalize(""), StatusRowOrder.normalize(null));
        assertEquals(StatusRowOrder.normalize(""),
            StatusRowOrder.normalize("cpu,gpu,ram,battery,weather"));
        assertNotEquals(StatusRowOrder.normalize(""), StatusRowOrder.normalize("gpu"));
    }

    @Test public void everyItemHasItsOwnKeyWidgetAndDot() {
        Set<String> keys = new HashSet<>();
        Set<Integer> ids = new HashSet<>();
        for (StatusRowItem item : StatusRowItem.values()) {
            assertTrue(item.key, keys.add(item.key));
            assertFalse(item.key.contains(","));
            assertTrue(item.key, ids.add(item.widgetId));
            assertTrue(item.key, ids.add(item.dotId));
            assertEquals(item, StatusRowItem.forKey(item.key));
        }
    }

    @Test public void moveTakesAnItemOutAndPutsItBackAtItsNewPlace() {
        StatusRowItem[] shipped = StatusRowOrder.defaults();
        assertArrayEquals(
            new StatusRowItem[] {GPU, RAM, CPU, BATTERY, WEATHER, TEXT_SIZE, NOTIFICATIONS, AI,
                MOUSE},
            StatusRowOrder.move(shipped, 0, 2));
        assertArrayEquals(
            new StatusRowItem[] {MOUSE, CPU, GPU, RAM, BATTERY, WEATHER, TEXT_SIZE, NOTIFICATIONS,
                AI},
            StatusRowOrder.move(shipped, 8, 0));
        assertArrayEquals(shipped, StatusRowOrder.move(shipped, 3, 3));
        assertArrayEquals(shipped, StatusRowOrder.move(shipped, -1, 3));
        // The array handed in is never changed.
        assertArrayEquals(StatusRowItem.values(), shipped);
    }

    /**
     * Over every on/off combination of the nine items: one dot between two shown neighbours,
     * none before the first shown item and none after the last.
     */
    @Test public void everyCombinationHasOneDotBetweenNeighboursAndNoneDangling() {
        int n = StatusRowItem.values().length;
        boolean[] shown = new boolean[n];
        boolean[] dots = new boolean[n];
        for (int mask = 0; mask < (1 << n); mask++) {
            int items = 0;
            for (int i = 0; i < n; i++) {
                shown[i] = (mask & (1 << i)) != 0;
                if (shown[i]) items++;
            }
            StatusRowOrder.dots(shown, dots);
            int first = -1;
            int count = 0;
            for (int i = 0; i < n; i++) {
                if (dots[i]) count++;
                if (dots[i]) assertTrue("mask " + mask, shown[i]);
                if (shown[i] && first < 0) first = i;
            }
            assertEquals("mask " + mask, Math.max(0, items - 1), count);
            if (first >= 0) assertFalse("mask " + mask, dots[first]);
        }
    }
}
