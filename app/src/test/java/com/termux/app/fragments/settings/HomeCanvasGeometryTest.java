package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.widget.WidgetGridMetrics;
import com.termux.app.statusbar.HomePillsPolicy;
import com.termux.app.statusbar.StatusBarEdgeGeometry;

import org.junit.Test;

/**
 * Home's page on the layout canvas, to scale: the pills' band, the grid below it with its rim
 * and gap, and the pills in the band.
 */
public class HomeCanvasGeometryTest {

    private static final float EPS = 0.001f;

    @Test
    public void theBandIsTheLiveCompactRowOnHomesStyle() {
        assertEquals(StatusBarEdgeGeometry.ROW_COMPACT_CAPSULE_DP,
            HomeCanvasGeometry.pillsBandDp(true), EPS);
        assertEquals(StatusBarEdgeGeometry.ROW_COMPACT_DOCKED_DP,
            HomeCanvasGeometry.pillsBandDp(false), EPS);
        assertEquals("one rule with the live band", HomePillsPolicy.bandDp(true),
            HomeCanvasGeometry.pillsBandDp(true), EPS);
    }

    @Test
    public void theGridStandsBelowTheBandWithHomesMarginAndTheLiveGap() {
        assertEquals(12f * 0.5f, HomeCanvasGeometry.rimPx(12f, 0.5f), EPS);
        assertEquals("a negative Margin is none", 0f, HomeCanvasGeometry.rimPx(-4f, 2f), EPS);
        assertEquals(WidgetGridMetrics.GAP_DP * 0.5f, HomeCanvasGeometry.gapPx(0.5f), EPS);

        float[] area = new float[4];
        assertTrue(HomeCanvasGeometry.gridArea(10f, 20f, 210f, 420f, 0.5f, 12f, false, area));
        float band = StatusBarEdgeGeometry.ROW_COMPACT_DOCKED_DP * 0.5f;
        assertEquals(16f, area[0], EPS);
        assertEquals("below the band, then the rim", 20f + band + 6f, area[1], EPS);
        assertEquals(204f, area[2], EPS);
        assertEquals(414f, area[3], EPS);
    }

    @Test
    public void nothingOnTheGridLiesUnderAPill() {
        for (boolean floating : new boolean[] {false, true}) {
            for (float margin : new float[] {0f, 6f, 12f, 24f}) {
                float[] area = new float[4];
                float[] pills = new float[8];
                assertTrue(HomeCanvasGeometry.gridArea(0f, 0f, 360f, 640f, 1f, margin, floating,
                    area));
                assertTrue(HomeCanvasGeometry.pills(0f, 0f, 360f, 640f, 1f, margin, floating,
                    pills));
                assertTrue("the windows pill ends above the grid", pills[3] <= area[1]);
                assertTrue("the stats pill ends above the grid", pills[7] <= area[1]);
            }
        }
    }

    @Test
    public void aMarginThatLeavesNothingLeavesNoGrid() {
        float[] area = {1f, 2f, 3f, 4f};
        assertFalse(HomeCanvasGeometry.gridArea(0f, 0f, 40f, 400f, 1f, 20f, false, area));
        assertEquals("untouched", 1f, area[0], EPS);
        assertFalse("the band takes the rest",
            HomeCanvasGeometry.gridArea(0f, 0f, 400f, 40f, 1f, 4f, false, area));
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
    public void thePillsStandCentredInTheBandAMarginInFromTheSides() {
        float scale = 0.5f;
        float margin = 12f;
        float[] pills = new float[8];
        assertTrue(HomeCanvasGeometry.pills(0f, 0f, 200f, 400f, scale, margin, true, pills));
        float rim = margin * scale;
        float band = StatusBarEdgeGeometry.ROW_COMPACT_CAPSULE_DP * scale;
        float height = Math.min(HomePillsPolicy.PILL_MAX_HEIGHT_DP * scale,
            band - 2f * HomePillsPolicy.PILL_BAND_INSET_DP * scale);
        // The windows pill at the start.
        assertEquals(rim, pills[0], EPS);
        assertEquals((band - height) / 2f, pills[1], EPS);
        assertEquals(pills[1] + height, pills[3], EPS);
        // The system readout at the end, level with it.
        assertEquals(200f - rim, pills[6], EPS);
        assertEquals(pills[1], pills[5], EPS);
        assertEquals(pills[3], pills[7], EPS);
        assertTrue("the two never meet", pills[2] < pills[4]);
    }

    @Test
    public void thePillsAreHeldToAShortOpening() {
        float[] pills = new float[8];
        // A 20px opening holds a 20px band; the pills keep their inset from its edges.
        assertTrue(HomeCanvasGeometry.pills(0f, 0f, 200f, 20f, 1f, 4f, false, pills));
        assertEquals(20f - 2f * HomePillsPolicy.PILL_BAND_INSET_DP, pills[3] - pills[1], EPS);
        assertFalse(HomeCanvasGeometry.pills(0f, 0f, 200f, 4f, 1f, 4f, false, pills));
    }
}
