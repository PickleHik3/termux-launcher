package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.widget.WidgetGridMetrics;
import com.termux.app.statusbar.StatusBarEdgeGeometry;

import org.junit.Test;

/** Home's page on the layout canvas, to scale: the grid's rim and gap, and the status pills. */
public class HomeCanvasGeometryTest {

    private static final float EPS = 0.001f;

    @Test
    public void theGridsRimIsHomesMarginAndItsGapTheLiveGridsToScale() {
        assertEquals(12f * 0.5f, HomeCanvasGeometry.rimPx(12f, 0.5f), EPS);
        assertEquals("a negative Margin is none", 0f, HomeCanvasGeometry.rimPx(-4f, 2f), EPS);
        assertEquals(WidgetGridMetrics.GAP_DP * 0.5f, HomeCanvasGeometry.gapPx(0.5f), EPS);

        float[] area = new float[4];
        assertTrue(HomeCanvasGeometry.gridArea(10f, 20f, 210f, 420f, 0.5f, 12f, area));
        assertEquals(16f, area[0], EPS);
        assertEquals(26f, area[1], EPS);
        assertEquals(204f, area[2], EPS);
        assertEquals(414f, area[3], EPS);
    }

    @Test
    public void aMarginThatLeavesNothingLeavesNoGrid() {
        float[] area = {1f, 2f, 3f, 4f};
        assertFalse(HomeCanvasGeometry.gridArea(0f, 0f, 40f, 400f, 1f, 20f, area));
        assertEquals("untouched", 1f, area[0], EPS);
    }

    @Test
    public void cellsShareTheAreaLessTheGaps() {
        // Four cells across 200 with a 4px gap: (200 - 12) / 4.
        assertEquals(47f, HomeCanvasGeometry.cellExtent(200f, 4, 4f), EPS);
        assertEquals("one cell takes it all", 200f, HomeCanvasGeometry.cellExtent(200f, 1, 4f),
            EPS);
        assertEquals("no fewer than one cell", 200f, HomeCanvasGeometry.cellExtent(200f, 0, 4f),
            EPS);
        assertEquals("never negative", 0f, HomeCanvasGeometry.cellExtent(10f, 6, 4f), EPS);
    }

    @Test
    public void thePillsStandAMarginInFromTheTopCornersAtTheCompactStatusRowsHeight() {
        float scale = 0.5f;
        float margin = 12f;
        float[] pills = new float[8];
        assertTrue(HomeCanvasGeometry.pills(0f, 0f, 200f, 400f, scale, margin, pills));
        float rim = margin * scale;
        float height = StatusBarEdgeGeometry.ROW_COMPACT_CAPSULE_DP * scale;
        // The windows pill at the start.
        assertEquals(rim, pills[0], EPS);
        assertEquals(rim, pills[1], EPS);
        assertEquals(rim + height, pills[3], EPS);
        // The system readout at the end, level with it.
        assertEquals(200f - rim, pills[6], EPS);
        assertEquals(pills[1], pills[5], EPS);
        assertEquals(pills[3], pills[7], EPS);
        assertTrue("the two never meet", pills[2] < pills[4]);
        assertEquals(HomeCanvasGeometry.pillsBandDp() * scale, pills[3] - pills[1], EPS);
    }

    @Test
    public void thePillsAreHeldToAShortOpening() {
        float[] pills = new float[8];
        assertTrue(HomeCanvasGeometry.pills(0f, 0f, 200f, 20f, 1f, 4f, pills));
        assertEquals("the room inside the rim", 12f, pills[3] - pills[1], EPS);
        assertFalse(HomeCanvasGeometry.pills(0f, 0f, 200f, 8f, 1f, 4f, pills));
    }
}
