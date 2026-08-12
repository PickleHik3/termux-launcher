package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StatusBarResizeGeometryTest {

    @Test
    public void rowMovesContinuouslyFromCollapsedCenterToExpandedBottom() {
        StatusBarResizeGeometry.Row collapsed = StatusBarResizeGeometry.calculate(
            32, 32, 102, 24, 26, 3);
        assertEquals(24, collapsed.height);
        assertEquals(4, collapsed.top);
        assertEquals(4, collapsed.clockClipBottom);
        assertEquals(0f, collapsed.expansion, .001f);

        StatusBarResizeGeometry.Row halfway = StatusBarResizeGeometry.calculate(
            67, 32, 102, 24, 26, 3);
        assertEquals(25, halfway.height);
        assertEquals(38, halfway.top);
        assertEquals(38, halfway.clockClipBottom);
        assertTrue(halfway.top > (67 - halfway.height) / 2);

        StatusBarResizeGeometry.Row expanded = StatusBarResizeGeometry.calculate(
            102, 32, 102, 24, 26, 3);
        assertEquals(26, expanded.height);
        assertEquals(73, expanded.top);
        assertEquals(73, expanded.clockClipBottom);
        assertEquals(1f, expanded.expansion, .001f);
    }

    @Test
    public void fullKeepsTopSlotVisibleAndRowOnMovingLowerEdge() {
        StatusBarResizeGeometry.Row full = StatusBarResizeGeometry.calculateFull(
            500, 102, 600, 26, 3);
        assertEquals(26, full.height);
        assertEquals(471, full.top);
        assertEquals(1f, full.expansion, 0f);
        assertEquals(1f, full.topSlotAlpha, 0f);
        assertTrue(Float.isFinite(full.fullExpansion));

        StatusBarResizeGeometry.Row invalid = StatusBarResizeGeometry.calculateFull(
            0, 0, 0, 0, 0);
        assertTrue(Float.isFinite(invalid.fullExpansion));
    }

    @Test
    public void fullRowUsesActualSurfaceEdgeWhenResolvedTargetIsStale() {
        StatusBarResizeGeometry.Row row = StatusBarResizeGeometry.calculateFull(
            600, 102, 300, 26, 3);
        assertEquals(571, row.top);
        assertEquals(600 - 3, row.top + row.height);
        assertEquals(1f, row.fullExpansion, 0f);
    }
}
