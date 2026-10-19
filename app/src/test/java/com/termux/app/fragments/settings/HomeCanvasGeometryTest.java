package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.widget.WidgetGridMetrics;

import org.junit.Test;

/** Home's page on the layout canvas, to scale: the grid with its rim and gap, and no band. */
public class HomeCanvasGeometryTest {

    private static final float EPS = 0.001f;

    @Test
    public void theGridTakesTheOpeningLessHomesMarginWithTheLiveGap() {
        assertEquals(12f * 0.5f, HomeCanvasGeometry.rimPx(12f, 0.5f), EPS);
        assertEquals("a negative Margin is none", 0f, HomeCanvasGeometry.rimPx(-4f, 2f), EPS);
        assertEquals(WidgetGridMetrics.GAP_DP * 0.5f, HomeCanvasGeometry.gapPx(0.5f), EPS);

        float[] area = new float[4];
        assertTrue(HomeCanvasGeometry.gridArea(10f, 20f, 210f, 420f, 0.5f, 12f, area));
        assertEquals(16f, area[0], EPS);
        assertEquals("no status band: the rim alone below the top", 26f, area[1], EPS);
        assertEquals(204f, area[2], EPS);
        assertEquals(414f, area[3], EPS);
    }

    @Test
    public void aMarginThatLeavesNothingLeavesNoGrid() {
        float[] area = {1f, 2f, 3f, 4f};
        assertFalse(HomeCanvasGeometry.gridArea(0f, 0f, 40f, 400f, 1f, 20f, area));
        assertEquals("untouched", 1f, area[0], EPS);
        assertFalse(HomeCanvasGeometry.gridArea(0f, 0f, 400f, 40f, 1f, 20f, area));
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
}
