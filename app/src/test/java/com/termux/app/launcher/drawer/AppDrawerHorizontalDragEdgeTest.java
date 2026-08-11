package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import org.junit.Test;

public class AppDrawerHorizontalDragEdgeTest {
    @Test public void exactEdgeZonesAndDwellContractAreStable() {
        assertEquals(32f, AppDrawerDragPolicy.HORIZONTAL_EDGE_DP, 0f);
        assertEquals(500L, AppDrawerDragPolicy.HORIZONTAL_DWELL_MS);
        assertEquals(-1, AppDrawerDragPolicy.edgeDirection(31f, 400f, 1f));
        assertEquals(0, AppDrawerDragPolicy.edgeDirection(33f, 400f, 1f));
        assertEquals(1, AppDrawerDragPolicy.edgeDirection(369f, 400f, 1f));
        assertEquals(0, AppDrawerDragPolicy.edgeDirection(200f, 400f, 1f));
    }
}
